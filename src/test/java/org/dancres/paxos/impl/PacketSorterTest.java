package org.dancres.paxos.impl;

import junit.framework.Assert;
import org.dancres.paxos.impl.PacketSorter;
import org.dancres.paxos.impl.Transport;
import org.dancres.paxos.messages.Collect;
import org.dancres.paxos.messages.Need;
import org.dancres.paxos.test.net.FakePacket;
import org.junit.Test;

import java.net.InetSocketAddress;

public class PacketSorterTest {
    @Test
    public void checkConsume() {
        PacketSorter mySorter = new PacketSorter();
        Tester myTester = new Tester(false);

        for (long mySeq = 0; mySeq < 5; mySeq++) {
            mySorter.add(new FakePacket(new Collect(mySeq, 1)));
            mySorter.process(mySeq - 1, myTester);
        }

        Assert.assertEquals(5, myTester._consumed);
        Assert.assertEquals(false, myTester._recoveryRequested);
        Assert.assertEquals(0, mySorter.numPackets());
    }

    @Test
    public void checkPositiveRecoverTrigger() {
        PacketSorter mySorter = new PacketSorter();
        Tester myTester = new Tester(true);

        mySorter.add(new FakePacket(new Collect(5, 1)));
        mySorter.process(0, myTester);

        Assert.assertEquals(0, myTester._consumed);
        Assert.assertEquals(true, myTester._recoveryRequested);
        Assert.assertEquals(1, mySorter.numPackets());
    }

    @Test
    public void checkNegativeRecoverTrigger() {
        PacketSorter mySorter = new PacketSorter();
        Tester myTester = new Tester(false);

        mySorter.add(new FakePacket(new Collect(4, 1)));
        mySorter.process(0, myTester);
        mySorter.add(new FakePacket(new Collect(5, 1)));
        mySorter.process(0, myTester);

        Assert.assertEquals(0, myTester._consumed);
        Assert.assertEquals(true, myTester._recoveryRequested);
        Assert.assertEquals(2, mySorter.numPackets());
    }

    @Test
    public void checkRecoveryConsume() {
        PacketSorter mySorter = new PacketSorter();
        Tester myTester = new Tester(true);

        // Recovery triggered immediately
        //
        mySorter.add(new FakePacket(new Collect(4, 1)));
        mySorter.process(-1, myTester);

        // Simulate arrival of missing packets from elsewhere
        //
        mySorter.add(new FakePacket(new Collect(0, 1)));
        mySorter.process(-1, myTester);

        mySorter.add(new FakePacket(new Collect(1, 1)));
        mySorter.process(0, myTester);

        mySorter.add(new FakePacket(new Collect(2, 1)));
        mySorter.process(1, myTester);

        mySorter.add(new FakePacket(new Collect(3, 1)));
        mySorter.process(2, myTester);

        // Now catch up the original packet that "triggered" recovery
        //
        mySorter.process(3, myTester);

        Assert.assertEquals(5, myTester._consumed);
        Assert.assertEquals(true, myTester._recoveryRequested);
        Assert.assertEquals(0, mySorter.numPackets());
    }

    class Tester implements PacketSorter.PacketProcessor {
        int _consumed = 0;
        boolean _recoveryRequested = false;

        private boolean _recoveryReturn;

        Tester(boolean aRecoveryReturn) {
            _recoveryReturn = aRecoveryReturn;
        }

        public void consume(Transport.Packet aPacket) {
            _consumed++;
        }

        public boolean recover(Need aNeed, InetSocketAddress aTriggeringSource) {
            _recoveryRequested = true;
            return _recoveryReturn;
        }
    }
}
