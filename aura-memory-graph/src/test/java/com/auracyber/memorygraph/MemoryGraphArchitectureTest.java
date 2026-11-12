package com.auracyber.memorygraph;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.auracyber.memorygraph", importOptions = ImportOption.DoNotIncludeTests.class)
class MemoryGraphArchitectureTest {

	@ArchTest
	static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
			.that().resideInAPackage("..memorygraph.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..memorygraph.neo4j..",
					"..memorygraph.qdrant..",
					"org.springframework..",
					"org.neo4j..",
					"io.qdrant..",
					"io.grpc..",
					"com.google.."
			);

	@ArchTest
	static final ArchRule neo4jAdapterDoesNotDependOnQdrantAdapter = noClasses()
			.that().resideInAPackage("..memorygraph.neo4j..")
			.should().dependOnClassesThat().resideInAPackage("..memorygraph.qdrant..");

	@ArchTest
	static final ArchRule qdrantAdapterDoesNotDependOnNeo4jAdapter = noClasses()
			.that().resideInAPackage("..memorygraph.qdrant..")
			.should().dependOnClassesThat().resideInAPackage("..memorygraph.neo4j..");
}
