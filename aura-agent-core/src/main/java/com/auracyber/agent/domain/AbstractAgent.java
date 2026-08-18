package com.auracyber.agent.domain;

/**
 * Runs the observe -> decide -> act lifecycle through the CCE credit gate:
 * refuses to run a bankrupt agent, charges the decision's cost before acting,
 * and applies the false-positive penalty when the action harms legitimate traffic.
 */
public abstract class AbstractAgent<P extends Perception, D extends Decision> implements Agent<P, D> {

	private final AgentId id;
	private final CreditLedger creditLedger;

	protected AbstractAgent(AgentId id, CreditLedger creditLedger) {
		this.id = id;
		this.creditLedger = creditLedger;
	}

	@Override
	public final AgentId id() {
		return id;
	}

	public final ActionOutcome run() {
		if (creditLedger.isBankrupt(id)) {
			throw new AgentExtinguishedException(id);
		}

		P perception = observe();
		D decision = decide(perception);
		creditLedger.charge(id, decision.cost());

		ActionOutcome outcome = act(decision);
		if (outcome instanceof ActionOutcome.Failure failure && failure.falsePositive()) {
			creditLedger.penalize(id, PenaltyReason.FALSE_POSITIVE);
		}
		return outcome;
	}
}
