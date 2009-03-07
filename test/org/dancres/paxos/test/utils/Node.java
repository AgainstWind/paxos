package org.dancres.paxos.test.utils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.dancres.paxos.impl.core.AcceptorLearnerImpl;
import org.dancres.paxos.impl.core.Channel;
import org.dancres.paxos.impl.core.ProposerImpl;
import org.dancres.paxos.impl.core.messages.Operations;
import org.dancres.paxos.impl.core.messages.PaxosMessage;
import org.dancres.paxos.impl.core.messages.ProposerPacket;
import org.dancres.paxos.impl.faildet.FailureDetector;
import org.dancres.paxos.impl.faildet.Heartbeater;
import org.dancres.paxos.impl.faildet.LivenessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node implements PacketListener {

    private static Logger _logger = LoggerFactory.getLogger(Node.class);
    ;
    private InetSocketAddress _addr;
    private PacketQueue _queue;
    private QueueRegistry _qr;
    private BroadcastChannel _bc;
    private AcceptorLearnerImpl _al;
    private ProposerImpl _pi;
    private FailureDetector _fd;
    private Heartbeater _hb;

    public Node(InetSocketAddress anAddr, QueueRegistry aRegistry) {
        _bc = new BroadcastChannel(anAddr, aRegistry);
        _addr = anAddr;
        _queue = new PacketQueue(this);
        _hb = new Heartbeater(_bc);
        _fd = new FailureDetector();
        _al = new AcceptorLearnerImpl();
        _pi = new ProposerImpl(_bc, _fd, _addr);
        _qr = aRegistry;

        _qr.register(anAddr, _queue);

        Thread myHeartbeater = new Thread(_hb);
        myHeartbeater.setDaemon(true);
        myHeartbeater.start();
    }

    public BroadcastChannel getBroadcastChannel() {
        return _bc;
    }

    public void deliver(Packet aPacket) throws Exception {
        PaxosMessage myMessage = aPacket.getMsg();

        switch (myMessage.getType()) {
            case Operations.HEARTBEAT: {
                _fd.processMessage(myMessage, aPacket.getSender());

                break;
            }

            case Operations.PROPOSER_REQ: {
                ProposerPacket myPropPkt = (ProposerPacket) myMessage;
                PaxosMessage myResponse = _al.process(myPropPkt.getOperation());

                if (myResponse != null) {
                    PacketQueue myQueue = _qr.getQueue(new InetSocketAddress(aPacket.getSender().getAddress(), myPropPkt.getPort()));
                    myQueue.add(new Packet(_addr, myResponse));
                }

                break;
            }

            default: {
                _pi.process(myMessage, new QueueChannelImpl(_addr, _qr.getQueue(aPacket.getSender())));
                break;
            }
        }
    }

    public InetSocketAddress getAddr() {
        return _addr;
    }

    public FailureDetector getFailureDetector() {
        return _fd;
    }

    public PacketQueue getQueue() {
        return _queue;
    }
}
