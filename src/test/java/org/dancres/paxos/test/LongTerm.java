package org.dancres.paxos.test;

import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

import org.dancres.paxos.*;
import org.dancres.paxos.impl.Transport;
import org.dancres.paxos.impl.faildet.FailureDetectorImpl;
import org.dancres.paxos.messages.Envelope;
import org.dancres.paxos.storage.HowlLogger;
import org.dancres.paxos.test.junit.FDUtil;
import org.dancres.paxos.test.net.ClientDispatcher;
import org.dancres.paxos.test.net.OrderedMemoryNetwork;
import org.dancres.paxos.test.net.OrderedMemoryTransportImpl;
import org.dancres.paxos.test.net.ServerDispatcher;
import org.dancres.paxos.test.utils.FileSystem;
import org.dancres.paxos.test.utils.MemoryCheckpointStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Need a statistical failure model with varying probabilities for each thing within a tolerance / order
 * For normalisation against a test duration, one year is 525600 minutes.
 * 
 * <ol>
 * <li>Hard disk failure - 6 to 10% </li>
 * <li>Node failure - 1 to 2% up to 15 minutes to recover</li>
 * <li>DC-wide network failure - 0 to 1% up to  5.26 minutes/year</li>
 * <li>Rack-level failure - 0 to 1% downtime up to 8.76 hours/year</li>
 * <li>Link-level failure - 0 to 1% up to 52.56 minutes/year</li>
 * <li>DC-wide packet loss - 1 to 2% with loss of 2 to 10's packets</li>
 * <li>Rack-level packet loss - 1 to 2% with loss of 2 to 10's packets</li>
 * <li>Link-level packet loss - 1 to 2% with loss of 2 to 10's packets</li>
 * </ol>
 *
 * Each of these will also have a typical time to fix. In essence we want an MTBF/MTRR model.
 *
 * @todo Implement failure model (might affect design for OUT_OF_DATE handling).
 */
public class LongTerm {
    private static final Logger _logger = LoggerFactory.getLogger(LongTerm.class);
    private static final String BASEDIR = "/Volumes/LaCie/paxoslogs/";

    static interface Args {
        @Option(defaultValue="100")
        long getCkptCycle();

        @Option(defaultValue="0")
        long getSeed();

        @Option(defaultValue="200")
        int getCycles();

        @Option
        boolean isCalibrate();
    }

    interface NodeAdmin {
        Transport getTransport();
        void checkpoint() throws Exception;
        CheckpointStorage.ReadCheckpoint getLastCheckpoint();
        long lastCheckpointTime();
        boolean isOutOfDate();
        void terminate();
        boolean bringUpToDate(CheckpointStorage.ReadCheckpoint aCkpt);
        void settle();
    }

    private static class Environment {
        final boolean _calibrate;
        final long _maxCycles;
        final long _settleCycles = 50;
        final long _ckptCycle;
        final Random _rng;
        private final List<NodeAdmin> _nodes = new LinkedList<NodeAdmin>();

        Transport _currentLeader;
        final OrderedMemoryNetwork _factory;

        Environment(long aSeed, long aCycles, boolean doCalibrate, long aCkptCycle) throws Exception {
            _ckptCycle = aCkptCycle;
            _calibrate = doCalibrate;
            _maxCycles = aCycles;
            _rng = new Random(aSeed);
            _factory = new OrderedMemoryNetwork();

            OrderedMemoryNetwork.Factory myFactory = new OrderedMemoryNetwork.Factory() {
                private int _nextNodeNum = 0;

                public OrderedMemoryNetwork.OrderedMemoryTransport newTransport(InetSocketAddress aLocalAddr,
                                                                                InetSocketAddress aBroadcastAddr,
                                                                                OrderedMemoryNetwork aNetwork) {
                    NodeAdminImpl myTp = new NodeAdminImpl(aLocalAddr, aBroadcastAddr, aNetwork, _nextNodeNum,
                            Environment.this);

                    _nodes.add(myTp);
                    _nextNodeNum++;

                    return myTp.getTransport();
                }
            };

            for (int i = 0; i < 5; i++) {
                _factory.newTransport(myFactory);
            }

            _currentLeader = _nodes.get(0).getTransport();
        }

