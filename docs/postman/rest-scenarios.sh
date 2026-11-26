#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

echo "=== 1. Healthcheck ==="
curl -s "$BASE_URL/actuator/health" ; echo

echo "=== 2. POST /api/v1/defenses (201 Created + Location) ==="
curl -i -s -X POST "$BASE_URL/api/v1/defenses" \
  -H "Content-Type: application/json" \
  -d '{
    "studentMatricule": "ETU-2026-001",
    "studentName": "Amine Ben Salem",
    "projectTitle": "Plateforme SOA de planification des soutenances",
    "supervisorCode": "PROF-01",
    "presidentCode": "PROF-02",
    "rapporteurCode": "PROF-03"
  }' ; echo

echo "=== 3. POST /api/v1/defenses doublon matricule (409 Conflict) ==="
curl -i -s -X POST "$BASE_URL/api/v1/defenses" \
  -H "Content-Type: application/json" \
  -d '{
    "studentMatricule": "ETU-2026-001",
    "projectTitle": "Doublon test",
    "supervisorCode": "PROF-01",
    "presidentCode": "PROF-02",
    "rapporteurCode": "PROF-03"
  }' ; echo

echo "=== 4. POST /api/v1/defenses jury non distinct (422 Unprocessable Entity) ==="
curl -i -s -X POST "$BASE_URL/api/v1/defenses" \
  -H "Content-Type: application/json" \
  -d '{
    "studentMatricule": "ETU-2026-002",
    "projectTitle": "Test jury invalide",
    "supervisorCode": "PROF-01",
    "presidentCode": "PROF-01",
    "rapporteurCode": "PROF-03"
  }' ; echo

echo "=== 5. POST /api/v1/defenses format invalide (400 Bad Request) ==="
curl -i -s -X POST "$BASE_URL/api/v1/defenses" \
  -H "Content-Type: application/json" \
  -d '{
    "studentMatricule": "INVALID",
    "projectTitle": "Test format",
    "supervisorCode": "PROF-01",
    "presidentCode": "PROF-02",
    "rapporteurCode": "PROF-03"
  }' ; echo

echo "=== 6. GET /api/v1/defenses/999 (404 Not Found) ==="
curl -i -s "$BASE_URL/api/v1/defenses/999" ; echo

echo "=== 7. POST /api/v1/slots (201 Created) ==="
curl -i -s -X POST "$BASE_URL/api/v1/slots" \
  -H "Content-Type: application/json" \
  -d '{
    "slotCode": "2026-06-15_09:00",
    "roomCode": "SALLE-A1",
    "available": true
  }' ; echo

echo "=== 8. GET /api/v1/defenses & /api/v1/slots (200 OK) ==="
curl -s "$BASE_URL/api/v1/defenses" ; echo
curl -s "$BASE_URL/api/v1/slots" ; echo