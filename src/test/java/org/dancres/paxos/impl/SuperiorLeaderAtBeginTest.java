package org.dancres.paxos.impl;

import java.nio.ByteBuffer;

import org.dancres.paxos.*;
import org.dancres.paxos.impl.Transport.Packet;
import org.dancres.paxos.impl.faildet.FailureDetectorImpl;
import org.dancres.paxos.storage.MemoryLogStorage;
import org.dancres.paxos.test.junit.FDUtil;
import org.dancres.paxos.test.net.ClientDispatcher;
import org.dancres.paxos.test.net.ServerDispatcher;
import org.dancres.paxos.messages.Begin;
import org.dancres.paxos.messages.OldRound;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.messages.Envelope;
import org.dancres.paxos.impl.netty.TransportImpl;
import org.junit.*;

public class SuperiorLeaderAtBeginTest {
    private ServerDispatcher _node1;
    private ServerDispatcher _node2;

    private TransportImpl _tport1;
    private TransportImpl _tport2;

    @Before public void init() throws Exception {
        _node1 = new ServerDispatcher();

        Core myCore = new Core(new MemoryLogStorage(),
                CheckpointHandle.NO_CHECKPOINT, new Listener() {
            public void transition(StateEvent anEvent) {
            }
        });

        DroppingListenerImpl myDispatcher = new DroppingListenerImpl(myCore);

        _node2 = new ServerDispatcher(myCore, myDispatcher);

        _tport1 = new TransportImpl(new FailureDetectorImpl(5000, FailureDetectorImpl.OPEN_PIN));
        _tport1.routeTo(_node1);
        _node1.init(_tport1);

        _tport2 = new TransportImpl(new FailureDetectorImpl(5000, FailureDetectorImpl.OPEN_PIN));
        _tport2.routeTo(_node2);
        _node2.init(_tport2);
    }

    @After public void stop() throws Exception {
    	_tport1.terminate();
    	_tport2.terminate();
    }
    
    @Test public void post() throws Exception {
    	ClientDispatcher myClient = new ClientDispatcher();
    	TransportImpl myTransport = new TransportImpl(null);
        myTransport.routeTo(myClient);
        myClient.init(myTransport);

        ByteBuffer myBuffer = ByteBuffer.allocate(4);
        myBuffer.putInt(55);

        Proposal myProposal = new Proposal("data", myBuffer.array());        
        FailureDetector myFd = _tport1.getFD();

        FDUtil.ensureFD(myFd);

        myClient.send(new Envelope(myProposal), _tport1.getLocalAddress());

        VoteOutcome myEv = myClient.getNext(10000);

        Assert.assertFalse((myEv == null));

        Assert.assertTrue(myEv.getResult() == VoteOutcome.Reason.OTHER_LEADER);
    }

    class DroppingListenerImpl implements Transport.Dispatcher {
        private Core _core;
        private Transport _tp;

        DroppingListenerImpl(Core aCore) {
            _core = aCore;
        }

        public void init(Transport aTransport) throws Exception {
            _tp = aTransport;
            _core.init(aTransport);
        }

        public void terminate() {
            _core.terminate();
        }

        public boolean messageReceived(Packet aPacket) {
            PaxosMessage myMessage = aPacket.getMessage();

            if (_core.getAcceptorLearner().accepts(aPacket)) {
                if (myMessage.getType() == PaxosMessage.Types.BEGIN) {
                    Begin myBegin = (Begin) myMessage;

                    _tp.send(
                            _tp.getPickler().newPacket(new OldRound(myBegin.getSeqNum(), _tp.getLocalAddress(),
                                    myBegin.getRndNumber() + 1)), aPacket.getSource());

                    return true;
                } else
                    return _core.messageReceived(aPacket);
            } else {
                return _core.messageReceived(aPacket);
            }
        }
    }
}
