package com.auracyber.memorygraph.neo4j;

import com.auracyber.memorygraph.domain.Incident;
import com.auracyber.memorygraph.domain.Ioc;
import com.auracyber.memorygraph.domain.IocType;
import com.auracyber.memorygraph.domain.MitigationRule;
import com.auracyber.memorygraph.domain.SignatureDecay;
import com.auracyber.memorygraph.domain.Ttp;
import com.auracyber.memorygraph.domain.UnknownTechniqueException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class Neo4jImmuneMemoryGraphTest {

	private static final double TOLERANCE = 1e-9;
	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	private static final Ttp BRUTE_FORCE = new Ttp("T1110", "Brute Force", "credential-access");
	private static final Ttp VALID_ACCOUNTS = new Ttp("T1078", "Valid Accounts", "initial-access");
	private static final Ttp EXFILTRATION = new Ttp("T1041", "Exfiltration Over C2 Channel", "exfiltration");
	private static final Ioc ATTACKER_ADDRESS = new Ioc(IocType.IP_ADDRESS, "203.0.113.7");
	private static final Ioc DROPPER_HASH = new Ioc(IocType.FILE_HASH, "44d88612fea8a8f36de82e1278abb02f");

	private static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26.0").withoutAuthentication();

	@Autowired
	private TtpNodeRepository ttps;
	@Autowired
	private IocNodeRepository iocs;
	@Autowired
	private IncidentNodeRepository incidents;
	@Autowired
	private MitigationRuleNodeRepository rules;
	@Autowired
	private Neo4jClient neo4jClient;

	private final AdjustableClock clock = new AdjustableClock(START);
	private Neo4jImmuneMemoryGraph graph;

	@BeforeAll
	static void startNeo4j() {
		NEO4J.start();
	}

	@AfterAll
	static void stopNeo4j() {
		NEO4J.stop();
	}

	@DynamicPropertySource
	static void neo4jProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
	}

	@BeforeEach
	void setUp() {
		neo4jClient.query("MATCH (n) DETACH DELETE n").run();
		graph = new Neo4jImmuneMemoryGraph(
				ttps, iocs, incidents, rules, new SignatureDecay(Duration.ofHours(1), 0.25), clock
		);
	}

	@Test
	void relatedTtpsIncludesTechniquesTheIocDirectlyIndicates() {
		graph.linkIocToTtp(ATTACKER_ADDRESS, BRUTE_FORCE);
		graph.linkIocToTtp(ATTACKER_ADDRESS, VALID_ACCOUNTS);

		assertEquals(Set.of(BRUTE_FORCE, VALID_ACCOUNTS), graph.relatedTtps(ATTACKER_ADDRESS));
	}

	@Test
	void relatedTtpsIncludesTechniquesExhibitedInIncidentsWhereTheIocWasObserved() {
		graph.recordIncident(new Incident("inc-1", START, Set.of(ATTACKER_ADDRESS), Set.of(EXFILTRATION)));

		assertEquals(Set.of(EXFILTRATION), graph.relatedTtps(ATTACKER_ADDRESS));
	}

	@Test
	void relatedTtpsCombinesDirectAndIncidentCorrelationsWithoutDuplicates() {
		graph.linkIocToTtp(ATTACKER_ADDRESS, BRUTE_FORCE);
		graph.recordIncident(new Incident(
				"inc-1", START, Set.of(ATTACKER_ADDRESS, DROPPER_HASH), Set.of(BRUTE_FORCE, EXFILTRATION)
		));

		assertEquals(Set.of(BRUTE_FORCE, EXFILTRATION), graph.relatedTtps(ATTACKER_ADDRESS));
		assertEquals(Set.of(BRUTE_FORCE, EXFILTRATION), graph.relatedTtps(DROPPER_HASH));
	}

	@Test
	void relatedTtpsExcludesTechniquesOfUnrelatedIocs() {
		graph.linkIocToTtp(ATTACKER_ADDRESS, BRUTE_FORCE);
		graph.linkIocToTtp(DROPPER_HASH, EXFILTRATION);

		assertEquals(Set.of(BRUTE_FORCE), graph.relatedTtps(ATTACKER_ADDRESS));
	}

	@Test
	void relatedTtpsIsEmptyForUnknownIoc() {
		graph.recordTtp(BRUTE_FORCE);

		assertTrue(graph.relatedTtps(ATTACKER_ADDRESS).isEmpty());
	}

	@Test
	void recordingAnIncidentKeepsTheExistingIndicatorLinksOfItsIocs() {
		graph.linkIocToTtp(ATTACKER_ADDRESS, BRUTE_FORCE);

		graph.recordIncident(new Incident("inc-1", START, Set.of(ATTACKER_ADDRESS), Set.of()));

		assertEquals(Set.of(BRUTE_FORCE), graph.relatedTtps(ATTACKER_ADDRESS));
	}

	@Test
	void recordingAKnownIocAgainKeepsItsIndicatorLinks() {
		graph.linkIocToTtp(ATTACKER_ADDRESS, BRUTE_FORCE);

		graph.recordIoc(ATTACKER_ADDRESS);

		assertEquals(Set.of(BRUTE_FORCE), graph.relatedTtps(ATTACKER_ADDRESS));
		assertEquals(1, iocs.count());
	}

	@Test
	void recordingTheSameTtpTwiceKeepsASingleNode() {
		graph.recordTtp(BRUTE_FORCE);
		graph.recordTtp(new Ttp("T1110", "Brute Force (renamed)", "credential-access"));

		assertEquals(1, ttps.count());
		assertEquals("Brute Force (renamed)", ttps.findById("T1110").orElseThrow().getName());
	}

	@Test
	void mitigationRuleRoundTripsThroughTheGraph() {
		graph.recordTtp(BRUTE_FORCE);
		MitigationRule rule = new MitigationRule("rule-1", "T1110", "block 203.0.113.7", 0.8, START, false);

		graph.recordMitigationRule(rule);

		assertEquals(List.of(rule), graph.activeMitigationRules());
	}

	@Test
	void recordingARuleAgainstAnUnrecordedTechniqueIsRejected() {
		MitigationRule rule = new MitigationRule("rule-1", "T9999", "block 203.0.113.7", 0.8, START, false);

		assertThrows(UnknownTechniqueException.class, () -> graph.recordMitigationRule(rule));
		assertEquals(0, rules.count());
	}

	@Test
	void unconfirmedRuleLosesStrengthOverTimeAndDropsOutOnceBelowThreshold() {
		graph.recordTtp(BRUTE_FORCE);
		graph.recordMitigationRule(new MitigationRule("rule-1", "T1110", "block 203.0.113.7", 0.8, START, false));

		clock.advance(Duration.ofHours(1));
		List<MitigationRule> afterOneHalfLife = graph.activeMitigationRules();
		assertEquals(1, afterOneHalfLife.size());
		assertEquals(0.4, afterOneHalfLife.getFirst().confidence(), TOLERANCE);
		assertEquals(START.plus(Duration.ofHours(1)), afterOneHalfLife.getFirst().assessedAt());

		clock.advance(Duration.ofHours(1));
		assertTrue(graph.activeMitigationRules().isEmpty());
	}

	@Test
	void confirmedRuleKeepsItsStrength() {
		graph.recordTtp(BRUTE_FORCE);
		MitigationRule confirmed = new MitigationRule("rule-1", "T1110", "block 203.0.113.7", 0.8, START, true);
		graph.recordMitigationRule(confirmed);

		clock.advance(Duration.ofDays(30));

		assertEquals(List.of(confirmed), graph.activeMitigationRules());
	}

	@Test
	void confirmingARuleFreezesItsConfidenceAtTheMomentOfConfirmation() {
		graph.recordTtp(BRUTE_FORCE);
		graph.recordMitigationRule(new MitigationRule("rule-1", "T1110", "block 203.0.113.7", 0.8, START, false));
		clock.advance(Duration.ofHours(1));

		graph.confirmMitigationRule("rule-1");
		clock.advance(Duration.ofDays(30));

		List<MitigationRule> active = graph.activeMitigationRules();
		assertEquals(1, active.size());
		assertEquals(0.4, active.getFirst().confidence(), TOLERANCE);
		assertTrue(active.getFirst().confirmed());
		assertEquals("T1110", active.getFirst().techniqueId());
	}

	@Test
	void confirmingAnUnknownRuleIsANoOp() {
		graph.confirmMitigationRule("missing");

		assertEquals(0, rules.count());
	}

	@Test
	void activeRulesAreListedStrongestFirst() {
		graph.recordTtp(BRUTE_FORCE);
		graph.recordTtp(EXFILTRATION);
		graph.recordMitigationRule(new MitigationRule("weak", "T1110", "rate-limit logins", 0.5, START, false));
		graph.recordMitigationRule(new MitigationRule("strong", "T1041", "sinkhole C2 domain", 0.9, START, false));

		List<String> ruleIds = graph.activeMitigationRules().stream().map(MitigationRule::ruleId).toList();

		assertEquals(List.of("strong", "weak"), ruleIds);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestApplication {
	}

	private static final class AdjustableClock extends Clock {

		private Instant now;

		private AdjustableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
