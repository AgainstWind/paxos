package org.dancres.paxos.test.junit;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.dancres.paxos.impl.core.messages.Operations;
import org.dancres.paxos.impl.core.messages.PaxosMessage;
import org.dancres.paxos.impl.io.mina.Post;
import org.dancres.paxos.impl.util.AddressImpl;
import org.dancres.paxos.test.utils.AddressGenerator;
import org.dancres.paxos.test.utils.ClientPacketFilter;
import org.dancres.paxos.test.utils.Node;
import org.dancres.paxos.test.utils.Packet;
import org.dancres.paxos.test.utils.PacketQueue;
import org.dancres.paxos.test.utils.PacketQueueImpl;
import org.dancres.paxos.test.utils.TransportImpl;
import org.junit.*;
import org.junit.Assert.*;

public class NoMajorityTest {
    private AddressGenerator _allocator;

    private InetSocketAddress _addr1;

    private Node _node1;

    private TransportImpl _tport1;

    @Before public void init() throws Exception {
        _allocator = new AddressGenerator();

        _addr1 = _allocator.allocate();

        _tport1 = new TransportImpl(_addr1);

        _node1 = new Node(_addr1, _tport1, 5000);

        /*
         * "Network" mappings for node1's broadcast channel
         *
         * addr1 maps to a channel that sends packets from addr1 to node1's queue
         */
        _tport1.add(_addr1, _node1.getQueue());

        _node1.startup();
    }

    @Test public void post() throws Exception {
        PacketQueue myQueue = new ClientPacketFilter(new PacketQueueImpl());
        InetSocketAddress myAddr = _allocator.allocate();

        _tport1.add(myAddr, myQueue);

        ByteBuffer myBuffer = ByteBuffer.allocate(4);
        myBuffer.putInt(55);

        Thread.sleep(5000);

        _node1.getQueue().add(new Packet(new AddressImpl(myAddr), new Post(myBuffer.array())));
        Packet myPacket = myQueue.getNext(10000);

        Assert.assertFalse((myPacket == null));

        PaxosMessage myMsg = myPacket.getMsg();

        Assert.assertTrue(myMsg.getType() == Operations.FAIL);
    }
}
