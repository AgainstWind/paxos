package org.dancres.paxos;

/**
 * Standard abstraction for the log required to maintain essential paxos state to ensure appropriate recovery.
 *
 * @author dan
 */
public interface LogStorage {
    public static final long EMPTY_LOG = Long.MIN_VALUE;
    public static final byte[] NO_VALUE = new byte[0];

    public byte[] get(long mark) throws Exception;
    public long put(byte[] data, boolean sync) throws Exception;
    public void mark(long key, boolean force) throws Exception;
    public void close() throws Exception;
    public void open() throws Exception;
    public void replay(RecordListener listener, long mark) throws Exception;    
}
