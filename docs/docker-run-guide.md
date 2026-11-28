# JuryOpt — Guide d'exécution Docker Compose, Tests & Démo Gateway

## 1. Prérequis et configuration

1. Arrêter d'éventuels conteneurs PostgreSQL locaux (`juryopt-registrar-db`, `juryopt-optimizer-db`, `juryopt-defense-db`) et tout processus Maven local.
2. Créer le fichier `.env` à la racine depuis `.env.example` (ne jamais commiter `.env`) :

```bash
cp .env.example .env
# Adapter POSTGRES_PASSWORD dans .env