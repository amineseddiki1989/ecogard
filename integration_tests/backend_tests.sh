#!/bin/bash

# Script de test d'intégration pour le backend EcoGuard
# Ce script exécute les tests d'intégration pour le backend

echo "=== Démarrage des tests d'intégration du backend EcoGuard ==="

# Vérification des prérequis
echo "Vérification des prérequis..."
if ! command -v java &> /dev/null; then
    echo "Java n'est pas installé. Veuillez installer Java 17+."
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "Maven n'est pas installé. Veuillez installer Maven 3.8+."
    exit 1
fi

# Vérification de la base de données PostgreSQL
echo "Vérification de la connexion PostgreSQL..."
if ! command -v psql &> /dev/null; then
    echo "PostgreSQL client n'est pas installé. Installation..."
    sudo apt-get update && sudo apt-get install -y postgresql-client
fi

# Vérification de la connexion à PostgreSQL
if ! pg_isready -h localhost -p 5432; then
    echo "PostgreSQL n'est pas accessible. Démarrage d'un conteneur PostgreSQL..."
    docker run --name postgres-ecoguard -e POSTGRES_PASSWORD=password -e POSTGRES_DB=ecoguard -d -p 5432:5432 postgres:13
    sleep 10  # Attendre que PostgreSQL démarre
    
    # Création de l'extension PostGIS
    echo "Configuration de PostGIS..."
    docker exec -it postgres-ecoguard psql -U postgres -d ecoguard -c "CREATE EXTENSION IF NOT EXISTS postgis;"
fi

# Vérification de Redis
echo "Vérification de la connexion Redis..."
if ! command -v redis-cli &> /dev/null; then
    echo "Redis client n'est pas installé. Installation..."
    sudo apt-get update && sudo apt-get install -y redis-tools
fi

# Vérification de la connexion à Redis
if ! redis-cli ping &> /dev/null; then
    echo "Redis n'est pas accessible. Démarrage d'un conteneur Redis..."
    docker run --name redis-ecoguard -d -p 6379:6379 redis:6
    sleep 5  # Attendre que Redis démarre
fi

# Configuration du backend
echo "Configuration du backend..."
cd /home/ubuntu/ecogard/backend

# Compilation du backend
echo "Compilation du backend..."
mvn clean install -DskipTests

# Exécution des tests unitaires
echo "Exécution des tests unitaires..."
mvn test

# Exécution des tests d'intégration
echo "Exécution des tests d'intégration..."
mvn verify

# Vérification des résultats
if [ $? -eq 0 ]; then
    echo "Tests d'intégration backend réussis!"
    exit 0
else
    echo "Tests d'intégration backend échoués!"
    exit 1
fi