        void stabilise() throws Exception {
            for (NodeAdmin myNA : _nodes) {
                FDUtil.ensureFD(myNA.getTransport().getFD());
            }
        }

        void settle() {
            for (NodeAdmin myNA : _nodes)
                myNA.settle();
        }

        void terminate() {
            for (NodeAdmin myNA : _nodes)
                myNA.terminate();
        }

        void updateLeader(InetSocketAddress anAddr) {
            for (NodeAdmin myNA : _nodes) {
                Transport myTp = myNA.getTransport();

                if (myTp.getLocalAddress().equals(anAddr)) {
                    _currentLeader = myTp;
                    break;
                }
            }
        }

        public void checkpoint() {
            for (NodeAdmin myNA : _nodes) {
                try {
                    myNA.checkpoint();
                } catch (Throwable aT) {
                    // We can get one of these if the AL is currently OUT_OF_DATE, ignore it
                    //
                    _logger.warn("Exception at checkpoint", aT);
                }
            }

            // Bring any out of date nodes back up
            //
            for (NodeAdmin myNA : _nodes) {
                if (myNA.isOutOfDate())
                    resolve(myNA);
            }
        }

        boolean resolve(NodeAdmin anAdmin) {
            for (NodeAdmin myNA : _nodes) {
                if ((! myNA.isOutOfDate()) && (myNA.lastCheckpointTime() > anAdmin.lastCheckpointTime())) {

                    try {
                        CheckpointStorage.ReadCheckpoint myCkpt = myNA.getLastCheckpoint();

                        try {
                            if (anAdmin.bringUpToDate(myCkpt)) {
                                _logger.info("AL is now back up to date");

                                return true;
                            }
                        } catch (Exception anE) {
                            _logger.warn("Exception at bring up to date", anE);
                        }
                    } catch (Exception anE) {
                        _logger.warn("Exception reading back checkpoint handle", anE);
                    }
                }
            }

            return false;
        }
    }

    private final Environment _env;

    private LongTerm(long aSeed, long aCycles, boolean doCalibrate, long aCkptCycle) throws Exception {
        _env = new Environment(aSeed, aCycles, doCalibrate, aCkptCycle);
        _env.stabilise();
    }

    long getSettleCycles() {
        return _env._settleCycles;
    }

    private static class NodeAdminImpl implements NodeAdmin, Listener {
        private final OrderedMemoryTransportImpl _transport;
        private final ServerDispatcher _dispatcher;
        private final AtomicBoolean _outOfDate = new AtomicBoolean(false);
        private final CheckpointHandling _checkpointer = new CheckpointHandling();
        private final Environment _env;
        private final NetworkDecider _decider;

        NodeAdminImpl(InetSocketAddress aLocalAddr,
                      InetSocketAddress aBroadcastAddr,
                      OrderedMemoryNetwork aNetwork,
                      int aNodeNum,
                      Environment anEnv) {
            _env = anEnv;
            _decider = new NetworkDecider(new Random(_env._rng.nextLong()));

            if (_env._calibrate) {
                _transport = new OrderedMemoryTransportImpl(aLocalAddr, aBroadcastAddr, aNetwork);
            } else {
                _transport = new OrderedMemoryTransportImpl(aLocalAddr, aBroadcastAddr, aNetwork, _decider);
            }

            FileSystem.deleteDirectory(new File(BASEDIR + "node" + Integer.toString(aNodeNum) + "logs"));

            _dispatcher =
                    new ServerDispatcher(new HowlLogger(BASEDIR + "node" + Integer.toString(aNodeNum) + "logs"));

            _dispatcher.add(this);

            try {
                _transport.routeTo(_dispatcher);
                _dispatcher.init(_transport);
            } catch (Exception anE) {
                throw new RuntimeException("Failed to add a dispatcher", anE);
            }
        }

