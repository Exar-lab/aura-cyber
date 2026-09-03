package com.auracyber.telemetry.capture;

import com.auracyber.agent.domain.Perception;
import com.auracyber.telemetry.domain.TelemetryCaptureException;
import com.auracyber.telemetry.domain.TelemetrySource;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.function.Consumer;

/**
 * Binds libbpf via the Java FFM API (Project Panama) to capture kernel
 * events for the Sentinel agent, without classic JNI overhead. Requires
 * libbpf on the host (Linux only) — {@link #start} fails fast with
 * {@link TelemetryCaptureException} everywhere else.
 *
 * <p>Attaching real kernel probes needs a compiled BPF object loaded via
 * {@code bpf_object__open_file}/{@code bpf_object__load} and a ring-buffer
 * poll loop; this adapter establishes the FFM link to libbpf and verifies
 * its version, which is the seam that probe-attachment work plugs into.
 */
public class EbpfKernelEventSource implements TelemetrySource {

	private final Arena arena = Arena.ofShared();
	private volatile boolean running;

	@Override
	public void start(Consumer<Perception> onEvent) throws TelemetryCaptureException {
		SymbolLookup libbpf;
		try {
			libbpf = SymbolLookup.libraryLookup("bpf", arena);
		} catch (IllegalArgumentException | IllegalCallerException e) {
			throw new TelemetryCaptureException(
					"libbpf is not available on this host; eBPF capture requires Linux with libbpf installed", e);
		}

		int majorVersion = probeMajorVersion(libbpf);
		if (majorVersion < 0) {
			throw new TelemetryCaptureException("Failed to resolve libbpf version via FFM", null);
		}

		running = true;
		// Kernel event polling wires here once a compiled BPF object is loaded
		// (bpf_object__open_file + bpf_object__load + ring_buffer__poll).
	}

	private int probeMajorVersion(SymbolLookup libbpf) {
		try {
			Linker linker = Linker.nativeLinker();
			MethodHandle majorVersion = linker.downcallHandle(
					libbpf.find("libbpf_major_version").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT)
			);
			return (int) majorVersion.invoke();
		} catch (Throwable e) {
			return -1;
		}
	}

	@Override
	public void stop() {
		running = false;
		if (arena.scope().isAlive()) {
			arena.close();
		}
	}
}
