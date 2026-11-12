package com.auracyber.memorygraph.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface MitigationRuleNodeRepository extends Neo4jRepository<MitigationRuleNode, String> {
}
