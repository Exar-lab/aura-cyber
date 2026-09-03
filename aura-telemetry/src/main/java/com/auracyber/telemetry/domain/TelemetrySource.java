package com.auracyber.telemetry.domain;

import com.auracyber.agent.domain.Perception;

import java.util.function.Consumer;

/**
 * Port for a telemetry feed consumable by the Sentinel agent. Implementations
 * capture from a real source (network flows, kernel events, ...) and push
 * each observation to {@code onEvent} on a background thread until
 * {@link #stop()} is called.
 */
public interface TelemetrySource {

	/**
	 * @throws TelemetryCaptureException when the underlying capture cannot be opened.
	 */
	void start(Consumer<Perception> onEvent) throws TelemetryCaptureException;

	void stop();
}
