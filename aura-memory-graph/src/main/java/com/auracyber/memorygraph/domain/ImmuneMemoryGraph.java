package com.auracyber.memorygraph.domain;

import java.util.List;
import java.util.Set;

/**
 * Port for the Immune Memory Graph (IMG): the swarm's long-term memory of
 * TTPs, IoCs, the incidents relating them, and the mitigation rules raised
 * against them. Queried by the Correlation Synthesizer; implemented by a
 * graph-database adapter.
 */
public interface ImmuneMemoryGraph {

	void recordTtp(Ttp ttp);

	void recordIoc(Ioc ioc);

	/** Records that the IoC is a known indicator of the TTP, recording either if unknown. */
	void linkIocToTtp(Ioc ioc, Ttp ttp);

	/** Records the incident together with every IoC and TTP it references. */
	void recordIncident(Incident incident);

	/**
	 * TTPs correlated with the IoC: those it directly indicates plus those
	 * exhibited in any incident where it was observed.
	 */
	Set<Ttp> relatedTtps(Ioc ioc);

	/**
	 * Records or replaces the rule.
	 *
	 * @throws UnknownTechniqueException when the rule targets a TTP that was never recorded.
	 */
	void recordMitigationRule(MitigationRule rule);

	/** Marks the rule as confirmed so it stops decaying. No-op for an unknown rule. */
	void confirmMitigationRule(String ruleId);

	/**
	 * Rules still above the signature-decay threshold, each carrying its
	 * confidence as of the time of the call, strongest first.
	 */
	List<MitigationRule> activeMitigationRules();
}
