package org.dancres.paxos.impl.core.messages;

public interface PaxosMessage {
    public int getType();
    public long getSeqNum();
}
