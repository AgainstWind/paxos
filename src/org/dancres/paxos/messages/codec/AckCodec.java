package org.dancres.paxos.messages.codec;

import java.nio.ByteBuffer;

import org.dancres.paxos.messages.Ack;
import org.dancres.paxos.messages.Operations;

public class AckCodec implements Codec {
    public ByteBuffer encode(Object anObject) {
        Ack myAck = (Ack) anObject;

        ByteBuffer myBuffer = ByteBuffer.allocate(4 + 8 + 8);
        myBuffer.putInt(Operations.ACK);
        myBuffer.putLong(myAck.getSeqNum());
        myBuffer.putLong(myAck.getNodeId());
        myBuffer.flip();
        return myBuffer;
    }

    public Object decode(ByteBuffer aBuffer) {
        aBuffer.getInt();
        long mySeqNum = aBuffer.getLong();
        long myNodeId = aBuffer.getLong();
        
        return new Ack(mySeqNum, myNodeId);
    }
}
