package com.auracyber.agent.domain;

/**
 * What an agent chose to do after observing, and the compute credits
 * it costs to carry that action out (deep scan, rule injection, ...).
 */
public interface Decision {

	CreditCost cost();
}
