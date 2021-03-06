package org.dancres.paxos.impl;

import java.util.Timer;

class Common {
    private Transport _transport;
    private final Timer _watchdog = new Timer("Paxos timers");
    private final NodeState _nodeState = new NodeState();

    Common(Transport aTransport) {
        _transport = aTransport;
    }

    Common() {
        this(null);
    }
    
    void setTransport(Transport aTransport) {
    	_transport = aTransport;
    }
    
    Timer getWatchdog() {
        return _watchdog;
    }

    Transport getTransport() {
        return _transport;
    }

    void stop() {
        _watchdog.cancel();
    }
    
    boolean amMember() {
        return _transport.getFD().isMember(_transport.getLocalAddress());
    }

    NodeState getNodeState() {
        return _nodeState;
    }
}
