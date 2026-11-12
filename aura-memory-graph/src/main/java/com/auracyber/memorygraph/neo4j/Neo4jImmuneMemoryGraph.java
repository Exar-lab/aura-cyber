package com.auracyber.memorygraph.neo4j;

import com.auracyber.memorygraph.domain.ImmuneMemoryGraph;
import com.auracyber.memorygraph.domain.Incident;
import com.auracyber.memorygraph.domain.Ioc;
import com.auracyber.memorygraph.domain.MitigationRule;
import com.auracyber.memorygraph.domain.SignatureDecay;
import com.auracyber.memorygraph.domain.Ttp;
import com.auracyber.memorygraph.domain.UnknownTechniqueException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immune Memory Graph backed by Neo4j through Spring Data Neo4j. Mitigation
 * rules are stored with the confidence they had when last assessed and are
 * decayed on read, so no background sweep is needed to keep them current.
 */
@Transactional
public class Neo4jImmuneMemoryGraph implements ImmuneMemoryGraph {

	private final TtpNodeRepository ttps;
	private final IocNodeRepository iocs;
	private final IncidentNodeRepository incidents;
	private final MitigationRuleNodeRepository rules;
	private final SignatureDecay signatureDecay;
	private final Clock clock;

	public Neo4jImmuneMemoryGraph(
			TtpNodeRepository ttps,
			IocNodeRepository iocs,
			IncidentNodeRepository incidents,
			MitigationRuleNodeRepository rules,
			SignatureDecay signatureDecay,
			Clock clock
	) {
		this.ttps = ttps;
		this.iocs = iocs;
		this.incidents = incidents;
		this.rules = rules;
		this.signatureDecay = signatureDecay;
		this.clock = clock;
	}

	@Override
	public void recordTtp(Ttp ttp) {
		ttps.save(TtpNode.from(ttp));
	}

	@Override
	public void recordIoc(Ioc ioc) {
		if (!iocs.existsById(IocNode.keyOf(ioc))) {
			iocs.save(IocNode.from(ioc));
		}
	}

	@Override
	public void linkIocToTtp(Ioc ioc, Ttp ttp) {
		IocNode node = loadOrCreate(ioc);
		node.indicate(TtpNode.from(ttp));
		iocs.save(node);
	}

	@Override
	public void recordIncident(Incident incident) {
		IncidentNode node = new IncidentNode(incident.incidentId(), incident.observedAt());
		incident.iocs().forEach(ioc -> node.observe(loadOrCreate(ioc)));
		incident.ttps().forEach(ttp -> node.exhibit(TtpNode.from(ttp)));
		incidents.save(node);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Ttp> relatedTtps(Ioc ioc) {
		return ttps.findCorrelatedWithIoc(IocNode.keyOf(ioc)).stream()
				.map(TtpNode::toDomain)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public void recordMitigationRule(MitigationRule rule) {
		TtpNode ttp = ttps.findById(rule.techniqueId())
				.orElseThrow(() -> new UnknownTechniqueException(rule.techniqueId()));
		rules.save(MitigationRuleNode.from(rule, ttp));
	}

	@Override
	public void confirmMitigationRule(String ruleId) {
		Instant now = clock.instant();
		rules.findById(ruleId).ifPresent(node -> {
			MitigationRule assessed = signatureDecay.assess(node.toDomain(), now);
			MitigationRule confirmed = new MitigationRule(
					assessed.ruleId(),
					assessed.techniqueId(),
					assessed.countermeasure(),
					assessed.confidence(),
					assessed.assessedAt(),
					true
			);
			rules.save(MitigationRuleNode.from(confirmed, node.getTtp()));
		});
	}

	@Override
	@Transactional(readOnly = true)
	public List<MitigationRule> activeMitigationRules() {
		Instant now = clock.instant();
		return rules.findAll().stream()
				.map(MitigationRuleNode::toDomain)
				.map(rule -> signatureDecay.assess(rule, now))
				.filter(rule -> rule.confidence() >= signatureDecay.activeThreshold())
				.sorted(Comparator.comparingDouble(MitigationRule::confidence).reversed())
				.toList();
	}

	/**
	 * Saving a node rewrites its outgoing relationships from the object's
	 * state, so an already-known IoC must be loaded first or its existing
	 * INDICATES links would be dropped.
	 */
	private IocNode loadOrCreate(Ioc ioc) {
		return iocs.findById(IocNode.keyOf(ioc)).orElseGet(() -> IocNode.from(ioc));
	}
}
