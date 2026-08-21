# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AURA-Cyber is a swarm of Java-native autonomous agents that turns defensive cybersecurity into a synthetic immune system: detecting anomalies, correlating attack vectors under MITRE ATT&CK, generating live countermeasures ("digital antibodies"), and validating them through adversarial self-testing before propagating them across the network. See `README.md` for the full vision, agent-role table, and success criteria.

## Commands

Maven multi-module project, Java 25, root `pom.xml` aggregates all modules.

```bash
# Full build + test (what CI runs)
mvn -B test

# Test a single module (and the modules it depends on)
mvn -pl aura-cce-engine -am test

# Run a single test class
mvn -pl aura-cce-engine test -Dtest=HazelcastCreditLedgerTest

# Compile only
mvn -B compile
```

CI (`.github/workflows/ci.yml`) runs `mvn -B test` on every push/PR to `master`. The `master` branch is protected by a GitHub ruleset (`protect-master`) that requires that `test` check to pass and blocks direct pushes/force-pushes/deletion — no bypass.

Releases are automated with `release-please` (`.github/workflows/release-please.yml`, config in `release-please-config.json`, release-type `maven`): it auto-discovers every `pom.xml` in the repo and keeps their versions in sync from Conventional Commits. Commit messages should follow Conventional Commits so version bumps and the changelog stay accurate.

## Architecture

### Module layout and dependency direction

```
aura-agent-api/         # Framework-free domain contract: Agent lifecycle, CreditLedger port
aura-telemetry/         # Pcap4j + eBPF FFM integration
aura-memory-graph/      # Spring Data Neo4j + Qdrant Java SDK
aura-agent-core/        # LangChain4j / Spring AI agents (Sentinel, Synthesizer, Immunizer, Red Team)
aura-cce-engine/        # Hazelcast-backed credit economy & mutation engine (implements aura-agent-api ports)
aura-sandbox-executor/  # Docker-Java / Testcontainers execution
aura-orchestrator/      # Threat intelligence bus, Spring Boot entrypoint — depends on every other module
```

`aura-orchestrator` is the only module with `spring-boot-maven-plugin`; it's the executable assembly point and pulls in `aura-telemetry`, `aura-memory-graph`, `aura-agent-core`, `aura-cce-engine`, and `aura-sandbox-executor`.

### Hexagonal / Clean Architecture per module

- `aura-agent-api` holds the domain: `Agent<P, D>` (observe → decide → act contract), `AbstractAgent` (the lifecycle template method), and ports like `CreditLedger`. It depends on nothing but JUnit (test scope) — no Spring, no Hazelcast, no framework code.
- Infrastructure modules implement those ports as adapters. Example: `aura-cce-engine`'s `HazelcastCreditLedger` implements the `CreditLedger` port from `aura-agent-api` using a distributed Hazelcast map with per-agent locking for atomic balance mutations.
- Rule: domain must not depend on application or infrastructure; application must not depend on infrastructure. This is meant to be enforced via ArchUnit (`archunit-junit5` is a root-level test-scope dependency inherited by every module) — write ArchUnit rules per module as infrastructure code lands.

### Agent lifecycle (the core cross-cutting pattern)

Every swarm role (Sentinel, Correlation Synthesizer, Immunizer, Red Team Adversary) extends `AbstractAgent<P, D>`, which runs a fixed `run()` template method gated by the Compute Credit Economy:

1. Refuse to run if `CreditLedger.isBankrupt(id)` — throws `AgentExtinguishedException`.
2. `observe()` → `decide(perception)` → charge the decision's `CreditCost` via `CreditLedger.charge`.
3. `act(decision)` → if the outcome is a `Failure` with `falsePositive() == true`, penalize the agent (`PenaltyReason.FALSE_POSITIVE`, currently a 40% balance cut in `HazelcastCreditLedger`).

Concrete agents only implement `observe`/`decide`/`act`; the credit-gating sequence itself lives in `AbstractAgent` and should not be reimplemented per agent.

### Versions to know

Dependency versions are centralized as properties in the root `pom.xml` (`langchain4j.version`, `neo4j.version`, `qdrant.version`, `hazelcast.version`, `testcontainers.version`, etc.) — bump them there, not in individual module POMs. Note `langchain4j-spring-boot4-starter` (used by `aura-agent-core`) is versioned independently of `langchain4j` core with a `-betaNN` suffix; check what actually exists on Maven Central before bumping either in isolation, since the two version schemes don't move in lockstep.
