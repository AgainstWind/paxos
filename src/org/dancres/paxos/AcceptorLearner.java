package org.dancres.paxos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.dancres.paxos.messages.Accept;
import org.dancres.paxos.messages.Ack;
import org.dancres.paxos.messages.Begin;
import org.dancres.paxos.messages.Collect;
import org.dancres.paxos.messages.Last;
import org.dancres.paxos.messages.OldRound;
import org.dancres.paxos.messages.Operations;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.messages.Success;
import org.dancres.paxos.messages.codec.Codecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the Acceptor/Learner state machine.  Note that the instance running in the same JVM as the current leader
 * is to all intents and purposes (bar very strange hardware or operating system failures) guaranteed to receive packets
 * from the leader.  Thus if a leader declares SUCCESS then the local instance will receive those packets.  This can be
 * useful for processing client requests correctly and signalling interested parties as necessary.
 *
 * @author dan
 */
public class AcceptorLearner {
    public static final byte[] HEARTBEAT = "org.dancres.paxos.Heartbeat".getBytes();

    private static long DEFAULT_LEASE = 30 * 1000;
    private static Logger _logger = LoggerFactory.getLogger(AcceptorLearner.class);

    /**
     * Statistic that tracks the number of Collects this AcceptorLearner ignored from competing leaders within
     * DEFAULT_LEASE ms of activity from the current leader.
     */
    private AtomicLong _ignoredCollects = new AtomicLong();
    private AtomicLong _receivedHeartbeats = new AtomicLong();

    private Collect _lastCollect = Collect.INITIAL;
    private long _lastLeaderActionTime = 0;

    private LogStorage _storage;

    /**
     * Tracks the last contiguous sequence number for which we have a value. A value of -1 indicates we have yet
     * to see a proposals.
     * 
     * When we receive a success, if it's seqNum is this field + 1, increment this field.  Acts as the low watermark for
     * leader recovery, essentially we want to recover from the last contiguous sequence number in the stream of paxos
     * instances.
     */
    private long _lowSeqNumWatermark = LogStorage.EMPTY_LOG;

    /**
     * Records the most recent seqNum we've seen in a BEGIN or SUCCESS message.  We may see a SUCCESS without BEGIN but
     * that's okay as the leader must have had sufficient majority to get agreement so we can just agree, update this
     * count and update the value/seqNum store. A value of -1 indicates we have yet to see a proposal.
     */
    private long _highSeqNumWatermark = LogStorage.EMPTY_LOG;

    /**
     * PacketBuffer is used to maintain a limited amount of past Paxos history that can be used to catch-up
     * recovering nodes without the cost of a full transfer of logs etc. This is useful in cases where nodes
     * temporarily fail due to loss of a network connection or reboots.
     */
    private PacketBuffer _buffer = new PacketBuffer(512);
    
    private final List<AcceptorLearnerListener> _listeners = new ArrayList<AcceptorLearnerListener>();

    public AcceptorLearner(LogStorage aStore) {
        _storage = aStore;
        
        try {
        	_storage.open();
        } catch (Exception anE) {
        	_logger.error("Failed to open logger", anE);
        	throw new RuntimeException(anE);
        }
    }

    public void close() {
        try {
        	_storage.close();
        	_buffer.dump(_logger);
        } catch (Exception anE) {
        	_logger.error("Failed to close logger", anE);
        	throw new RuntimeException(anE);
        }    	
    }
    
    public long getLeaderLeaseDuration() {
        return DEFAULT_LEASE;
    }

    public void add(AcceptorLearnerListener aListener) {
        synchronized(_listeners) {
            _listeners.add(aListener);
        }
    }

    public void remove(AcceptorLearnerListener aListener) {
        synchronized(_listeners) {
            _listeners.remove(aListener);
        }
    }

    public long getHeartbeatCount() {
        return _receivedHeartbeats.longValue();
    }

    public long getIgnoredCollectsCount() {
        return _ignoredCollects.longValue();
    }

    private LogStorage getStorage() {
        return _storage;
    }

    private void updateLowWatermark(long aSeqNum) {
        synchronized(this) {
            if (_lowSeqNumWatermark == LogStorage.EMPTY_LOG)
                _lowSeqNumWatermark = -1;

            if (_lowSeqNumWatermark == (aSeqNum - 1)) {
                _lowSeqNumWatermark = aSeqNum;

                _logger.info("Low watermark:" + aSeqNum);
            }

        }
    }

    public long getLowWatermark() {
        synchronized(this) {
            return _lowSeqNumWatermark;
        }
    }

    private void updateHighWatermark(long aSeqNum) {
        synchronized(this) {
            if (_highSeqNumWatermark < aSeqNum) {
                _highSeqNumWatermark = aSeqNum;

                _logger.info("High watermark:" + aSeqNum);
            }
        }
    }

    public long getHighWatermark() {
        synchronized(this) {
            return _highSeqNumWatermark;
        }
    }

    /**
     * @param aCollect should be tested to see if it supercedes the current COLLECT
     * @return the old collect if it's superceded or null
     */
    private Collect supercedes(Collect aCollect) {
        synchronized(this) {
            if (aCollect.supercedes(_lastCollect)) {
                Collect myOld = _lastCollect;
                _lastCollect = aCollect;

                return myOld;
            } else {
                return null;
            }
        }
    }

    public Collect getLastCollect() {
        synchronized(this) {
            return _lastCollect;
        }
    }