        class NetworkDecider implements OrderedMemoryTransportImpl.RoutingDecisions {
            private final Random _rng;
            private AtomicBoolean _isSettling = new AtomicBoolean(false);
            private AtomicBoolean _haveDropped = new AtomicBoolean(false);

            NetworkDecider(Random aRandom) {
                _rng = aRandom;
            }

            public boolean sendUnreliable(OrderedMemoryNetwork.OrderedMemoryTransport aTransport) {
                /*
                if (_rng.nextInt(100) < 10) {
                    if (_haveDropped.compareAndSet(false, true)) {
                        _logger.warn("ND [ " + aTransport.getLocalAddress() + "] Dropped on send");
                        return false;
                    }
                }
                */

                return true;
            }

            public boolean receive(OrderedMemoryNetwork.OrderedMemoryTransport aTransport) {
                if ((! _isSettling.get()) && (_rng.nextInt(100) < 10)) {
                    if (_haveDropped.compareAndSet(false, true)) {
                        _logger.warn("ND [ " + aTransport.getLocalAddress() + "] Dropped on receive");
                        return false;
                    }
                }

                return true;
            }

            public void settle() {
                _isSettling.set(true);
            }
        }

        /*
         * Create failure state machine at construction (passing in rng).
         *
         * Wedge each of distributed, send and connectTo to hit the state machine.
         * State machine has listener and if it decides to trigger a failure it invokes
         * on that listener so that appropriate implementation can be done.
         *
         * State machine returns type of fail or proceed to the caller. Caller
         * can then determine what it should do (might be stop or continue or ...)
         *
         * State machine not only considers failures to inject but also sweeps it's current
         * list of failures and if any have expired, invokes the listener appropriately
         * to allow appropriate implementation of restore
         *
         */

        public void settle() {
            _decider.settle();
        }

        public void terminate() {
            _transport.terminate();
        }

        public OrderedMemoryNetwork.OrderedMemoryTransport getTransport() {
            return _transport;
        }

        public boolean bringUpToDate(CheckpointStorage.ReadCheckpoint aCkpt) {
            boolean myAnswer = _checkpointer.bringUpToDate(aCkpt, _dispatcher);

            if (myAnswer)
                _outOfDate.set(false);

            return myAnswer;
        }

        public void checkpoint() throws Exception {
            _checkpointer.checkpoint(_dispatcher);
        }

        public CheckpointStorage.ReadCheckpoint getLastCheckpoint() {
            return _checkpointer.getLastCheckpoint();
        }

        public long lastCheckpointTime() {
            return _checkpointer.lastCheckpointTime();
        }

        public boolean isOutOfDate() {
            return _outOfDate.get();
        }

        public void transition(StateEvent anEvent) {
            switch (anEvent.getResult()) {
                case OUT_OF_DATE : {
                    // Seek an instant resolution and if it fails, flag it for later recovery
                    //
                    if (! _env.resolve(this))
                        _outOfDate.set(true);

                    break;
                }

                case UP_TO_DATE : {
                    _outOfDate.set(false);

                    break;
                }
            }
        }
    }

    static class CheckpointHandling {
        private CheckpointStorage _ckptStorage = new MemoryCheckpointStorage();
        private AtomicLong _checkpointTime = new AtomicLong(0);

        boolean bringUpToDate(CheckpointStorage.ReadCheckpoint aCkpt, ServerDispatcher aDispatcher) {
            try {
                ObjectInputStream myOIS = new ObjectInputStream(aCkpt.getStream());
                CheckpointHandle myHandle = (CheckpointHandle) myOIS.readObject();

                try {
                    return aDispatcher.getCore().bringUpToDate(myHandle);
                } catch (Exception anE) {
                    _logger.warn("Exception at bring up to date", anE);
                }
            } catch (Exception anE) {
                _logger.warn("Exception reading back checkpoint handle", anE);
            }

            return false;
        }

