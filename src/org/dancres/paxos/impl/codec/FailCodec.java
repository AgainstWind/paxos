package org.dancres.paxos.impl.codec;

import org.apache.mina.common.IoBuffer;
import org.dancres.paxos.impl.core.messages.Fail;
import org.dancres.paxos.impl.core.messages.Operations;

class FailCodec implements Codec {
    public IoBuffer encode(Object anObject) {
        Fail myFail = (Fail) anObject;

        IoBuffer myBuffer = IoBuffer.allocate(8 + 8 + 4);
        myBuffer.putInt(4 + 8 + 4);
        myBuffer.putInt(Operations.FAIL);
        myBuffer.putLong(myFail.getSeqNum());
        myBuffer.putInt(myFail.getReason());
        myBuffer.flip();
        return myBuffer;
    }

    public Object decode(IoBuffer aBuffer) {
        // Discard the length and operation so remaining data can be processed
        // separately
        aBuffer.getInt();
        aBuffer.getInt();
        long mySeqNum = aBuffer.getLong();
        int myReason = aBuffer.getInt();

        return new Fail(mySeqNum, myReason);
    }
}
