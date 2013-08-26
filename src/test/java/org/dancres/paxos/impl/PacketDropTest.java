package org.dancres.paxos.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.dancres.paxos.FailureDetector;
import org.dancres.paxos.impl.MessageBasedFailureDetector;
import org.dancres.paxos.impl.faildet.FailureDetectorImpl;
import org.dancres.paxos.test.net.ClientDispatcher;
import org.dancres.paxos.test.net.ServerDispatcher;
import org.dancres.paxos.messages.Operations;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.messages.Envelope;
import org.dancres.paxos.impl.netty.TransportImpl;
import org.dancres.paxos.VoteOutcome;
import org.dancres.paxos.Proposal;
import org.junit.*;

public class PacketDropTest {
    private TransportImpl _tport1;
    private TransportImpl _tport2;

    private ServerDispatcher _node1;
    private ServerDispatcher _node2;

    @Before public void init() throws Exception {
    	_node1 = new ServerDispatcher(new FailureDetectorImpl(5000));
    	_node2 = new ServerDispatcher(new FailureDetectorImpl(5000));
        _tport1 = new TransportImpl();
        _tport1.routeTo(_node1);
        _node1.init(_tport1);

        _tport2 = new DroppingTransportImpl();
        _tport2.routeTo(_node2);
        _node2.init(_tport2);
    }

    @After public void stop() throws Exception {
    	_tport1.terminate();
    	_tport2.terminate();
    }
    
    /**
     * @throws java.lang.Exception
     */
    @Test public void post() throws Exception {
    	ClientDispatcher myClient = new ClientDispatcher();
    	TransportImpl myTransport = new TransportImpl();
        myTransport.routeTo(myClient);
        myClient.init(myTransport);

    	ByteBuffer myBuffer = ByteBuffer.allocate(4);
        myBuffer.putInt(55);

        Proposal myProposal = new Proposal("data", myBuffer.array());
        MessageBasedFailureDetector myFd = _node1.getCore().getCommon().getPrivateFD();

        int myChances = 0;

        while (!myFd.couldComplete()) {
            ++myChances;
            if (myChances == 4)
                Assert.assertTrue("Membership not achieved", false);

            Thread.sleep(5000);
        }

        /*
         * Node2 should be a member as we allow it's heartbeats but no other packets to reach other nodes.
         * If there is no stable majority and we cannot circumvent packet loss we expect the leader to ultimately
         * give up.
         */
        myClient.send(new Envelope(myProposal), _tport1.getLocalAddress());

        VoteOutcome myEv = myClient.getNext(15000);

        Assert.assertFalse((myEv == null));

        Assert.assertTrue(myEv.getResult() == VoteOutcome.Reason.VOTE_TIMEOUT);
    }

    static class DroppingTransportImpl extends TransportImpl {
        private boolean _drop;

        DroppingTransportImpl() throws Exception {
        	super();
        }

        public void send(Packet aPacket, InetSocketAddress anAddress) {
            if (aPacket.getMessage().getType() == Operations.HEARTBEAT)
                super.send(aPacket, anAddress);
        }
    }
}
