# JuryOpt — Planification automatisée des soutenances (SOA)

Mini-projet Architecture SOA & Services Web — FSM, LGLSI3, 2026-2027.

| Service | Protocole | Port |
|---|---|---|
| api-gateway | REST (routage) | 8080 |
| defense-orchestrator-service | REST | 8081 |
| schedule-optimizer-service | gRPC | 9090 (Actuator 8082) |
| registrar-clearance-service | SOAP | 8083 |

Stack : Java 17, Spring Boot 3.3.x, Maven, PostgreSQL 16, Docker Compose.

Documentation complète à venir (Jour 8).
