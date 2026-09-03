package com.auracyber.telemetry.capture;

import com.auracyber.telemetry.domain.NetworkFlowEvent;
import com.auracyber.telemetry.domain.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.Packet;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcapNetworkFlowSourceTest {

	private static final Instant CAPTURED_AT = Instant.parse("2026-09-03T10:00:00Z");

	@Test
	void mapsTcpPacketToNetworkFlowEvent() throws org.pcap4j.packet.IllegalRawDataException {
		Packet packet = ethernetFrame(ipv4Header((byte) 6, 40), tcpSegment(443, 51000));

		Optional<NetworkFlowEvent> event = PcapNetworkFlowSource.toNetworkFlowEvent(packet, CAPTURED_AT);

		assertTrue(event.isPresent());
		assertEquals(TransportProtocol.TCP, event.get().protocol());
		assertEquals(443, event.get().sourcePort());
		assertEquals(51000, event.get().destinationPort());
		assertEquals("10.0.0.1", event.get().sourceAddress());
		assertEquals("10.0.0.2", event.get().destinationAddress());
		assertEquals(CAPTURED_AT, event.get().capturedAt());
	}

	@Test
	void mapsUdpPacketToNetworkFlowEvent() throws org.pcap4j.packet.IllegalRawDataException {
		Packet packet = ethernetFrame(ipv4Header((byte) 17, 28), udpSegment(53, 40000));

		Optional<NetworkFlowEvent> event = PcapNetworkFlowSource.toNetworkFlowEvent(packet, CAPTURED_AT);

		assertTrue(event.isPresent());
		assertEquals(TransportProtocol.UDP, event.get().protocol());
		assertEquals(53, event.get().sourcePort());
		assertEquals(40000, event.get().destinationPort());
	}

	private static Packet ethernetFrame(byte[] ipHeader, byte[] transportSegment) throws org.pcap4j.packet.IllegalRawDataException {
		ByteBuffer buffer = ByteBuffer.allocate(14 + ipHeader.length + transportSegment.length);
		buffer.put(new byte[] {0, 0, 0, 0, 0, 1}); // destination MAC
		buffer.put(new byte[] {0, 0, 0, 0, 0, 2}); // source MAC
		buffer.putShort((short) 0x0800); // IPv4 ethertype
		buffer.put(ipHeader);
		buffer.put(transportSegment);
		byte[] rawFrame = buffer.array();
		return EthernetPacket.newPacket(rawFrame, 0, rawFrame.length);
	}

	private static byte[] ipv4Header(byte protocol, int totalLength) {
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.put((byte) 0x45); // version 4, IHL 5
		buffer.put((byte) 0x00); // DSCP/ECN
		buffer.putShort((short) totalLength);
		buffer.putShort((short) 0); // identification
		buffer.putShort((short) 0); // flags/fragment offset
		buffer.put((byte) 64); // TTL
		buffer.put(protocol);
		buffer.putShort((short) 0); // header checksum
		buffer.put(new byte[] {10, 0, 0, 1}); // source address
		buffer.put(new byte[] {10, 0, 0, 2}); // destination address
		return buffer.array();
	}

	private static byte[] tcpSegment(int sourcePort, int destinationPort) {
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.putShort((short) sourcePort);
		buffer.putShort((short) destinationPort);
		buffer.putInt(0); // sequence number
		buffer.putInt(0); // acknowledgment number
		buffer.put((byte) 0x50); // data offset 5, reserved
		buffer.put((byte) 0x02); // SYN flag
		buffer.putShort((short) 0); // window
		buffer.putShort((short) 0); // checksum
		buffer.putShort((short) 0); // urgent pointer
		return buffer.array();
	}

	private static byte[] udpSegment(int sourcePort, int destinationPort) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putShort((short) sourcePort);
		buffer.putShort((short) destinationPort);
		buffer.putShort((short) 8); // length
		buffer.putShort((short) 0); // checksum
		return buffer.array();
	}
}
