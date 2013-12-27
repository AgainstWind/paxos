package org.dancres.paxos.impl.faildet;

import org.dancres.paxos.FailureDetector;
import org.dancres.paxos.Membership;
import org.dancres.paxos.impl.*;
import org.dancres.paxos.impl.Transport.Packet;
import org.dancres.paxos.messages.Operations;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.util.AbstractFuture;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple failure detector driven by reception of {@link Heartbeat} messages generated by {@link Heartbeater}.
 * This implementation expects the transport to present all received messages to the detector via <code>processMessage</code>
 *
 * @todo Ultimately this detector could be further enhanced by using messages generated as part of standard Paxos interactions
 * to determine liveness.  The Heartbeater would then be modified to generate a message only if there had been an absence of
 * other messages sent by a node for a suitable period of time.
 */
public class FailureDetectorImpl extends MessageBasedFailureDetector {
    private static class ContainsAll<T> extends LinkedList<T> {
        public boolean contains(Object anObject) {
            return true;
        }
    }

    /**
     * An open pin is treated like a pin but membership is wildcard'ed -- TESTING USE ONLY. Membership in a real
     * cluster must change as the result of an instance of Paxos in order to maintain the constraints required for
     * safe progress.
     */
    public static final List<InetSocketAddress> OPEN_PIN = new ContainsAll<>();

    private static final int DEFAULT_CLUSTER_SIZE = 3;

    private final Timer _tasks = new Timer();

    private volatile Collection<InetSocketAddress> _pinned = null;
    private final AtomicBoolean _stopping = new AtomicBoolean(false);

    private final LinkedBlockingQueue<StateListener> _listeners = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<FutureImpl> _futures = new LinkedBlockingQueue<>();
    private final Random _random = new Random();
    private final ConcurrentMap<InetSocketAddress, MetaDataImpl> _lastHeartbeats = new ConcurrentHashMap<>();
    private final long _maximumPeriodOfUnresponsiveness;
    private final int _majority;

    private class MetaDataImpl implements FailureDetector.MetaData {
        final long _timestamp;
        final byte[] _metaData;

        MetaDataImpl(long aTimestamp, byte[] aMeta) {
            _timestamp = aTimestamp;
            _metaData = aMeta;
        }

        public byte[] getData() {
            return _metaData;
        }

        public long getTimestamp() {
            return _timestamp;
        }
    }

    private class ScanImpl extends TimerTask {
        public void run() {
            Iterator<InetSocketAddress> myProcesses = _lastHeartbeats.keySet().iterator();
            long myMinTime = System.currentTimeMillis() - _maximumPeriodOfUnresponsiveness;

            while (myProcesses.hasNext()) {
                InetSocketAddress myAddress = myProcesses.next();
                long myTimeout = _lastHeartbeats.get(myAddress)._timestamp;

                // No heartbeat since myMinTime means we assume dead
                //
                if (myTimeout < myMinTime) {
                    myProcesses.remove();
                }
            }
        }
    }

    /**
     * Assumes a three-node cluster in which a majority of 2 is sufficient for progress
     *
     * @param anUnresponsivenessThreshold is the maximum period a node may "dark" before being declared failed.
     */
    public FailureDetectorImpl(long anUnresponsivenessThreshold) {
        this(DEFAULT_CLUSTER_SIZE, anUnresponsivenessThreshold);
    }

    /**
     * @param anUnresponsivenessThreshold is the maximum period a node may "dark" before being declared failed.
     * @param aPinSet is the set of addresses to allow into the membership
     */
    public FailureDetectorImpl(long anUnresponsivenessThreshold, Collection<InetSocketAddress> aPinSet) {
        this(DEFAULT_CLUSTER_SIZE, anUnresponsivenessThreshold, aPinSet);
    }

    /**
     * @param aClusterSize is the number of members in the cluster
     * @param anUnresponsivenessThreshold is the maximum period a node may "dark" before being declared failed.
     */
    public FailureDetectorImpl(int aClusterSize, long anUnresponsivenessThreshold) {
        this(aClusterSize, anUnresponsivenessThreshold, null);
    }

    /**
     * @param aClusterSize is the number of members in the cluster
     * @param anUnresponsivenessThreshold is the maximum period a node may "dark" before being declared failed.
     * @param aPinSet is the set of addresses to allow into the membership
     */
    public FailureDetectorImpl(int aClusterSize, long anUnresponsivenessThreshold,
                               Collection<InetSocketAddress> aPinSet) {
        _majority = calculateMajority(aClusterSize);
        _maximumPeriodOfUnresponsiveness = anUnresponsivenessThreshold;
        _tasks.schedule(new ScanImpl(), 0, (_maximumPeriodOfUnresponsiveness / 5));
        _pinned = aPinSet;
    }

    private int calculateMajority(int aClusterSize) {
        if ((aClusterSize % 2) != 1)
            throw new IllegalArgumentException("Cluster size must be an odd number");

        return (aClusterSize / 2) + 1;
    }

    public void stop() {
        _stopping.set(true);
        _tasks.cancel();
    }

    private void notifyListeners(State aState) {
        for (StateListener myL : _listeners)
            myL.change(this, aState);
    }

