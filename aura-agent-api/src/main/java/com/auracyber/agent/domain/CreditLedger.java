package com.auracyber.agent.domain;

/**
 * Port for the Compute Credit Economy (CCE). Implemented by aura-cce-engine
 * (Hazelcast-backed) as an infrastructure adapter.
 */
public interface CreditLedger {

	/** Onboards an agent with its starting balance. No-op if the agent is already open. */
	void open(AgentId agentId, long initialBalance);

	long balanceOf(AgentId agentId);

	/**
	 * @throws InsufficientCreditsException when the agent's balance cannot cover the cost.
	 */
	void charge(AgentId agentId, CreditCost cost);

	/** Deducts the CCE penalty (40% of balance) for the given reason. */
	void penalize(AgentId agentId, PenaltyReason reason);

	boolean isBankrupt(AgentId agentId);
}
