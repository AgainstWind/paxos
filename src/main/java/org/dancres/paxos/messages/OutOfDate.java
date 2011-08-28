package org.dancres.paxos.messages;

import java.net.InetSocketAddress;

public class OutOfDate implements PaxosMessage {
    private InetSocketAddress _nodeId;

    public OutOfDate(InetSocketAddress aNodeId) {
        _nodeId = aNodeId;
    }

    public InetSocketAddress getNodeId() {
        return _nodeId;
    }

    public short getClassification() {
        return RECOVERY;
    }

    public long getSeqNum() {
        // No meaningful seqnum
        //
        return -1;
    }

    public int getType() {
        return Operations.OUTOFDATE;
    }

    public String toString() {
        return "OutOfDate: " + _nodeId + " ]";
    }
}