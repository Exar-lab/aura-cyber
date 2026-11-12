package com.auracyber.memorygraph.neo4j;

import com.auracyber.memorygraph.domain.Ioc;
import com.auracyber.memorygraph.domain.IocType;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Ioc")
public class IocNode {

	@Id
	private final String key;
	private final IocType type;
	private final String value;

	@Relationship(type = "INDICATES", direction = Relationship.Direction.OUTGOING)
	private Set<TtpNode> ttps = new HashSet<>();

	public IocNode(String key, IocType type, String value) {
		this.key = key;
		this.type = type;
		this.value = value;
	}

	static IocNode from(Ioc ioc) {
		return new IocNode(keyOf(ioc), ioc.type(), ioc.value());
	}

	static String keyOf(Ioc ioc) {
		return ioc.type().name() + ":" + ioc.value();
	}

	void indicate(TtpNode ttp) {
		ttps.removeIf(existing -> existing.getTechniqueId().equals(ttp.getTechniqueId()));
		ttps.add(ttp);
	}

	public String getKey() {
		return key;
	}

	public IocType getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public Set<TtpNode> getTtps() {
		return ttps;
	}
}
