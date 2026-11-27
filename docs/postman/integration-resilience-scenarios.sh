#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
HEALTH_URL="${HEALTH_URL:-$BASE_URL/actuator/health}"
PHASE="${1:-help}"

print_response() {
  local raw="$1"
  local status
  local body
  status=$(printf "%s" "$raw" | tail -n1)
  body=$(printf "%s" "$raw" | sed '$d')
  echo "HTTP $status"
  printf "%s" "$body" | python3 -m json.tool 2>/dev/null || printf "%s\n" "$body"
}

call_api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local raw
  if [ -n "$data" ]; then
    raw=$(curl -sS -w "\n%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" -d "$data")
  else
    raw=$(curl -sS -w "\n%{http_code}" -X "$method" "$BASE_URL$path")
  fi
  print_response "$raw"
}

show_circuit_breakers() {
  echo "--- Etat $HEALTH_URL (circuitBreakers) ---"
  curl -sS "$HEALTH_URL" | python3 -c '
import json, sys
data = json.load(sys.stdin)
cb = data.get("components", {}).get("circuitBreakers", {})
print(json.dumps(cb, indent=2, ensure_ascii=False))
'
}

get_defense_id_by_matricule() {
  local matricule="$1"
  curl -sS "$BASE_URL/api/v1/defenses" | python3 -c "
import json, sys
items = json.load(sys.stdin)
for item in items:
    if item.get('studentMatricule') == '$matricule':
        print(item['id'])
        sys.exit(0)
sys.exit(1)
"
}

