package com.auracyber.telemetry.capture;

import com.auracyber.agent.domain.Perception;
import com.auracyber.telemetry.domain.NetworkFlowEvent;
import com.auracyber.telemetry.domain.TelemetryCaptureException;
import com.auracyber.telemetry.domain.TelemetrySource;
import com.auracyber.telemetry.domain.TransportProtocol;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;

import java.io.EOFException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Captures L2-L4 network flow telemetry off a live network interface via
 * Pcap4j and republishes each frame as a {@link NetworkFlowEvent}.
 */
public class PcapNetworkFlowSource implements TelemetrySource {

	private static final int SNAPSHOT_LENGTH = 65536;
	private static final int READ_TIMEOUT_MILLIS = 100;

	private final PcapNetworkInterface networkInterface;
	private volatile PcapHandle handle;
	private volatile boolean running;

	public PcapNetworkFlowSource(PcapNetworkInterface networkInterface) {
		this.networkInterface = networkInterface;
	}

	/** Resolves the interface behind the host's default outbound address. */
	public static PcapNetworkFlowSource onDefaultInterface() throws TelemetryCaptureException {
		try {
			PcapNetworkInterface defaultInterface = Pcaps.getDevByAddress(InetAddress.getLocalHost());
			if (defaultInterface == null) {
				throw new TelemetryCaptureException("No default network interface available for packet capture", null);
			}
			return new PcapNetworkFlowSource(defaultInterface);
		} catch (UnknownHostException | PcapNativeException e) {
			throw new TelemetryCaptureException("Failed to resolve default network interface", e);
		}
	}

	@Override
	public void start(Consumer<Perception> onEvent) throws TelemetryCaptureException {
		try {
			handle = networkInterface.openLive(SNAPSHOT_LENGTH, PromiscuousMode.PROMISCUOUS, READ_TIMEOUT_MILLIS);
		} catch (PcapNativeException e) {
			throw new TelemetryCaptureException("Failed to open live capture on " + networkInterface.getName(), e);
		}
		running = true;
		Thread captureThread = new Thread(() -> pollLoop(onEvent), "aura-telemetry-pcap-" + networkInterface.getName());
		captureThread.setDaemon(true);
		captureThread.start();
	}

	private void pollLoop(Consumer<Perception> onEvent) {
		while (running) {
			try {
				Packet packet = handle.getNextPacketEx();
				toNetworkFlowEvent(packet, Instant.now()).ifPresent(onEvent::accept);
			} catch (NotOpenException e) {
				running = false;
			} catch (PcapNativeException | TimeoutException | EOFException e) {
				// transient capture read miss, keep polling until stop() is called
			}
		}
	}

	@Override
	public void stop() {
		running = false;
		PcapHandle currentHandle = handle;
		if (currentHandle != null && currentHandle.isOpen()) {
			currentHandle.close();
		}
	}

	static Optional<NetworkFlowEvent> toNetworkFlowEvent(Packet packet, Instant capturedAt) {
		IpPacket ipPacket = packet.get(IpPacket.class);
		if (ipPacket == null) {
			return Optional.empty();
		}
		String sourceAddress = ipPacket.getHeader().getSrcAddr().getHostAddress();
		String destinationAddress = ipPacket.getHeader().getDstAddr().getHostAddress();

		TcpPacket tcpPacket = packet.get(TcpPacket.class);
		if (tcpPacket != null) {
			return Optional.of(new NetworkFlowEvent(
					capturedAt,
					sourceAddress,
					tcpPacket.getHeader().getSrcPort().valueAsInt(),
					destinationAddress,
					tcpPacket.getHeader().getDstPort().valueAsInt(),
					TransportProtocol.TCP,
					packet.length()
			));
		}

		UdpPacket udpPacket = packet.get(UdpPacket.class);
		if (udpPacket != null) {
			return Optional.of(new NetworkFlowEvent(
					capturedAt,
					sourceAddress,
					udpPacket.getHeader().getSrcPort().valueAsInt(),
					destinationAddress,
					udpPacket.getHeader().getDstPort().valueAsInt(),
					TransportProtocol.UDP,
					packet.length()
			));
		}

		return Optional.of(new NetworkFlowEvent(
				capturedAt, sourceAddress, 0, destinationAddress, 0, TransportProtocol.OTHER, packet.length()
		));
	}
}
