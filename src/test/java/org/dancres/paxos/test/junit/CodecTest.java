package org.dancres.paxos.test.junit;

import org.dancres.paxos.VoteOutcome;
import org.dancres.paxos.Proposal;
import org.dancres.paxos.messages.*;
import org.dancres.paxos.messages.codec.Codecs;
import org.dancres.paxos.impl.faildet.Heartbeat;
import org.dancres.paxos.test.utils.Utils;
import org.junit.*;

import java.net.InetSocketAddress;

public class CodecTest {
    private InetSocketAddress _testAddress = Utils.getTestAddress();

    @Test public void outOfDate() throws Exception {
        OutOfDate myOOD = new OutOfDate(_testAddress);

        byte[] myBuffer = Codecs.encode(myOOD);

        OutOfDate myOOD2 = (OutOfDate) Codecs.decode(myBuffer);
        Assert.assertTrue(myOOD.getNodeId().equals(myOOD2.getNodeId()));
    }

    @Test public void accept() throws Exception {
        Accept myAccept = new Accept(1, 2, _testAddress);

        byte[] myBuffer = Codecs.encode(myAccept);

        Accept myAccept2 = (Accept) Codecs.decode(myBuffer);

        Assert.assertTrue(myAccept.getRndNumber() == myAccept2.getRndNumber());
        Assert.assertTrue(myAccept.getSeqNum() == myAccept2.getSeqNum());
        Assert.assertTrue(myAccept.getNodeId().equals( myAccept2.getNodeId()));
    }

    @Test public void event() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};
        Proposal myVal = new Proposal();
        myVal.put("data", myData);
        myVal.put("handback", myHandback);
        
        VoteOutcome myEvent = new VoteOutcome(1, 2, 3, myVal, _testAddress);

        byte[] myBuffer = Codecs.encode(myEvent);

        VoteOutcome myEvent2 = (VoteOutcome) Codecs.decode(myBuffer);

        Assert.assertEquals(myEvent.getSeqNum(), myEvent2.getSeqNum());
        Assert.assertEquals(myEvent.getResult(), myEvent2.getResult());
        Assert.assertEquals(myEvent.getNodeId(), myEvent2.getNodeId());
        Assert.assertEquals(myEvent.getValues(), myEvent2.getValues());
    }
    
    @Test public void begin() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};
        Proposal myVal = new Proposal();
        myVal.put("data", myData);
        myVal.put("handback", myHandback);
        
        Begin myBegin = new Begin(1, 2, myVal, _testAddress);

        byte[] myBuffer = Codecs.encode(myBegin);

        Begin myBegin2 = (Begin) Codecs.decode(myBuffer);

        Assert.assertEquals(myBegin.getSeqNum(), myBegin2.getSeqNum());
        Assert.assertEquals(myBegin.getRndNumber(), myBegin2.getRndNumber());
        Assert.assertEquals(myBegin.getNodeId(), myBegin2.getNodeId());
        Assert.assertEquals(myBegin.getConsolidatedValue(), myBegin2.getConsolidatedValue());
    }

    @Test public void collect() throws Exception {
        Collect myCollect = new Collect(1, 2, _testAddress);

        byte[] myBuffer = Codecs.encode(myCollect);

        Collect myCollect2 = (Collect) Codecs.decode(myBuffer);

        Assert.assertEquals(myCollect.getSeqNum(), myCollect.getSeqNum());
        Assert.assertEquals(myCollect.getRndNumber(), myCollect2.getRndNumber());
        Assert.assertEquals(myCollect.getNodeId(), myCollect2.getNodeId());
    }

    @Test public void heartbeat() throws Exception {
        String myMeta = "MetaData";
        Heartbeat myHeartbeat = new Heartbeat(_testAddress, myMeta.getBytes());

        byte[] myBuffer = Codecs.encode(myHeartbeat);

        Heartbeat myHeartbeat2 = (Heartbeat) Codecs.decode(myBuffer);
        
        Assert.assertEquals(myHeartbeat.getNodeId(), myHeartbeat2.getNodeId());
        Assert.assertEquals(myMeta, new String(myHeartbeat.getMetaData()));
    }

    @Test public void last() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};
        Proposal myVal = new Proposal();
        myVal.put("data", myData);
        myVal.put("handback", myHandback);
        
        Last myLast = new Last(0, 1, 2, myVal, _testAddress);

        byte[] myBuffer = Codecs.encode(myLast);

        Last myLast2 = (Last) Codecs.decode(myBuffer);

        Assert.assertEquals(myLast.getSeqNum(), myLast.getSeqNum());
        Assert.assertEquals(myLast.getLowWatermark(), myLast2.getLowWatermark());
        Assert.assertEquals(myLast.getRndNumber(), myLast2.getRndNumber());
        Assert.assertEquals(myLast.getConsolidatedValue(), myLast2.getConsolidatedValue());
        Assert.assertEquals(myLast.getNodeId(), myLast2.getNodeId());
    }

    @Test public void oldRound() throws Exception {
        OldRound myOldRound = new OldRound(1, _testAddress, 3, _testAddress);

        byte[] myBuffer = Codecs.encode(myOldRound);

        OldRound myOldRound2 = (OldRound) Codecs.decode(myBuffer);

        Assert.assertEquals(myOldRound.getSeqNum(), myOldRound2.getSeqNum());
        Assert.assertEquals(myOldRound.getLeaderNodeId(), myOldRound2.getLeaderNodeId());
        Assert.assertEquals(myOldRound.getLastRound(), myOldRound2.getLastRound());
        Assert.assertEquals(myOldRound.getNodeId(), myOldRound2.getNodeId());
    }

    @Test public void post() throws Exception {
        byte[] myData = {55};
        Proposal myProp = new Proposal("data", myData);

        Envelope myEnv = new Envelope(myProp, _testAddress);

        byte[] myBuffer = Codecs.encode(myEnv);

        Envelope myEnv2 = (Envelope) Codecs.decode(myBuffer);

        Assert.assertEquals(myEnv.getValue(), myEnv2.getValue());
        Assert.assertEquals(myEnv.getNodeId(), myEnv2.getNodeId());
    }

    @Test public void success() throws Exception {
        byte[] myData = {55};
        byte[] myHandback = {56};

        Success mySuccess = new Success(1, 2, _testAddress);

        byte[] myBuffer = Codecs.encode(mySuccess);

        Success mySuccess2 = (Success) Codecs.decode(myBuffer);

        Assert.assertEquals(mySuccess.getSeqNum(), mySuccess2.getSeqNum());
        Assert.assertEquals(mySuccess.getRndNum(), mySuccess2.getRndNum());
        Assert.assertEquals(mySuccess.getNodeId(), mySuccess2.getNodeId());
    }

    @Test public void need() throws Exception {
    	Need myNeed = new Need(1, 2, _testAddress);
    	
    	byte[] myBuffer = Codecs.encode(myNeed);
    	
    	Need myNeed2 = (Need) Codecs.decode(myBuffer);
    	
    	Assert.assertEquals(myNeed.getMinSeq(), myNeed2.getMinSeq());
    	Assert.assertEquals(myNeed.getMaxSeq(), myNeed2.getMaxSeq());
    	Assert.assertEquals(myNeed.getNodeId(), myNeed2.getNodeId());    	
    }
    
    private void dump(byte[] aBuffer) {
        for (int i = 0; i < aBuffer.length; i++) {
            System.err.print(Integer.toHexString(aBuffer[i]) + " ");
        }

        System.err.println();
    }
}
