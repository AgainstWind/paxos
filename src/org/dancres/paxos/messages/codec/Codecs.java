package org.dancres.paxos.messages.codec;

import java.nio.ByteBuffer;

import org.dancres.paxos.messages.PaxosMessage;

public class Codecs {
    public static final Codec[] CODECS = new Codec[] {
        new HeartbeatCodec(), new EmptyCodec(), new PostCodec(), new CollectCodec(), new LastCodec(),
            new BeginCodec(), new AcceptCodec(), new SuccessCodec(), new AckCodec(), new OldRoundCodec(),
            new EmptyCodec(), new FailCodec(), new CompleteCodec()
    };

    public static byte[] encode(PaxosMessage aMessage) {
        int myType = aMessage.getType();
        Codec myCodec = Codecs.CODECS[myType];

        return myCodec.encode(aMessage).array();
    }

    public static PaxosMessage decode(byte[] aBuffer) {
        ByteBuffer myBuffer = ByteBuffer.wrap(aBuffer);
        int myOp;
        
		myOp = myBuffer.getInt(4);

        Codec myCodec = Codecs.CODECS[myOp];

        return (PaxosMessage) myCodec.decode(myBuffer);
    }

}
