package org.dancres.paxos.impl.io.mina;

import org.apache.mina.common.IoSession;
import org.dancres.paxos.impl.core.Channel;
import org.dancres.paxos.impl.core.messages.PaxosMessage;

public class ChannelImpl implements Channel {

    private IoSession _session;

    public ChannelImpl(IoSession aSession) {
        _session = aSession;
    }

    public void write(PaxosMessage aMessage) {
        _session.write(aMessage);
    }
}
