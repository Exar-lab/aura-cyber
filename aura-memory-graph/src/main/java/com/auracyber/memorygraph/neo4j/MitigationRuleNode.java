package com.auracyber.memorygraph.neo4j;

import com.auracyber.memorygraph.domain.MitigationRule;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;

@Node("MitigationRule")
public class MitigationRuleNode {

	@Id
	private final String ruleId;
	private final String countermeasure;
	private final double confidence;
	private final Instant assessedAt;
	private final boolean confirmed;

	@Relationship(type = "MITIGATES", direction = Relationship.Direction.OUTGOING)
	private TtpNode ttp;

	public MitigationRuleNode(
			String ruleId, String countermeasure, double confidence, Instant assessedAt, boolean confirmed
	) {
		this.ruleId = ruleId;
		this.countermeasure = countermeasure;
		this.confidence = confidence;
		this.assessedAt = assessedAt;
		this.confirmed = confirmed;
	}

	static MitigationRuleNode from(MitigationRule rule, TtpNode ttp) {
		MitigationRuleNode node = new MitigationRuleNode(
				rule.ruleId(), rule.countermeasure(), rule.confidence(), rule.assessedAt(), rule.confirmed()
		);
		node.ttp = ttp;
		return node;
	}

	MitigationRule toDomain() {
		return new MitigationRule(ruleId, ttp.getTechniqueId(), countermeasure, confidence, assessedAt, confirmed);
	}

	public String getRuleId() {
		return ruleId;
	}

	public String getCountermeasure() {
		return countermeasure;
	}

	public double getConfidence() {
		return confidence;
	}

	public Instant getAssessedAt() {
		return assessedAt;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public TtpNode getTtp() {
		return ttp;
	}
}