        void checkpoint(ServerDispatcher aDispatcher) throws Exception {
            CheckpointHandle myHandle = aDispatcher.getCore().newCheckpoint();
            CheckpointStorage.WriteCheckpoint myCkpt = _ckptStorage.newCheckpoint();
            ObjectOutputStream myStream = new ObjectOutputStream(myCkpt.getStream());
            myStream.writeObject(myHandle);
            myStream.close();

            myCkpt.saved();
            myHandle.saved();

            _checkpointTime.set(System.currentTimeMillis());

            assert(_ckptStorage.numFiles() == 1);
        }

        public CheckpointStorage.ReadCheckpoint getLastCheckpoint() {
            return _ckptStorage.getLastCheckpoint();
        }

        public long lastCheckpointTime() {
            return _checkpointTime.get();
        }
    }

    private void run() throws Exception {
        ClientDispatcher myClient = new ClientDispatcher();
        Transport myTransport = _env._factory.newTransport(null);
        myTransport.routeTo(myClient);
        myClient.init(myTransport);

        cycle(myClient, _env._maxCycles, _env._ckptCycle);

        if (! _env._calibrate) {
            System.out.println("********** Transition to Settling **********");

            _env.settle();

            cycle(myClient, _env._settleCycles, _env._ckptCycle);
        }

        myTransport.terminate();

        _env.terminate();

        _env._factory.stop();
    }

    private void cycle(ClientDispatcher aClient, long aCycles, long aCkptCycle) {
        long opsSinceCkpt = 0;
        long myOpCount = 0;

        while (myOpCount < aCycles) {
            /*
             * Perform a paxos vote - need to react to other leader messages but ignore all else - allows us to
             * cope with strategies that switch our leader to test out election and recover from them (assuming we
             * get the right response)
             */
            ByteBuffer myBuffer = ByteBuffer.allocate(8);
            myBuffer.putLong(myOpCount);
            Proposal myProposal = new Proposal("data", myBuffer.array());

            aClient.send(new Envelope(myProposal),
                    _env._currentLeader.getLocalAddress());

            VoteOutcome myEv = aClient.getNext(10000);

            ++opsSinceCkpt;

            if (myEv.getResult() == VoteOutcome.Reason.OTHER_LEADER) {
                _env.updateLeader(myEv.getLeader());
            } else if (myEv.getResult() == VoteOutcome.Reason.VALUE) {
                if (opsSinceCkpt >= aCkptCycle) {
                    _env.checkpoint();

                    opsSinceCkpt = 0;
                }
            }

            // Round we go again
            //
            myOpCount++;
        }
    }

    public static void main(String[] anArgs) throws Exception {
        Args myArgs = CliFactory.parseArguments(Args.class, anArgs);

        LongTerm myLT =
                new LongTerm(myArgs.getSeed(), myArgs.getCycles(), myArgs.isCalibrate(), myArgs.getCkptCycle());

        long myStart = System.currentTimeMillis();

        myLT.run();

        double myDuration = (System.currentTimeMillis() - myStart) / 1000.0;

        if (myArgs.isCalibrate()) {
            System.out.println("Run for " + myArgs.getCycles() + " cycles took " + myDuration + " seconds");

            double myOpsPerSec = myDuration / myArgs.getCycles();
            double myOpsHour = myOpsPerSec * 60 * 60;

            System.out.println("Calibration recommendation - ops/sec: " + myOpsPerSec +
                    " iterations in an hour would be: " + myOpsHour);
        } else {
            System.out.println("Run for " + (myArgs.getCycles() + myLT.getSettleCycles()) +
                    " cycles took " + myDuration + " seconds");
        }
    }
}
