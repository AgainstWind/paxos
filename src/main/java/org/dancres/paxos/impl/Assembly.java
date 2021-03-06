package org.dancres.paxos.impl;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;

/**
 * Represents a membership snapshot from a particular point in time.
 * This majority should be used until a Paxos round is completed or restarted.
 */
public interface Assembly {
    /**
     * @return the current size of the membership
     */
    public int getSize();

    public boolean couldComplete();

    public boolean isMajority(Collection<InetSocketAddress> aListOfAddresses);

    public Map<InetSocketAddress, FailureDetector.MetaData> getMembers();

    public byte[] dataForNode(InetSocketAddress anAddress);
}
