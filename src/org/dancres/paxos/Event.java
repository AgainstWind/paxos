package org.dancres.paxos;

/**
 * Status indication returned from the state machine for each vote requested
 */
public class Event {
	public interface Reason {
		public static final int DECISION = -1;
		public static final int OTHER_LEADER = -2;
		public static final int VOTE_TIMEOUT = -3;
		public static final int BAD_MEMBERSHIP = -4;
        public static final int OUT_OF_DATE = -5;
	}
	
    private int _result;
    private long _seqNum;
    private Proposal _consolidatedValue;
    private Object _context;

    public Event(int aResult, long aSeqNum, Proposal aValue) {
    	assert(aValue != null);
    	
        _result = aResult;
        _seqNum = aSeqNum;
        _consolidatedValue = aValue;
    }

    public Event(int aResult, long aSeqNum, Proposal aValue, Object aContext) {
        this(aResult, aSeqNum, aValue);
        _context = aContext;
    }

    public Proposal getValues() {
    	return _consolidatedValue;
    }
    
    /**
     * @return the completion code for a requested vote, one of {@link Reason}
     */
    public int getResult() {
        return _result;
    }

    /**
     * @return the sequence number associated with the vote, if any
     */
    public long getSeqNum() {
        return _seqNum;
    }

    /**
     * @return additional information associated with the reason returned from <code>getResult()</code>.
     */
    public Object getContext() {
        return _context;
    }

    public String toString() {
        return "Event: " + _result + ", " + Long.toHexString(_seqNum) + ", " + 
        	_consolidatedValue + ", " + _context;
    }
}
