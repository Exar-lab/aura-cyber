# AURA-Cyber

**Autonomous Cyber-Immune Response Ecosystem** — a swarm of Java-native autonomous agents that transforms defensive cybersecurity into a synthetic immune system: detecting anomalies, correlating attack vectors under MITRE ATT&CK, generating live countermeasures ("digital antibodies"), and validating them through adversarial self-testing before propagating them across the network.

## Vision

AURA-Cyber does not just detect threats — it evolves defenses. A swarm of autonomous agents monitors infrastructure telemetry, hypothesizes about undocumented attack vectors (zero-days), and competes internally under a bounded compute budget to isolate or eradicate threats. When a vector bypasses the first line of defense, the evolutionary engine mutates and combines agent strategies to deploy a specialized response agent ("antibody") within seconds.

## Success Criteria

Given an injection of a previously undocumented malware/exploit into a test network, the swarm must:

- Reduce **Mean Time To Detect (MTTD)** to under 10 seconds.
- Generate a functional containment rule/patch **without human intervention** in under 60 seconds.
- Keep the impact on legitimate service traffic **below 1%**.

## Agent Roles

| Agent | Responsibility |
|---|---|
| **Sentinel** | Monitors logs, network flows (PCAP), eBPF kernel events, and endpoint telemetry in real time. |
| **Correlation Synthesizer** | Correlates isolated Sentinel findings into structured attack trees (MITRE ATT&CK), queries the Immune Memory Graph for known matches. |
| **Immunizer** | Generates and applies hot-patch countermeasures: firewall/eBPF rules, config patches, container isolation, credential revocation. |
| **Red Team Adversary** | Emulates threats against honeypots/digital twins to validate Immunizer-generated rules before they propagate. |
| **AURA Orchestrator** | Threat intelligence bus; routes events between agents and owns the Immune Memory Graph. |

```
                      +----------------------------------+
                      |       AURA Orchestrator          |
                      |   (Threat Intelligence Bus)      |
                      +----------------+-----------------+
                                       |
     +-------------------+-------------+-------------+-------------------+
     |                   |                           |                   |
+----v-----+       +-----v-----+               +-----v-----+       +-----v-----+
| Sentinel |       |Synthesizer|               | Immunizer |       | Red Team  |
|  Agent   |       |   Agent   |               |   Agent   |       | Adversary |
+----+-----+       +-----+-----+               +-----+-----+       +-----+-----+
     |                   |                           |                   |
     +-------------------+-------------+-------------+-------------------+
                                       |
                        +--------------v-------------+
                        |  Immune Memory Graph (IMG) |
                        | - TTP Vectors & IoCs       |
                        | - Active Mitigation Rules  |
                        +----------------------------+
```

## Core Modules

- **Immune Memory Graph (IMG)** — graph + vector store mapping TTPs, IoCs, and mitigation rules, with a signature-decay mechanism so unconfirmed temporary rules lose strength over time.
- **Compute Credit Economy (CCE)** — agents spend compute tokens per scan/rule; a false-positive that blocks legitimate traffic costs 40% of an agent's credit balance, and bankruptcy revokes its policy.
- **Immune Simulation Sandbox** — ephemeral containers/VMs where the Red Team Adversary tests exploits against Immunizer-generated patches in full isolation.

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language / Runtime | Java 21 (Virtual Threads / Project Loom) | Massive concurrent agent instantiation without OS thread exhaustion |
| Application Framework | Spring Boot 3.x, Spring Cloud Stream | Service orchestration, async event-driven messaging |
| Agent / LLM Framework | LangChain4j, Spring AI | Agent tools, memory stores, LLM abstraction (local via Ollama/vLLM or remote) |
| Packet Capture | Pcap4j | Deep packet inspection (L2–L4) |
| Kernel Telemetry | Java FFM API (Project Panama) over libbpf (eBPF) | Native syscall / kernel event capture without classic JNI overhead |
| Signature Matching | YARA4J | Pattern/payload detection bindings |
| Graph Store | Neo4j + Spring Data Neo4j | TTP / IoC / incident relationship mapping (Cypher via POJOs) |
| Vector Store | Qdrant Java Client (or Milvus Java SDK) | Behavioral embedding search |
| In-Memory Grid | Hazelcast / Redisson (Redis) | Compute credit economy, fitness scores, mutation state |
| Messaging Bus | Apache Kafka (or Redpanda) via Spring Cloud Stream | Telemetry and inter-agent control plane |
| Low-Latency IPC | gRPC / Reactor Netty | Sub-2ms point-to-point agent communication |
| Sandbox Execution | Docker-Java Client, Testcontainers | Ephemeral isolated containers for Red Team simulation |
| Testing | JUnit 5, Testcontainers, ArchUnit | Unit/integration tests, architecture rule enforcement |

## Module Layout (Maven multi-module)

```
aura-cyber/
├── aura-telemetry/         # Pcap4j + eBPF FFM integration
├── aura-memory-graph/      # Spring Data Neo4j + Qdrant Java SDK
├── aura-agent-core/        # LangChain4j / Spring AI agents (Sentinel, Synthesizer, Immunizer, Red Team)
├── aura-cce-engine/        # Hazelcast / Redisson credit economy & mutation engine
├── aura-sandbox-executor/  # Docker-Java / Testcontainers execution
└── aura-orchestrator/      # Threat intelligence bus, Spring Boot entrypoint
```

## Architecture Rules

- **Hexagonal / Clean Architecture** per module: domain (zero framework deps) → application (use cases/ports) → infrastructure (adapters: Kafka, Neo4j, Docker, WebClient).
- Domain must not depend on application or infrastructure.
- Application must not depend on infrastructure.
- Enforced via ArchUnit tests per module.

## Status

Early scaffolding. No modules implemented yet.