case "$PHASE" in
  phase1)
    echo "========================================================="
    echo "PHASE 1 (3 services UP) : Scenarios 1, 2, 3 + OpenAPI"
    echo "========================================================="

    echo "=== Export OpenAPI -> docs/contracts/defense-orchestrator-openapi.yaml ==="
    curl -fsS "$BASE_URL/v3/api-docs.yaml" -o docs/contracts/defense-orchestrator-openapi.yaml
    ls -lh docs/contracts/defense-orchestrator-openapi.yaml

    echo ""
    echo "=== Scenario 1 : Etudiant eligible ETU-2026-001 (attendu: 201 ELIGIBLE + token) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-001",
      "projectTitle": "Plateforme SOA de planification des soutenances",
      "supervisorCode": "PROF-01",
      "presidentCode": "PROF-02",
      "rapporteurCode": "PROF-03"
    }'

    echo ""
    echo "=== Scenario 2a : Credits insuffisants ETU-2026-005 (attendu: 422) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-005",
      "projectTitle": "Sujet credits insuffisants",
      "supervisorCode": "PROF-01",
      "presidentCode": "PROF-02",
      "rapporteurCode": "PROF-03"
    }'

    echo ""
    echo "=== Scenario 2b : Blocage administratif ETU-2026-006 (attendu: 422) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-006",
      "projectTitle": "Sujet blocage administratif",
      "supervisorCode": "PROF-01",
      "presidentCode": "PROF-02",
      "rapporteurCode": "PROF-03"
    }'

    echo ""
    echo "=== Scenario 2c : Etudiant inconnu ETU-2026-999 (attendu: 404) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-999",
      "projectTitle": "Sujet etudiant inconnu",
      "supervisorCode": "PROF-01",
      "presidentCode": "PROF-02",
      "rapporteurCode": "PROF-03"
    }'

    echo ""
    echo "=== Verification Scenario 2 : GET /api/v1/defenses (seul ETU-2026-001 en base) ==="
    call_api GET "/api/v1/defenses"

    echo ""
    echo "=== Scenario 3 : 4 erreurs metier repetees sur ETU-2026-005 (circuit reste CLOSED) ==="
    for i in 1 2 3 4; do
      code=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/defenses" \
        -H "Content-Type: application/json" \
        -d '{
          "studentMatricule": "ETU-2026-005",
          "projectTitle": "Test circuit metier",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-02",
          "rapporteurCode": "PROF-03"
        }')
      echo "Appel metier #$i -> HTTP $code"
    done
    show_circuit_breakers
    ;;

  phase2)
    echo "========================================================="
    echo "PHASE 2 (SOAP ARRETE) : Scenarios 4 et 5a (PROVISIONAL + OPEN)"
    echo "========================================================="

    echo "=== Scenario 4 : ETU-2026-002 avec SOAP arrete (attendu: 201 PROVISIONAL + retry dans logs) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-002",
      "studentName": "Sarra Trabelsi",
      "projectTitle": "Orchestration resiliente des soutenances",
      "supervisorCode": "PROF-01",
      "presidentCode": "PROF-04",
      "rapporteurCode": "PROF-05"
    }'
    show_circuit_breakers

    echo ""
    echo "=== Scenario 5a : ETU-2026-003 avec SOAP arrete (attendu: 201 PROVISIONAL + circuit OPEN) ==="
    call_api POST "/api/v1/defenses" '{
      "studentMatricule": "ETU-2026-003",
      "studentName": "Youssef Khelifi",
      "projectTitle": "Optimisation multi-criteres des jurys",
      "supervisorCode": "PROF-02",
      "presidentCode": "PROF-04",
      "rapporteurCode": "PROF-06"
    }'
    show_circuit_breakers
    ;;

  phase3)
    echo "========================================================="
    echo "PHASE 3 (SOAP REDEMARRE) : Scenarios 5b/5c et 6 (HALF_OPEN -> CLOSED + ELIGIBLE)"
    echo "========================================================="

    echo "Attente de 16 secondes (wait-duration-in-open-state = 15s)..."
    sleep 16

    echo "=== Scenario 5b : Etat du circuit apres 16s (attendu: HALF_OPEN) ==="
    show_circuit_breakers

    ID2=$(get_defense_id_by_matricule "ETU-2026-002")
    ID3=$(get_defense_id_by_matricule "ETU-2026-003")

    echo ""
    echo "=== Scenario 6a : Reverification ETU-2026-002 (id=$ID2, attendu: 200 ELIGIBLE) ==="
    call_api POST "/api/v1/defenses/$ID2/clearances"

    echo ""
    echo "=== Scenario 6b : Reverification ETU-2026-003 (id=$ID3, attendu: 200 ELIGIBLE) ==="
    call_api POST "/api/v1/defenses/$ID3/clearances"

    echo ""
    echo "=== Scenario 5c : Etat du circuit apres 2 succes en HALF_OPEN (attendu: CLOSED) ==="
    show_circuit_breakers
    ;;

  phase4)
    echo "========================================================="
    echo "PHASE 4 (3 services UP) : Creneaux + Scenario 7 (Optimisation + Evaluation)"
    echo "========================================================="

    echo "=== Creation de 3 creneaux disponibles ==="
    call_api POST "/api/v1/slots" '{"slotCode": "2026-06-15_09:00", "roomCode": "SALLE-A1", "available": true}'
    call_api POST "/api/v1/slots" '{"slotCode": "2026-06-15_10:00", "roomCode": "SALLE-A1", "available": true}'
    call_api POST "/api/v1/slots" '{"slotCode": "2026-06-15_11:00", "roomCode": "SALLE-A2", "available": true}'

    echo ""
    echo "=== Scenario 7a : Optimisation gRPC streaming (attendu: 201 DRAFT + 5 etapes) ==="
    call_api POST "/api/v1/schedules/optimization-runs"

    echo ""
    echo "=== Scenario 7b : Evaluation gRPC unaire AVEC conflits (attendu: 200 valid=false, 2 conflits) ==="
    call_api POST "/api/v1/schedules/evaluations" '{
      "assignments": [
        {
          "requestId": "1",
          "slotCode": "2026-06-15_09:00",
          "roomCode": "SALLE-A1",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-02",
          "rapporteurCode": "PROF-03"
        },
        {
          "requestId": "2",
          "slotCode": "2026-06-15_09:00",
          "roomCode": "SALLE-A1",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-04",
          "rapporteurCode": "PROF-05"
        }
      ]
    }'

    echo ""
    echo "=== Scenario 7c : Evaluation gRPC unaire SANS conflit (attendu: 200 valid=true, 0 conflit) ==="
    call_api POST "/api/v1/schedules/evaluations" '{
      "assignments": [
        {
          "requestId": "1",
          "slotCode": "2026-06-15_09:00",
          "roomCode": "SALLE-A1",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-02",
          "rapporteurCode": "PROF-03"
        },
        {
          "requestId": "2",
          "slotCode": "2026-06-15_10:00",
          "roomCode": "SALLE-A1",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-04",
          "rapporteurCode": "PROF-05"
        }
      ]
    }'
    ;;

  phase5)
    echo "========================================================="
    echo "PHASE 5 (gRPC ARRETE) : Scenario 8 (UNOPTIMIZED_FALLBACK + refus 422 + eval 503)"
    echo "========================================================="

    echo "=== Scenario 8a : Optimisation avec gRPC arrete (attendu: 201 UNOPTIMIZED_FALLBACK) ==="
    RAW_FALLBACK=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/schedules/optimization-runs")
    print_response "$RAW_FALLBACK"
    FALLBACK_ID=$(printf "%s" "$RAW_FALLBACK" | sed '$d' | python3 -c "import json, sys; print(json.load(sys.stdin)['scheduleId'])")

    echo ""
    echo "=== Scenario 8b : Refus de publication du planning fallback id=$FALLBACK_ID (attendu: 422) ==="
    call_api POST "/api/v1/schedules/$FALLBACK_ID/publications"

    echo ""
    echo "=== Scenario 8c : Evaluation avec gRPC arrete (attendu: 503, jamais un faux 0 conflit) ==="
    call_api POST "/api/v1/schedules/evaluations" '{
      "assignments": [
        {
          "requestId": "1",
          "slotCode": "2026-06-15_09:00",
          "roomCode": "SALLE-A1",
          "supervisorCode": "PROF-01",
          "presidentCode": "PROF-02",
          "rapporteurCode": "PROF-03"
        }
      ]
    }'
    ;;

  phase6)
    echo "========================================================="
    echo "PHASE 6 (gRPC REDEMARRE) : Scenario 9 (Nouveau DRAFT + Publication REG-...)"
    echo "========================================================="

    echo "=== Relance optimisation propre pour obtenir un dernier planning DRAFT sans conflit ==="
    RAW_DRAFT=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/schedules/optimization-runs")
    print_response "$RAW_DRAFT"
    DRAFT_ID=$(printf "%s" "$RAW_DRAFT" | sed '$d' | python3 -c "import json, sys; print(json.load(sys.stdin)['scheduleId'])")

    echo ""
    echo "=== Scenario 9 : Publication officielle du planning id=$DRAFT_ID (attendu: 200 PUBLISHED + REG-...) ==="
    call_api POST "/api/v1/schedules/$DRAFT_ID/publications"

    echo ""
    echo "=== Verification finale : GET /api/v1/defenses (statuts PUBLISHED et codes REG-...) ==="
    call_api GET "/api/v1/defenses"
    ;;

  *)
    echo "Usage : [BASE_URL=...] [HEALTH_URL=...] bash docs/postman/integration-resilience-scenarios.sh <phase1|phase2|phase3|phase4|phase5|phase6>"
    echo "  Exemple Gateway : BASE_URL=http://localhost:8080 HEALTH_URL=http://localhost:8080/ops/orchestrator/health bash docs/postman/integration-resilience-scenarios.sh phase1"
    echo "  phase1 : 3 services UP  -> Export OpenAPI + Scenarios 1, 2, 3"
    echo "  phase2 : SOAP ARRETE    -> Scenarios 4 et 5a (PROVISIONAL + OPEN)"
    echo "  phase3 : SOAP REDEMARRE -> Scenarios 5b/5c et 6 (HALF_OPEN -> CLOSED + ELIGIBLE)"
    echo "  phase4 : 3 services UP  -> Creneaux + Scenario 7 (Optimisation + Evaluation)"
    echo "  phase5 : gRPC ARRETE    -> Scenario 8 (UNOPTIMIZED_FALLBACK + 422 + 503)"
    echo "  phase6 : gRPC REDEMARRE -> Scenario 9 (Nouveau DRAFT + Publication REG-...)"
    ;;
esac