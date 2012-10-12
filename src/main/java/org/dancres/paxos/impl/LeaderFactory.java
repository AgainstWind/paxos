package org.dancres.paxos.impl;

import org.dancres.paxos.Paxos;
import org.dancres.paxos.Proposal;
import org.dancres.paxos.VoteOutcome;
import org.dancres.paxos.messages.PaxosMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * <p>Each paxos instance is driven and represented by an individual instance of <code>Leader</code>.
 * These are created, tracked and driven by this factory class. The factory also looks after handling error outcomes
 * that require an adjustment in round or sequence number and heartbeating.</p>
 *
 * @see Leader
 */
public class LeaderFactory {
    private static final Logger _logger = LoggerFactory.getLogger(LeaderFactory.class);

    public static final Proposal HEARTBEAT = new Proposal("heartbeat",
            "org.dancres.paxos.Heartbeat".getBytes());
    
    private final NavigableMap<Long, Leader> _leaders = new TreeMap<Long, Leader>();
    private final Common _common;

    /**
     * This alarm is used to ensure the leader sends regular heartbeats in the face of inactivity so as to extend
     * its lease with AcceptorLearners.
     */
    private TimerTask _heartbeatAlarm;

    LeaderFactory(Common aCommon) {
        _common = aCommon;

        _common.add(new Paxos.Listener() {
            public void done(VoteOutcome anEvent) {
                if (anEvent.getResult() == VoteOutcome.Reason.OUT_OF_DATE) {
                    synchronized (this) {
                        killHeartbeats();
                    }
                }
            }
        });
    }

    /**
     * @throws org.dancres.paxos.Paxos.InactiveException if the Paxos process is currently out of date or shutting down
     *
     * @return a leader for a new sequence
     */
    Leader newLeader() throws Paxos.InactiveException {
        synchronized (this) {
            while (isActive()) {
                try { 
                    wait();
                } catch (InterruptedException anIE) {}
            }

            killHeartbeats();

            if ((_common.testState(Common.FSMStates.SHUTDOWN)) || (_common.testState(Common.FSMStates.OUT_OF_DATE)))
                throw new Paxos.InactiveException();

            return newLeaderImpl();
        }
    }

    private void killHeartbeats() {
        if (_heartbeatAlarm != null) {
            _heartbeatAlarm.cancel();
            _heartbeatAlarm = null;
            _common.getWatchdog().purge();
        }
    }

    private boolean isActive() {
        return ((_leaders.size() > 0) && (!_leaders.lastEntry().getValue().isDone()));
    }