    private boolean originates(Begin aBegin) {
        synchronized(this) {
            return aBegin.originates(_lastCollect);
        }
    }

    private boolean precedes(Begin aBegin) {
        synchronized(this) {
            return aBegin.precedes(_lastCollect);
        }
    }

    /**
     * @return <code>true</code> if the collect is either from the existing leader, or there is no leader or there's
     * been nothing heard from the current leader within DEFAULT_LEASE milliseconds else <code>false</code>
     */
    private boolean amAccepting(Collect aCollect, long aCurrentTime) {
        synchronized(this) {
            if (_lastCollect.isInitial()) {
                return true;
            } else {
                if (aCollect.getNodeId() == _lastCollect.getNodeId())
                    return true;
                else
                    return (aCurrentTime > _lastLeaderActionTime + DEFAULT_LEASE);
            }
        }
    }

    private void updateLastActionTime(long aTime) {
        _logger.info("Updating last action time: " + aTime);

        synchronized(this) {
            _lastLeaderActionTime = aTime;
        }
    }

    /**
     * @todo FIX THIS - we need to return a value in a LAST not just a default!
     */
    public PaxosMessage process(PaxosMessage aMessage) {
        long myCurrentTime = System.currentTimeMillis();
        long mySeqNum = aMessage.getSeqNum();

        _logger.info("AcceptorLearnerState got [ " + mySeqNum + " ] : " + aMessage);

        switch (aMessage.getType()) {
            case Operations.COLLECT : {
                Collect myCollect = (Collect) aMessage;

                if (! amAccepting(myCollect, myCurrentTime)) {
                    _ignoredCollects.incrementAndGet();

                    _logger.info("Not accepting: " + myCollect + ", " + getIgnoredCollectsCount());
                    return null;
                }

                Collect myOld = supercedes(myCollect);

                if (myOld != null) {
                    updateLastActionTime(myCurrentTime);
                    write(aMessage, true);
                    
                    // @TODO FIX THIS!!!!
                    //
                    return new Last(mySeqNum, getLowWatermark(), getHighWatermark(), myOld.getRndNumber(),
                            LogStorage.NO_VALUE);
                } else {
                    // Another collect has already arrived with a higher priority, tell the proposer it has competition
                    //
                    Collect myLastCollect = getLastCollect();

                    return new OldRound(mySeqNum, myLastCollect.getNodeId(), myLastCollect.getRndNumber());
                }
            }

            case Operations.BEGIN : {
                Begin myBegin = (Begin) aMessage;

                // If the begin matches the last round of a collect we're fine
                //
                if (originates(myBegin)) {
                    updateLastActionTime(myCurrentTime);
                    updateHighWatermark(myBegin.getSeqNum());

                    return new Accept(mySeqNum, getLastCollect().getRndNumber());
                } else if (precedes(myBegin)) {
                    // New collect was received since the collect for this begin, tell the proposer it's got competition
                    //
                    Collect myLastCollect = getLastCollect();

                    return new OldRound(mySeqNum, myLastCollect.getNodeId(), myLastCollect.getRndNumber());
                } else {
                    // Quiet, didn't see the collect, leader hasn't accounted for our values, it hasn't seen our last
                    //
                    _logger.info("Missed collect, going silent: " + mySeqNum + " [ " + myBegin.getRndNumber() + " ]");
                }
            }
            
            case Operations.SUCCESS : {
                Success mySuccess = (Success) aMessage;

                _logger.info("Learnt value: " + mySuccess.getSeqNum());

                updateLastActionTime(myCurrentTime);
                updateLowWatermark(mySuccess.getSeqNum());
                updateHighWatermark(mySuccess.getSeqNum());

                Completion myCompletion = new Completion(Reasons.OK, mySuccess.getSeqNum(), mySuccess.getValue());

                // Always record the value even if it's the heartbeat so there are no gaps in the Paxos sequence
                //
                write(aMessage, true);
                
                if (notHeartbeat(myCompletion.getValue())) {
                    signal(myCompletion);
                } else {
                    _receivedHeartbeats.incrementAndGet();

                    _logger.info("AcceptorLearner discarded heartbeat: " + System.currentTimeMillis() + ", " +
                            getHeartbeatCount());
                }

                return new Ack(mySuccess.getSeqNum());
            }

            default : throw new RuntimeException("Unexpected message");
        }
    }

    private void write(PaxosMessage aMessage, boolean aForceRequired) {
        try {
        	_buffer.add(aMessage, getStorage().put(Codecs.encode(aMessage), aForceRequired));
        } catch (Exception anE) {
        	_logger.error("Acceptor cannot log: " + System.currentTimeMillis(), anE);
        	throw new RuntimeException(anE);
        }    	
    }
    
    private boolean notHeartbeat(byte[] aValue) {
        if (aValue.length != HEARTBEAT.length) {
            return true;
        }

        for (int i = 0; i < aValue.length; i++) {
            if (aValue[i] != HEARTBEAT[i]) {
                return true;
            }
        }

        return false;
    }

    void signal(Completion aStatus) {
        List<AcceptorLearnerListener> myListeners;

        synchronized(_listeners) {
            myListeners = new ArrayList<AcceptorLearnerListener>(_listeners);
        }

        Iterator<AcceptorLearnerListener> myTargets = myListeners.iterator();

        while (myTargets.hasNext()) {
            myTargets.next().done(aStatus);
        }
    }
}
