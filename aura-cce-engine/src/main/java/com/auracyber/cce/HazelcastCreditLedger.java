package com.auracyber.cce;

import com.auracyber.agent.domain.AgentId;
import com.auracyber.agent.domain.CreditCost;
import com.auracyber.agent.domain.CreditLedger;
import com.auracyber.agent.domain.InsufficientCreditsException;
import com.auracyber.agent.domain.PenaltyReason;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * CCE credit ledger backed by a distributed Hazelcast map, so every agent
 * instance across the cluster reads and mutates the same balance.
 * Balance mutations are guarded by a per-agent distributed lock to keep
 * read-modify-write sequences atomic under concurrent agent activity.
 */
public class HazelcastCreditLedger implements CreditLedger {

	private static final String MAP_NAME = "aura-cce-balances";
	private static final double FALSE_POSITIVE_PENALTY_RATE = 0.40;

	private final IMap<String, Long> balances;

	public HazelcastCreditLedger(HazelcastInstance hazelcastInstance) {
		this.balances = hazelcastInstance.getMap(MAP_NAME);
	}

	@Override
	public void open(AgentId agentId, long initialBalance) {
		balances.putIfAbsent(key(agentId), initialBalance);
	}

	@Override
	public long balanceOf(AgentId agentId) {
		Long balance = balances.get(key(agentId));
		return balance == null ? 0L : balance;
	}

	@Override
	public void charge(AgentId agentId, CreditCost cost) {
		String key = key(agentId);
		balances.lock(key);
		try {
			long balance = balanceOf(agentId);
			if (balance < cost.tokens()) {
				throw new InsufficientCreditsException(agentId, cost, balance);
			}
			balances.put(key, balance - cost.tokens());
		} finally {
			balances.unlock(key);
		}
	}

	@Override
	public void penalize(AgentId agentId, PenaltyReason reason) {
		String key = key(agentId);
		balances.lock(key);
		try {
			long balance = balanceOf(agentId);
			long penalty = Math.round(balance * FALSE_POSITIVE_PENALTY_RATE);
			balances.put(key, balance - penalty);
		} finally {
			balances.unlock(key);
		}
	}

	@Override
	public boolean isBankrupt(AgentId agentId) {
		return balanceOf(agentId) <= 0;
	}

	private static String key(AgentId agentId) {
		return agentId.value();
	}
}
