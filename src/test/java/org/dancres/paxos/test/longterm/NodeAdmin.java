package org.dancres.paxos.test.longterm;

import org.dancres.paxos.CheckpointStorage;
import org.dancres.paxos.impl.Transport;

public interface NodeAdmin {
    Transport getTransport();
    void checkpoint() throws Exception;
    CheckpointStorage.ReadCheckpoint getLastCheckpoint();
    long lastCheckpointTime();
    boolean isOutOfDate();
    void terminate();
    boolean bringUpToDate(CheckpointStorage.ReadCheckpoint aCkpt);
    void settle();
    long getDropCount();
    long getTxCount();
    long getRxCount();
    long getLastSeq();
}
