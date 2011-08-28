package org.dancres.paxos.test.junit;

import org.dancres.paxos.impl.AcceptorLearner;
import org.dancres.paxos.impl.CheckpointHandle;
import org.dancres.paxos.impl.Stream;
import org.dancres.paxos.impl.Transport;
import org.dancres.paxos.impl.HowlLogger;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.test.utils.FileSystem;
import org.dancres.paxos.test.utils.NullFailureDetector;
import org.dancres.paxos.test.utils.Utils;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ALCheckpointTest {
    private static final String DIRECTORY = "howllogs";

    private InetSocketAddress _nodeId = Utils.getTestAddress();
    private InetSocketAddress _broadcastId = Utils.getTestAddress();

    private class TransportImpl implements Transport {
        public void add(Dispatcher aDispatcher) {
        }

        private List<PaxosMessage> _messages = new ArrayList<PaxosMessage>();

        public void send(PaxosMessage aMessage, InetSocketAddress aNodeId) {
            synchronized(_messages) {
                _messages.add(aMessage);
            }
        }

        PaxosMessage getNextMsg() {
            synchronized(_messages) {
                return _messages.remove(0);
            }
        }

        public InetSocketAddress getLocalAddress() {
            return _nodeId;
        }

        public InetSocketAddress getBroadcastAddress() {
            return _broadcastId;
        }

        public void shutdown() {
        }

        public Stream connectTo(InetSocketAddress aNodeId) {
            return null;
        }
    }

    @Before
    public void init() throws Exception {
        FileSystem.deleteDirectory(new File(DIRECTORY));
    }

    @Test
    public void test() throws Exception {
        HowlLogger myLogger = new HowlLogger(DIRECTORY);
        TransportImpl myTransport = new TransportImpl();

        AcceptorLearner myAl = new AcceptorLearner(myLogger, new NullFailureDetector(), myTransport, 0);

        myAl.open(CheckpointHandle.NO_CHECKPOINT);
        CheckpointHandle myHandle = myAl.newCheckpoint();
        myHandle.saved();
        myAl.close();

        myAl = new AcceptorLearner(myLogger, new NullFailureDetector(), myTransport, 0);

        myAl.open(myHandle);
        myAl.close();

        ByteArrayOutputStream myBAOS = new ByteArrayOutputStream();
        ObjectOutputStream myOOS = new ObjectOutputStream(myBAOS);

        myOOS.writeObject(myHandle);
        myOOS.close();

        ByteArrayInputStream myBAIS = new ByteArrayInputStream(myBAOS.toByteArray());
        ObjectInputStream myOIS = new ObjectInputStream(myBAIS);
        CheckpointHandle myRecoveredHandle = (CheckpointHandle) myOIS.readObject();

        myAl = new AcceptorLearner(myLogger, new NullFailureDetector(), myTransport, 0);

        myAl.open(myRecoveredHandle);
        myAl.close();
    }

    public static void main(String[] anArgs) throws Exception {
        ALStartupTest myTest = new ALStartupTest();
        myTest.init();
        myTest.test();
    }
}
