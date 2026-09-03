package com.auracyber.telemetry.domain;

import com.auracyber.agent.domain.Perception;

import java.time.Instant;

/**
 * A single kernel-level event captured via eBPF, consumed by the Sentinel
 * agent as a {@link Perception}.
 */
public record KernelEvent(
		Instant capturedAt,
		KernelEventType type,
		int pid,
		String comm
) implements Perception {
}
