package org.dancres.paxos.impl.faildet;

import org.dancres.paxos.Transport;
import org.dancres.paxos.NodeId;

/**
 * Broadcasts <code>Heartbeat</code> messages at an appropriate rate for <code>FailureDetectorImpl</code>'s in other nodes.
 *
 * @author dan
 */
public class Heartbeater implements Runnable {
    private Transport _transport;
    private NodeId _nodeId;
    
    public Heartbeater(NodeId aNodeId, Transport aTransport) {
        _transport = aTransport;
        _nodeId = aNodeId;
    }

    public void run() {
        while (true) {
            _transport.send(new Heartbeat(_nodeId.asLong()), NodeId.BROADCAST);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
}
