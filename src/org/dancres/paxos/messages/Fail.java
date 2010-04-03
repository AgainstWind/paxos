package org.dancres.paxos.messages;

import org.dancres.paxos.NodeId;

public class Fail implements PaxosMessage {
    private long _seqNum;
    private int _reason;

    public Fail(long aSeqNum, int aReason) {
        _seqNum = aSeqNum;
        _reason = aReason;
    }

    public int getType() {
        return Operations.FAIL;
    }
    
    public long getNodeId() {
    	return NodeId.BROADCAST.asLong();
    }
    
    public short getClassification() {
    	return CLIENT;
    }
    
    public long getSeqNum() {
        return _seqNum;
    }

    public int getReason() {
        return _reason;
    }

    public String toString() {
        return "Fail: " + _seqNum;
    }
}
