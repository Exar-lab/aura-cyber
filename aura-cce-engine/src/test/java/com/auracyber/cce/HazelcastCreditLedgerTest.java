package com.auracyber.cce;

import com.auracyber.agent.domain.AgentId;
import com.auracyber.agent.domain.CreditCost;
import com.auracyber.agent.domain.InsufficientCreditsException;
import com.auracyber.agent.domain.PenaltyReason;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastCreditLedgerTest {

	private HazelcastInstance hazelcastInstance;
	private HazelcastCreditLedger ledger;
	private final AgentId sentinel = new AgentId("sentinel-1");

	@BeforeEach
	void setUp() {
		Config config = new Config();
		config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
		config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
		hazelcastInstance = Hazelcast.newHazelcastInstance(config);
		ledger = new HazelcastCreditLedger(hazelcastInstance);
	}

	@AfterEach
	void tearDown() {
		hazelcastInstance.shutdown();
	}

	@Test
	void opensAgentWithInitialBalance() {
		ledger.open(sentinel, 100L);

		assertEquals(100L, ledger.balanceOf(sentinel));
	}

	@Test
	void openIsNoOpWhenAgentAlreadyOpen() {
		ledger.open(sentinel, 100L);
		ledger.open(sentinel, 500L);

		assertEquals(100L, ledger.balanceOf(sentinel));
	}

	@Test
	void chargeDeductsCostFromBalance() {
		ledger.open(sentinel, 100L);

		ledger.charge(sentinel, new CreditCost(30L));

		assertEquals(70L, ledger.balanceOf(sentinel));
	}

	@Test
	void chargeThrowsWhenBalanceCannotCoverCost() {
		ledger.open(sentinel, 10L);

		assertThrows(InsufficientCreditsException.class, () -> ledger.charge(sentinel, new CreditCost(20L)));
		assertEquals(10L, ledger.balanceOf(sentinel));
	}

	@Test
	void penalizeDeducts40PercentOfBalance() {
		ledger.open(sentinel, 100L);

		ledger.penalize(sentinel, PenaltyReason.FALSE_POSITIVE);

		assertEquals(60L, ledger.balanceOf(sentinel));
	}

	@Test
	void agentIsBankruptWhenBalanceReachesZero() {
		ledger.open(sentinel, 10L);

		ledger.charge(sentinel, new CreditCost(10L));

		assertTrue(ledger.isBankrupt(sentinel));
	}

	@Test
	void unopenedAgentIsBankrupt() {
		assertFalse(ledger.balanceOf(new AgentId("unknown")) > 0);
		assertTrue(ledger.isBankrupt(new AgentId("unknown")));
	}
}
