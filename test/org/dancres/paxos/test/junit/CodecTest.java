package org.dancres.paxos.test.junit;

import org.dancres.paxos.ConsolidatedValue;
import org.dancres.paxos.messages.codec.Codecs;
import org.dancres.paxos.messages.Accept;
import org.dancres.paxos.messages.Ack;
import org.dancres.paxos.messages.Begin;
import org.dancres.paxos.messages.Collect;
import org.dancres.paxos.messages.Complete;
import org.dancres.paxos.messages.Fail;
import org.dancres.paxos.impl.faildet.Heartbeat;
import org.dancres.paxos.messages.Last;
import org.dancres.paxos.messages.OldRound;
import org.dancres.paxos.messages.Post;
import org.dancres.paxos.messages.Success;
import org.junit.*;

public class CodecTest {
    @Test public void accept() throws Exception {
        Accept myAccept = new Accept(1, 2);

        byte[] myBuffer = Codecs.encode(myAccept);

        Accept myAccept2 = (Accept) Codecs.decode(myBuffer);

        Assert.assertTrue(myAccept.getRndNumber() == myAccept2.getRndNumber());
        Assert.assertTrue(myAccept.getSeqNum() == myAccept2.getSeqNum());
    }

    @Test public void complete() throws Exception {    
        Complete myComp = new Complete(1);

        byte[] myBuffer = Codecs.encode(myComp);

        Complete myComp2 = (Complete) Codecs.decode(myBuffer);

        Assert.assertTrue(myComp.getSeqNum() == myComp2.getSeqNum());
    }
    
    @Test public void ack() throws Exception {
        Ack myAck = new Ack(1);

        byte[] myBuffer = Codecs.encode(myAck);

        Ack myAck2 = (Ack) Codecs.decode(myBuffer);

        Assert.assertTrue(myAck.getSeqNum() == myAck2.getSeqNum());
    }

    @Test public void fail() throws Exception {
        Fail myFail = new Fail(1, 2);

        byte[] myBuffer = Codecs.encode(myFail);

        Fail myFail2 = (Fail) Codecs.decode(myBuffer);

        Assert.assertTrue(myFail.getSeqNum() == myFail2.getSeqNum());
        Assert.assertTrue(myFail.getReason() == myFail2.getReason());
    }

    @Test public void begin() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};
        
        Begin myBegin = new Begin(1, 2, new ConsolidatedValue(myData, myHandback), 3);

        byte[] myBuffer = Codecs.encode(myBegin);

        Begin myBegin2 = (Begin) Codecs.decode(myBuffer);

        Assert.assertEquals(myBegin.getSeqNum(), myBegin2.getSeqNum());
        Assert.assertEquals(myBegin.getRndNumber(), myBegin2.getRndNumber());
        Assert.assertEquals(myBegin.getNodeId(), myBegin2.getNodeId());
        Assert.assertEquals(myBegin.getConsolidatedValue(), myBegin2.getConsolidatedValue());
    }

    @Test public void collect() throws Exception {
        Collect myCollect = new Collect(1, 2, 3);

        byte[] myBuffer = Codecs.encode(myCollect);

        Collect myCollect2 = (Collect) Codecs.decode(myBuffer);

        Assert.assertEquals(myCollect.getSeqNum(), myCollect.getSeqNum());
        Assert.assertEquals(myCollect.getRndNumber(), myCollect2.getRndNumber());
        Assert.assertEquals(myCollect.getNodeId(), myCollect2.getNodeId());
    }

    @Test public void heartbeat() throws Exception {
        Heartbeat myHeartbeat = new Heartbeat();

        byte[] myBuffer = Codecs.encode(myHeartbeat);

        Heartbeat myHeartbeat2 = (Heartbeat) Codecs.decode(myBuffer);
    }

    @Test public void last() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};

        Last myLast = new Last(0, 1, 2, new ConsolidatedValue(myData, myHandback));

        byte[] myBuffer = Codecs.encode(myLast);

        Last myLast2 = (Last) Codecs.decode(myBuffer);

        Assert.assertEquals(myLast.getSeqNum(), myLast.getSeqNum());
        Assert.assertEquals(myLast.getLowWatermark(), myLast2.getLowWatermark());
        Assert.assertEquals(myLast.getRndNumber(), myLast2.getRndNumber());
        Assert.assertEquals(myLast.getConsolidatedValue(), myLast2.getConsolidatedValue());
    }

    @Test public void oldRound() throws Exception {
        OldRound myOldRound = new OldRound(1, 2, 3);

        byte[] myBuffer = Codecs.encode(myOldRound);

        OldRound myOldRound2 = (OldRound) Codecs.decode(myBuffer);

        Assert.assertEquals(myOldRound.getSeqNum(), myOldRound2.getSeqNum());
        Assert.assertEquals(myOldRound.getNodeId(), myOldRound2.getNodeId());
        Assert.assertEquals(myOldRound.getLastRound(), myOldRound2.getLastRound());
    }

    @Test public void post() throws Exception {
        byte[] myData = {55};
        byte[] myOther = {65};

        Post myPost = new Post(myData, myOther);

        byte[] myBuffer = Codecs.encode(myPost);

        Post myPost2 = (Post) Codecs.decode(myBuffer);

        Assert.assertEquals(myPost.getValue().length, myPost2.getValue().length);
        Assert.assertEquals(myPost.getValue()[0], myPost2.getValue()[0]);
        Assert.assertEquals(myPost.getHandback().length, myPost2.getHandback().length);
        Assert.assertEquals(myPost.getHandback()[0], myPost2.getHandback()[0]);
    }

    @Test public void success() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};

        Success mySuccess = new Success(1, 2, new ConsolidatedValue(myData, myHandback), 3);

        byte[] myBuffer = Codecs.encode(mySuccess);

        Success mySuccess2 = (Success) Codecs.decode(myBuffer);

        Assert.assertEquals(mySuccess.getSeqNum(), mySuccess2.getSeqNum());
        Assert.assertEquals(mySuccess.getRndNum(), mySuccess2.getRndNum());
        Assert.assertEquals(mySuccess.getNodeId(), mySuccess2.getNodeId());
        Assert.assertEquals(mySuccess.getConsolidatedValue(), mySuccess2.getConsolidatedValue());
    }

    private void dump(byte[] aBuffer) {
        for (int i = 0; i < aBuffer.length; i++) {
            System.err.print(Integer.toHexString(aBuffer[i]) + " ");
        }

        System.err.println();
    }
}
