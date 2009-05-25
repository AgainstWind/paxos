package org.dancres.paxos.impl.mina.io;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.mina.common.IoSession;
import org.dancres.paxos.impl.core.Address;
import org.dancres.paxos.impl.core.Transport;
import org.dancres.paxos.impl.core.messages.Operations;
import org.dancres.paxos.impl.core.messages.PaxosMessage;
import org.dancres.paxos.impl.mina.io.ProposerHeader;
import org.dancres.paxos.impl.util.AddressImpl;

public class TransportImpl implements Transport {

    private ConcurrentHashMap<Address, IoSession> _sessions = new ConcurrentHashMap<Address, IoSession>();
    private InetSocketAddress _addr;

    public TransportImpl(InetSocketAddress aNodeAddr, IoSession aBroadcastSession) {
        super();
        _sessions.put(Address.BROADCAST, aBroadcastSession);
        _addr = aNodeAddr;
    }

    public void send(PaxosMessage aMessage, Address anAddress) {
        PaxosMessage myMessage;

        switch (aMessage.getType()) {
            case Operations.COLLECT :
            case Operations.BEGIN :
            case Operations.SUCCESS : {
                myMessage = new ProposerHeader(aMessage, _addr.getPort());
                break;
            }

            default : {
                 myMessage = aMessage;
                 break;
            }
        }

        IoSession mySession = (IoSession) _sessions.get(anAddress);
        mySession.write(myMessage);
    }

    public void register(IoSession aSession) {
        _sessions.putIfAbsent(new AddressImpl(aSession.getRemoteAddress()), aSession);
    }
}
