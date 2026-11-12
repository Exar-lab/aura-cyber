package com.auracyber.memorygraph.neo4j;

import com.auracyber.memorygraph.domain.Ttp;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Ttp")
public class TtpNode {

	@Id
	private final String techniqueId;
	private final String name;
	private final String tactic;

	public TtpNode(String techniqueId, String name, String tactic) {
		this.techniqueId = techniqueId;
		this.name = name;
		this.tactic = tactic;
	}

	static TtpNode from(Ttp ttp) {
		return new TtpNode(ttp.techniqueId(), ttp.name(), ttp.tactic());
	}

	Ttp toDomain() {
		return new Ttp(techniqueId, name, tactic);
	}

	public String getTechniqueId() {
		return techniqueId;
	}

	public String getName() {
		return name;
	}

	public String getTactic() {
		return tactic;
	}
}
