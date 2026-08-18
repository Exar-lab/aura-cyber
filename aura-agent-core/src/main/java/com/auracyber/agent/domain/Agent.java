package com.auracyber.agent.domain;

/**
 * Contract shared by every swarm role (Sentinel, Synthesizer, Immunizer,
 * Red Team Adversary): observe telemetry, decide on a response, act on it.
 */
public interface Agent<P extends Perception, D extends Decision> {

	AgentId id();

	P observe();

	D decide(P perception);

	ActionOutcome act(D decision);
}
