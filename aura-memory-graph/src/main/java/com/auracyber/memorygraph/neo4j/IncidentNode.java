package com.auracyber.memorygraph.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Node("Incident")
public class IncidentNode {

	@Id
	private final String incidentId;
	private final Instant observedAt;

	@Relationship(type = "OBSERVED", direction = Relationship.Direction.OUTGOING)
	private Set<IocNode> iocs = new HashSet<>();

	@Relationship(type = "EXHIBITED", direction = Relationship.Direction.OUTGOING)
	private Set<TtpNode> ttps = new HashSet<>();

	public IncidentNode(String incidentId, Instant observedAt) {
		this.incidentId = incidentId;
		this.observedAt = observedAt;
	}

	void observe(IocNode ioc) {
		iocs.add(ioc);
	}

	void exhibit(TtpNode ttp) {
		ttps.add(ttp);
	}

	public String getIncidentId() {
		return incidentId;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public Set<IocNode> getIocs() {
		return iocs;
	}

	public Set<TtpNode> getTtps() {
		return ttps;
	}
}
