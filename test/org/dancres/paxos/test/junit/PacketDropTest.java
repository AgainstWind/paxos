package org.dancres.paxos.test.junit;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.dancres.paxos.impl.net.ClientDispatcher;
import org.dancres.paxos.impl.net.ServerDispatcher;
import org.dancres.paxos.messages.Operations;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.messages.Envelope;
import org.dancres.paxos.impl.netty.TransportImpl;
import org.dancres.paxos.impl.FailureDetector;
import org.dancres.paxos.Event;
import org.dancres.paxos.Proposal;
import org.junit.*;

public class PacketDropTest {
    private TransportImpl _tport1;
    private TransportImpl _tport2;

    private ServerDispatcher _node1;
    private ServerDispatcher _node2;

    @Before public void init() throws Exception {
    	_node1 = new ServerDispatcher(5000);
    	_node2 = new ServerDispatcher(5000);
        _tport1 = new TransportImpl();
        _tport1.add(_node1);
        _tport2 = new DroppingTransportImpl();
        _tport2.add(_node2);
    }

    @After public void stop() throws Exception {
    	_node1.stop();
    	_node2.stop();
    }
    
    /**
     * @throws java.lang.Exception
     */
    @Test public void post() throws Exception {
    	ClientDispatcher myClient = new ClientDispatcher();
    	TransportImpl myTransport = new TransportImpl();
        myTransport.add(myClient);

    	ByteBuffer myBuffer = ByteBuffer.allocate(4);
        myBuffer.putInt(55);

        Proposal myProposal = new Proposal("data", myBuffer.array());
        FailureDetector myFd = _node1.getFailureDetector();

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
        myClient.send(new Envelope(myProposal, myTransport.getLocalAddress()),
        		_tport1.getLocalAddress());

        Envelope myEnv = myClient.getNext(15000);

        Assert.assertFalse((myEnv == null));

        Assert.assertTrue(ServerDispatcher.getResult(myEnv) == Event.Reason.VOTE_TIMEOUT);
    }

    static class DroppingTransportImpl extends TransportImpl {
        private boolean _drop;

        DroppingTransportImpl() throws Exception {
        	super();
        }

        public void send(PaxosMessage aMessage, InetSocketAddress anAddress) {
            if (aMessage.getType() == Operations.HEARTBEAT)
                super.send(aMessage, anAddress);
        }
    }
}
