package org.dancres.paxos.test.junit;

import junit.framework.Assert;
import org.dancres.paxos.Proposal;
import org.dancres.paxos.VoteOutcome;
import org.dancres.paxos.impl.Instance;
import org.dancres.paxos.impl.InstanceStateFactory;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class InstanceStateTest {
    @Test
    public void oneLeader() {
        InstanceStateFactory myFactory = new InstanceStateFactory(-1, 0);

        // Not yet a leader, so max of one in-flight instance applies (or should)
        Instance myFirstInstance = myFactory.nextInstance(1);

        Instance mySecondInstance = myFactory.nextInstance(1);

        Assert.assertNull(mySecondInstance);

        myFactory.conclusion(myFirstInstance,
                new VoteOutcome(VoteOutcome.Reason.VALUE, myFirstInstance.getSeqNum(), myFirstInstance.getRound(),
                        Proposal.NO_VALUE, null));

        int myCount = 0;

        while ((myFactory.nextInstance(1) != null) && (myCount <= InstanceStateFactory.MAX_INFLIGHT))
            myCount++;

        Assert.assertNull(myFactory.nextInstance(1));
        Assert.assertEquals(InstanceStateFactory.MAX_INFLIGHT, myCount);
    }

    @Test
    public void correctSequence() {
        InstanceStateFactory myFactory = new InstanceStateFactory(-1, 0);

        for (int i = 0; i < 10; i++) {
            Instance myInstance = myFactory.nextInstance(1);

            Assert.assertEquals(i, myInstance.getSeqNum());

            myFactory.conclusion(myInstance,
                    new VoteOutcome(VoteOutcome.Reason.VALUE, myInstance.getSeqNum(), myInstance.getRound(),
                            Proposal.NO_VALUE, null));
        }

        for (int i = 10; i < 20; i++) {
            Instance myInstance = myFactory.nextInstance(1);

            Assert.assertEquals(i, myInstance.getSeqNum());

            myFactory.conclusion(myInstance,
                    new VoteOutcome(VoteOutcome.Reason.OTHER_VALUE, myInstance.getSeqNum(), myInstance.getRound(),
                            Proposal.NO_VALUE, null));
        }
    }

    @Test
    public void reuseSequenceOnFail() {
        InstanceStateFactory myFactory = new InstanceStateFactory(-1, 0);
        Instance myInstance = myFactory.nextInstance(1);

        Assert.assertNotNull(myInstance);
        Assert.assertEquals(0, myInstance.getSeqNum());

        myFactory.conclusion(myInstance,
                new VoteOutcome(VoteOutcome.Reason.VOTE_TIMEOUT, myInstance.getSeqNum(), myInstance.getRound(),
                        Proposal.NO_VALUE, null));

        myInstance = myFactory.nextInstance(1);

        Assert.assertNotNull(myInstance);
        Assert.assertEquals(0, myInstance.getSeqNum());

        myFactory.conclusion(myInstance,
                new VoteOutcome(VoteOutcome.Reason.BAD_MEMBERSHIP, myInstance.getSeqNum(), myInstance.getRound(),
                        Proposal.NO_VALUE, null));

        myInstance = myFactory.nextInstance(1);

        Assert.assertNotNull(myInstance);
        Assert.assertEquals(0, myInstance.getSeqNum());
    }

    @Test
    public void reuseSomeOnOtherLeader() {
        // This test can't run if inflight is too small
        //
        Assert.assertTrue(InstanceStateFactory.MAX_INFLIGHT >= 3);

        InstanceStateFactory myFactory = new InstanceStateFactory(-1, 0);
        Instance myInstance = myFactory.nextInstance(1);

        Assert.assertNotNull(myInstance);
        Assert.assertEquals(0, myInstance.getSeqNum());

        myFactory.conclusion(myInstance,
                new VoteOutcome(VoteOutcome.Reason.VALUE, myInstance.getSeqNum(), myInstance.getRound(),
                        Proposal.NO_VALUE, null));

        List<Instance> myInstances = new LinkedList<Instance>();

        for (int i = 0; i < (InstanceStateFactory.MAX_INFLIGHT - 1); i++) {
            myInstances.add(myFactory.nextInstance(1));
        }

        // Inject a retryable failure so we can ensure reservations are vanquished
        //
        Instance myFailedInstance = myInstances.get(2);
        myFactory.conclusion(myFailedInstance,
                new VoteOutcome(VoteOutcome.Reason.VOTE_TIMEOUT, myFailedInstance.getSeqNum(),
                        myFailedInstance.getRound(), Proposal.NO_VALUE, null));

        Instance mySplitInstance = myInstances.get(myInstances.size() - 1);
        myFactory.conclusion(mySplitInstance,
                new VoteOutcome(VoteOutcome.Reason.OTHER_LEADER, mySplitInstance.getSeqNum(),
                        mySplitInstance.getRound(), Proposal.NO_VALUE, null));

        myInstance = myFactory.nextInstance(1);

        Assert.assertEquals(mySplitInstance.getSeqNum() + 1, myInstance.getSeqNum());
        Assert.assertEquals(2, myInstance.getRound());
    }
}
