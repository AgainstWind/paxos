package org.dancres.paxos;

/**
 * Base interface for failure detector implementations.  For more on failure detectors read: Unreliable Failure Detectors for
 * Reliable Distributed Systems by Tushar Deepak Chandra and Sam Toueg.
 *
 * @author dan
 */
public interface FailureDetector {
    public void add(LivenessListener aListener);
    public NodeId getLeader();
    public long getUnresponsivenessThreshold();
    public boolean amLeader(NodeId aNodeId);
    public Membership getMembers(MembershipListener aListener);
    public boolean isLive(NodeId aNodeId);
}
