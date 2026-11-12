package com.auracyber.memorygraph.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TtpNodeRepository extends Neo4jRepository<TtpNode, String> {

	@Query("""
			MATCH (t:Ttp)
			WHERE EXISTS { (:Ioc {key: $iocKey})-[:INDICATES]->(t) }
			   OR EXISTS { (:Ioc {key: $iocKey})<-[:OBSERVED]-(:Incident)-[:EXHIBITED]->(t) }
			RETURN t
			""")
	List<TtpNode> findCorrelatedWithIoc(@Param("iocKey") String iocKey);
}