    private Leader newLeaderImpl() {
        long mySeqNum = _common.getRecoveryTrigger().getLowWatermark().getSeqNum() + 1;
        long myRndNum = _common.getLeaderRndNum() + 1;
        Leader.States myState = Leader.States.COLLECT;

        if (_leaders.size() > 0) {
            Leader myLast = _leaders.lastEntry().getValue();
            VoteOutcome myOutcome = myLast.getOutcome();

            /*
            * Seq & round allocation:
            *
            * If there are active leaders, pick last (i.e. biggest seqNum) and use its seqNum + 1 and its
            * rndNumber
            * elseif last outcome was positive, compare with AL seqNum. If AL wins, use its rndNum + 1 and
            * its seqNum + 1 else use outcome's rndNum and seqNum + 1 (and apply multi-paxos).
            * elseif last outcome was negative and state was other leader proceed with this outcome as per the other
            * outcome case above (but with the proviso that if AL doesn't win, we use outcome's rndNum + 1)
            * else use AL round number + 1 and seqNum + 1
            *
            * When we're considering past leaders outcomes, we only consider those for which we have some meaningful
            * information. Thus vote timeouts, membership problems etc provide no useful feedback re: accuracy of
            * sequence numbers or rounds whilst decisions, other values and other leaders (which includes feedback
            * about round and sequence) do.
            *
            * AL knowledge always takes priority if it's more recent than what our last informative leader action
            * discloses. If our AL has more recent knowledge, it implies leader activity in another process.
            */
            if (myOutcome != null) {
                /*
                 * Last leader has resolved - account for mySeqNum being our ideal next sequence number not current
                 * (hence myOutcome.getSeqNum() + 1
                 */
                switch(myOutcome.getResult()) {
                    case VoteOutcome.Reason.DECISION :
                    case VoteOutcome.Reason.OTHER_VALUE : {

                        if (mySeqNum <= (myOutcome.getSeqNum() + 1)) {
                            // Apply multi-paxos
                            //
                            myState = Leader.States.BEGIN;
                            mySeqNum = myOutcome.getSeqNum() + 1;
                            myRndNum = myOutcome.getRndNumber();
                        }

                        break;
                    }

                    case VoteOutcome.Reason.OTHER_LEADER : {
                        if (mySeqNum <= (myOutcome.getSeqNum() + 1)) {
                            mySeqNum = myOutcome.getSeqNum() + 1;
                            myRndNum = myOutcome.getRndNumber() + 1;
                        }

                        break;
                    }
                }
            } else {
                // Last leader is still active
                //
                mySeqNum = myLast.getSeqNum() + 1;
                myRndNum = myLast.getRound();
            }
        }

        Leader myLeader = new Leader(_common, this, mySeqNum, myRndNum, myState);

        _leaders.put(new Long(mySeqNum), myLeader);

        return myLeader;
    }

    /**
     *
     * @todo Increment round number via heartbeats every so often to avoid jittering collects
     *
     * @param aLeader
     */
    void dispose(Leader aLeader) {
        // If there are no leaders and the last one exited cleanly, do heartbeats
        //
        synchronized (this) {
            if (_leaders.size() > 1) {
                Long myLast = _leaders.lastKey();
                Iterator<Long> allKeys = _leaders.keySet().iterator();
                
                while (allKeys.hasNext()) {
                    Long k = allKeys.next();
                    if ((! k.equals(myLast)) && (_leaders.get(k).getOutcome() != null))
                        allKeys.remove();
                }                
            }

            if ((_leaders.size() == 1) && (_leaders.lastEntry().getValue().isDone())) {
                switch (_leaders.lastEntry().getValue().getOutcome().getResult()) {
                    case VoteOutcome.Reason.OTHER_VALUE :
                    case VoteOutcome.Reason.DECISION : {
                        // Still leader so heartbeat
                        //
                        _heartbeatAlarm = new TimerTask() {
                            public void run() {
                                _logger.info(this + ": sending heartbeat: " + System.currentTimeMillis());

                                newLeaderImpl().submit(HEARTBEAT);
                            }
                        };

                        _common.getWatchdog().schedule(_heartbeatAlarm, calculateLeaderRefresh());
                    }

                    default : {
                        // Not leader, nothing to do
                        //
                        break;
                    }
                }
                
                notify();
            }
        }
    }

    private long calculateLeaderRefresh() {
        long myExpiry = Constants.getLeaderLeaseDuration();
        return myExpiry - (myExpiry * 10 / 100);
    }

    public void shutdown() {
        synchronized (this) {
            killHeartbeats();

            Iterator<Map.Entry<Long, Leader>> all = _leaders.entrySet().iterator();

            while (all.hasNext()) {
                Map.Entry<Long, Leader> myCurrent = all.next();
                myCurrent.getValue().shutdown();
                all.remove();
            }
        }
    }

    void messageReceived(Transport.Packet aPacket) {
        _logger.debug("Got packet for leaders: " + aPacket.getSource() + "->" + aPacket.getMessage());
        
        synchronized(this) {
            _logger.debug("Routing packet to " + _leaders.size() + " leaders");

            for (Leader aLeader : new LinkedList<Leader>(_leaders.values())) {
                aLeader.messageReceived(aPacket);
            }
        }
    }
}
