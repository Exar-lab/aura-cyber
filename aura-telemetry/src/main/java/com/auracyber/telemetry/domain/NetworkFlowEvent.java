package com.auracyber.telemetry.domain;

import com.auracyber.agent.domain.Perception;

import java.time.Instant;

/**
 * A single L2-L4 network flow observation captured off the wire, consumed
 * by the Sentinel agent as an {@link Perception}.
 */
public record NetworkFlowEvent(
		Instant capturedAt,
		String sourceAddress,
		int sourcePort,
		String destinationAddress,
		int destinationPort,
		TransportProtocol protocol,
		int frameLength
) implements Perception {
}