    public void addListener(StateListener aListener) {
        _listeners.add(aListener);
        aListener.change(this, isPinned() ? State.PINNED : State.OPEN);
    }

    public Heartbeater newHeartbeater(Transport aTransport, byte[] aMetaData) {
        // We want at least three heartbeats within the unresponsiveness period
        //
        return new HeartbeaterImpl(aTransport, aMetaData, (_maximumPeriodOfUnresponsiveness / 3) - 100);
    }   

    public boolean accepts(Packet aPacket) {
        return aPacket.getMessage().getClassifications().contains(PaxosMessage.Classification.FAILURE_DETECTOR);
    }

    /**
     * Examine a received {@link PaxosMessage} and update liveness information as appropriate.
     */
    public void processMessage(Packet aPacket) {
        PaxosMessage myMessage = aPacket.getMessage();

        if (myMessage.getType() == Operations.HEARTBEAT) {
            MetaDataImpl myLast;

            final Heartbeat myHeartbeat = (Heartbeat) myMessage;
            final InetSocketAddress myNodeId = aPacket.getSource();

            // If we see a heartbeat from a node that isn't pinned, ignore it
            //
            if ((_pinned != null) && (! _pinned.contains(myNodeId)))
                return;

            for (;;) {
                myLast = _lastHeartbeats.get(myNodeId);

                if (myLast == null) {
                    if (_lastHeartbeats.putIfAbsent(myNodeId,
                            new MetaDataImpl(System.currentTimeMillis(), myHeartbeat.getMetaData())) == null)
                        break;
                } else {
                    if (_lastHeartbeats.replace(myNodeId, myLast, new MetaDataImpl(System.currentTimeMillis(),
                            myHeartbeat.getMetaData())))
                    break;
                }
            }

            if (_futures.size() != 0) {

                Membership myMembership =
                        new MembershipImpl(new HashMap<InetSocketAddress, MetaData>(_lastHeartbeats));

                Iterator<FutureImpl> myFutures = _futures.iterator();

                while (myFutures.hasNext()) {
                    FutureImpl myFuture = myFutures.next();

                    myFuture.offer(myMembership);
                }
            }
        }
    }

    private class FutureImpl extends AbstractFuture<Membership> {
        private final Queue _queue;
        private final int _required;

        FutureImpl(Queue aQueue, int aRequired) {
            _queue = aQueue;
            _required = aRequired;
        }

        void offer(Membership aMembership) {
            if (aMembership.getSize() >= _required)
                set(aMembership);
        }

        protected void done() {
            _queue.remove();
        }
    }

    public Future<Membership> barrier() {
        return barrier(getMajority());
    }

    public Future<Membership> barrier(int aRequired) {
        FutureImpl myFuture = new FutureImpl(_futures, aRequired);
        _futures.add(myFuture);

        return myFuture;
    }

    public void pin(Collection<InetSocketAddress> aMembers) {
        _pinned = aMembers;

        if (_pinned == null) {
            notifyListeners(State.OPEN);
            return;
        } else {
            notifyListeners(State.PINNED);
        }

        Iterator<InetSocketAddress> myCurrentMembers = _lastHeartbeats.keySet().iterator();
        while (myCurrentMembers.hasNext()) {
            InetSocketAddress myMember = myCurrentMembers.next();

            if (! _pinned.contains(myMember))
                _lastHeartbeats.remove(myMember);
        }
    }

    private boolean isPinned() {
        return (_pinned != null);
    }

    public int getMajority() {
        return _majority;
    }

    public Membership getMembers() {
        return new MembershipImpl(new HashMap<InetSocketAddress, MetaData>(_lastHeartbeats));
    }

    public boolean isMember(InetSocketAddress anAddress) {
        return _pinned.contains(anAddress);
    }

    public InetSocketAddress getRandomMember(InetSocketAddress aLocalAddress) {
        LinkedList<InetSocketAddress> myMembers = new LinkedList<>(_lastHeartbeats.keySet());

        myMembers.remove(aLocalAddress);

        if (myMembers.size() > 0)
            return myMembers.get(_random.nextInt(myMembers.size()));
        else
            return null;
    }

    /**
     * A snapshot of the membership at some point in time, updated by the <code>FailureDetectorImpl</code> over time.
     * Note the snapshot only reduces in size, it cannot grow so as to allow correct behaviour in cases where
     * majorities are required.
     *
     * @author dan
     */
    class MembershipImpl implements Membership {
        /**
         * Tracks the membership that forms the base for each round
         */
        private final Map<InetSocketAddress, MetaData> _members;

        MembershipImpl(Map<InetSocketAddress, MetaData> anInitialAddresses) {
            _members = anInitialAddresses;
        }

        public Map<InetSocketAddress, MetaData> getMemberMap() {
            return _members;
        }

        public int getSize() {
            return _members.size();
        }

        public boolean couldComplete() {
            return (_members.size() >= _majority);
        }

        public boolean isMajority(Collection<InetSocketAddress> aListOfAddresses) {
            return ((_members.keySet().containsAll(aListOfAddresses)) &&
                    aListOfAddresses.size() >= _majority);
        }
    }
}
