package org.dancres.paxos.test.utils;

import java.net.InetSocketAddress;
import org.dancres.paxos.impl.core.AcceptorLearnerImpl;
import org.dancres.paxos.impl.core.Channel;
import org.dancres.paxos.impl.core.ProposerImpl;
import org.dancres.paxos.impl.core.messages.Operations;
import org.dancres.paxos.impl.core.messages.PaxosMessage;
import org.dancres.paxos.impl.core.messages.ProposerPacket;
import org.dancres.paxos.impl.faildet.FailureDetector;
import org.dancres.paxos.impl.faildet.Heartbeater;
import org.dancres.paxos.impl.util.AddressImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emulates a paxos network process, acting as both Leader and Acceptor/Learner.
 * 
 * @author dan
 */
public class Node implements PacketListener {

    private static Logger _logger = LoggerFactory.getLogger(Node.class);

    private BroadcastChannel _bc;
    private InetSocketAddress _addr;
    private ChannelRegistry _qr;
    private AcceptorLearnerImpl _al;
    private ProposerImpl _pi;
    private FailureDetector _fd;
    private Heartbeater _hb;
    private PacketQueue _pq;

    /**
     * @param anAddr is the address this node should use
     * @param aBroadChannel is the broadcast channel to use for e.g. heartbeats
     * @param aRegistry is the registry from which channels for other addresses can be obtained
     */
    public Node(InetSocketAddress anAddr, BroadcastChannel aBroadChannel, ChannelRegistry aRegistry, long anUnresponsivenessThreshold) {
        _bc = aBroadChannel;
        _addr = anAddr;
        _hb = new Heartbeater(_bc);
        _fd = new FailureDetector(anUnresponsivenessThreshold);
        _al = new AcceptorLearnerImpl();
        _pi = new ProposerImpl(_bc, _fd, _addr);
        _qr = aRegistry;
        _pq = new PacketQueueImpl(this);
    }

    public void startup() {
        Thread myHeartbeater = new Thread(_hb);
        myHeartbeater.setDaemon(true);
        myHeartbeater.start();
    }

    public PacketQueue getQueue() {
        return _pq;
    }

    public void deliver(Packet aPacket) throws Exception {
        PaxosMessage myMessage = aPacket.getMsg();

        switch (myMessage.getType()) {
            case Operations.HEARTBEAT: {
                _fd.processMessage(myMessage, new AddressImpl(aPacket.getSender()));

                break;
            }

            case Operations.PROPOSER_REQ: {
                ProposerPacket myPropPkt = (ProposerPacket) myMessage;
                PaxosMessage myResponse = _al.process(myPropPkt.getOperation());

                if (myResponse != null) {
                    Channel myChannel = _qr.getChannel(new InetSocketAddress(
                            aPacket.getSender().getAddress(),
                            aPacket.getSender().getPort()));
                    myChannel.write(myResponse);
                }

                break;
            }

            default: {
                _pi.process(myMessage, _qr.getChannel(aPacket.getSender()), new AddressImpl(aPacket.getSender()));
                break;
            }
        }
    }

    public FailureDetector getFailureDetector() {
        return _fd;
    }
}
