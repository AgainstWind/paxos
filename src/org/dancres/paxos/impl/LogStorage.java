package org.dancres.paxos.impl;

import org.dancres.paxos.Proposal;

/**
 * Standard abstraction for the log required to maintain essential paxos state to ensure appropriate recovery.
 *
 * @author dan
 */
public interface LogStorage {
    public static final Proposal NO_VALUE =
    	new Proposal("org.dancres.paxos.NoValue", new byte[0]);

    public byte[] get(long mark) throws Exception;
    public long put(byte[] data, boolean sync) throws Exception;
    public void mark(long key, boolean force) throws Exception;
    public void close() throws Exception;
    public void open() throws Exception;
    public void replay(RecordListener listener, long mark) throws Exception;    
}
