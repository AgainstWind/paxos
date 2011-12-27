package org.dancres.paxos;

import java.net.InetSocketAddress;

import org.dancres.paxos.impl.Tuple;
import org.dancres.paxos.messages.Operations;
import org.dancres.paxos.messages.PaxosMessage;

/**
 * Status indication returned from the state machine for each vote requested
 */
public class VoteOutcome implements PaxosMessage {
	public static final class Reason {
		public static final int DECISION = 0;
		public static final int OTHER_LEADER = 1;
		public static final int VOTE_TIMEOUT = 2;
		public static final int BAD_MEMBERSHIP = 3;
        public static final int OUT_OF_DATE = 4;
        public static final int UP_TO_DATE = 5;
        public static final int OTHER_VALUE = 6;
        
        private static final String[] _names = {"Decision", "Other Leader", "Vote Timeout", "Bad Membership",
        	"Out of Date", "Up to Date", "Other Value"};
        
        public static String nameFor(int aCode) {
        	if (aCode < 0 || aCode > _names.length - 1)
        		throw new IllegalArgumentException("Code not known:" + aCode);
        	
        	return _names[aCode];
        }
	}
	
    private int _result;
    private long _seqNum;
    private long _rndNumber;
    private Proposal _consolidatedValue;
    private InetSocketAddress _leader;

    public VoteOutcome(int aResult, long aSeqNum, long aRndNumber, Proposal aValue,
                       InetSocketAddress aLeader) {
    	assert(aValue != null);
    	
        _result = aResult;
        _seqNum = aSeqNum;
        _rndNumber = aRndNumber;
        _consolidatedValue = aValue;
        _leader = aLeader;
    }

    public Proposal getValues() {
    	return _consolidatedValue;
    }
    
    public Tuple<Long, Long> getLeaderBootstrap() {
        switch (_result) {
            case Reason.OTHER_LEADER :
            case Reason.DECISION :
            case Reason.OTHER_VALUE : return new Tuple<Long, Long>(_seqNum, _rndNumber);
            
            default : return null;
        }
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

    public long getRndNumber() {
        return _rndNumber;
    }

    /**
     * @return additional information associated with the reason returned from <code>getResult()</code>.
     */
    public InetSocketAddress getLeader() {
        return _leader;
    }

    public String toString() {
        return "Event: " + Reason.nameFor(_result) + ", " + Long.toHexString(_seqNum) + ", " + _leader;
    }

	public int getType() {
		return Operations.EVENT;
	}

	public short getClassification() {
		return CLIENT;
	}

	public InetSocketAddress getNodeId() {
		return _leader;
	}
}
