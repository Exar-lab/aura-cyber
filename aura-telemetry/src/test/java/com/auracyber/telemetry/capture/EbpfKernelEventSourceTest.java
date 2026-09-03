package com.auracyber.telemetry.capture;

import com.auracyber.telemetry.domain.TelemetryCaptureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EbpfKernelEventSourceTest {

	@Test
	void startFailsFastWhenLibbpfIsUnavailable() {
		EbpfKernelEventSource source = new EbpfKernelEventSource();

		assertThrows(TelemetryCaptureException.class, () -> source.start(event -> { }));
	}
}
