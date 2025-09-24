#!/bin/bash

# Script d'installation des dépendances pour l'environnement de test EcoGuard
# Ce script installe PostgreSQL avec PostGIS, Redis, et d'autres dépendances nécessaires

echo "=== Configuration de l'environnement de test EcoGuard ==="
echo "Date: $(date)"

# Fonction pour afficher les messages d'information
info() {
    echo -e "\e[1;34m[INFO]\e[0m $1"
}

# Fonction pour afficher les messages de succès
success() {
    echo -e "\e[1;32m[SUCCESS]\e[0m $1"
}

# Fonction pour afficher les messages d'erreur
error() {
    echo -e "\e[1;31m[ERROR]\e[0m $1"
}

# Fonction pour vérifier si une commande existe
command_exists() {
    command -v "$1" &> /dev/null
}

# Vérification des privilèges sudo
if ! command_exists sudo; then
    error "La commande sudo n'est pas disponible. Veuillez l'installer ou exécuter ce script en tant que root."
    exit 1
fi

# Mise à jour des paquets
info "Mise à jour des paquets..."
sudo apt-get update || {
    error "Échec de la mise à jour des paquets."
    exit 1
}
success "Paquets mis à jour avec succès."

# Installation de Docker si nécessaire
if ! command_exists docker; then
    info "Installation de Docker..."
    sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io
    sudo systemctl enable docker
    sudo systemctl start docker
    sudo usermod -aG docker $USER
    success "Docker installé avec succès."
else
    info "Docker est déjà installé."
fi

# Installation de Docker Compose si nécessaire
if ! command_exists docker-compose; then
    info "Installation de Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    success "Docker Compose installé avec succès."
else
    info "Docker Compose est déjà installé."
fi

# Création du répertoire pour les données persistantes
DOCKER_DATA_DIR="/home/ubuntu/ecogard/docker_data"
mkdir -p $DOCKER_DATA_DIR/postgres
mkdir -p $DOCKER_DATA_DIR/redis
success "Répertoires pour les données persistantes créés."

# Création du fichier docker-compose.yml
DOCKER_COMPOSE_FILE="/home/ubuntu/ecogard/docker-compose.yml"
info "Création du fichier docker-compose.yml..."
cat > $DOCKER_COMPOSE_FILE << EOF
version: '3.8'

services:
  postgres:
    image: postgis/postgis:13-3.1
    container_name: ecoguard-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
      POSTGRES_DB: ecoguard
    ports:
      - "5432:5432"
    volumes:
      - ${DOCKER_DATA_DIR}/postgres:/var/lib/postgresql/data
      - ./init-scripts:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:6
    container_name: ecoguard-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - ${DOCKER_DATA_DIR}/redis:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  pgadmin:
    image: dpage/pgadmin4
    container_name: ecoguard-pgadmin
    restart: unless-stopped
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@ecoguard.com
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "5050:80"
    depends_on:
      - postgres

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: ecoguard-redis-commander
    restart: unless-stopped
    environment:
      - REDIS_HOSTS=local:redis:6379
    ports:
      - "8081:8081"
    depends_on:
      - redis
EOF
success "Fichier docker-compose.yml créé avec succès."

# Création du répertoire pour les scripts d'initialisation
INIT_SCRIPTS_DIR="/home/ubuntu/ecogard/init-scripts"
mkdir -p $INIT_SCRIPTS_DIR

# Création du script d'initialisation pour PostgreSQL
info "Création du script d'initialisation pour PostgreSQL..."
cat > $INIT_SCRIPTS_DIR/01-init-db.sh << EOF
#!/bin/bash
set -e

# Création de l'extension PostGIS
echo "Création de l'extension PostGIS..."
psql -v ON_ERROR_STOP=1 --username "\$POSTGRES_USER" --dbname "\$POSTGRES_DB" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EOSQL

# Création des tables principales
echo "Création des tables principales..."
psql -v ON_ERROR_STOP=1 --username "\$POSTGRES_USER" --dbname "\$POSTGRES_DB" <<-EOSQL
    -- Création de la table des utilisateurs
    CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        email VARCHAR(100) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        first_name VARCHAR(50),
        last_name VARCHAR(50),
        phone_number VARCHAR(20),
        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
        last_login_at TIMESTAMP
    );

    -- Création de la table des appareils
    CREATE TABLE IF NOT EXISTS devices (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id),
        name VARCHAR(100) NOT NULL,
        device_id VARCHAR(100) UNIQUE NOT NULL,
        partition_id VARCHAR(100) UNIQUE NOT NULL,
        model VARCHAR(100),
        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
        last_seen_at TIMESTAMP
    );

    -- Création de la table des points de tracking
    CREATE TABLE IF NOT EXISTS tracking_points (
        id SERIAL PRIMARY KEY,
        device_id INTEGER NOT NULL REFERENCES devices(id),
        latitude DOUBLE PRECISION NOT NULL,
        longitude DOUBLE PRECISION NOT NULL,
        accuracy FLOAT,
        altitude DOUBLE PRECISION,
        speed FLOAT,
        bearing FLOAT,
        timestamp TIMESTAMP NOT NULL,
        type VARCHAR(20) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        title VARCHAR(100),
        description TEXT,
        address TEXT,
        metadata JSONB,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
        geom GEOGRAPHY(POINT, 4326)
    );

    -- Création de l'index spatial
    CREATE INDEX idx_tracking_points_geom ON tracking_points USING GIST(geom);

    -- Création de la table des déclarations de vol
    CREATE TABLE IF NOT EXISTS theft_reports (
        id SERIAL PRIMARY KEY,
        device_id INTEGER NOT NULL REFERENCES devices(id),
        user_id INTEGER NOT NULL REFERENCES users(id),
        report_date TIMESTAMP NOT NULL DEFAULT NOW(),
        theft_date TIMESTAMP NOT NULL,
        location_description TEXT,
        circumstances TEXT,
        status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
        police_report_number VARCHAR(50),
        resolved_at TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

    -- Création de la table des observations
    CREATE TABLE IF NOT EXISTS observations (
        id SERIAL PRIMARY KEY,
        device_id INTEGER NOT NULL REFERENCES devices(id),
        latitude DOUBLE PRECISION NOT NULL,
        longitude DOUBLE PRECISION NOT NULL,
        accuracy FLOAT,
        timestamp TIMESTAMP NOT NULL,
        reporter_id VARCHAR(100),
        confidence FLOAT NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        processed_at TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        geom GEOGRAPHY(POINT, 4326)
    );

    -- Création de l'index spatial
    CREATE INDEX idx_observations_geom ON observations USING GIST(geom);

    -- Création de la table des notifications
    CREATE TABLE IF NOT EXISTS notifications (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id),
        title VARCHAR(100) NOT NULL,
        message TEXT NOT NULL,
        type VARCHAR(20) NOT NULL,
        read BOOLEAN NOT NULL DEFAULT FALSE,
        read_at TIMESTAMP,
        data JSONB,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

    -- Création d'un utilisateur de test
    INSERT INTO users (username, email, password, first_name, last_name, status)
    VALUES ('testuser', 'test@example.com', '\$2a\$10\$8KzaNdKwIYeWU0vN6BnOWOPwEPQX0QOXUOAJyZIXUdbvjCJGwcSoW', 'Test', 'User', 'ACTIVE')
    ON CONFLICT (username) DO NOTHING;
EOSQL

echo "Initialisation de la base de données terminée."
EOF
chmod +x $INIT_SCRIPTS_DIR/01-init-db.sh
success "Script d'initialisation pour PostgreSQL créé avec succès."

# Démarrage des conteneurs Docker
info "Démarrage des conteneurs Docker..."
cd /home/ubuntu/ecogard
docker-compose up -d

# Vérification que les conteneurs sont en cours d'exécution
info "Vérification des conteneurs..."
sleep 10  # Attendre que les conteneurs démarrent

if docker ps | grep -q "ecoguard-postgres" && docker ps | grep -q "ecoguard-redis"; then
    success "Les conteneurs PostgreSQL et Redis sont en cours d'exécution."
else
    error "Certains conteneurs ne sont pas en cours d'exécution. Veuillez vérifier les logs Docker."
    docker-compose logs
    exit 1
fi

# Installation des outils clients
info "Installation des outils clients PostgreSQL et Redis..."
sudo apt-get install -y postgresql-client redis-tools

# Vérification de la connexion à PostgreSQL
info "Vérification de la connexion à PostgreSQL..."
if PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT version();" > /dev/null 2>&1; then
    success "Connexion à PostgreSQL réussie."
else
    error "Échec de la connexion à PostgreSQL."
    exit 1
fi

# Vérification de la connexion à Redis
info "Vérification de la connexion à Redis..."
if redis-cli ping | grep -q "PONG"; then
    success "Connexion à Redis réussie."
else
    error "Échec de la connexion à Redis."
    exit 1
fi

# Installation de Java si nécessaire
if ! command_exists java; then
    info "Installation de Java..."
    sudo apt-get install -y openjdk-17-jdk
    success "Java installé avec succès."
else
    info "Java est déjà installé."
    java -version
fi

# Installation de Maven si nécessaire
if ! command_exists mvn; then
    info "Installation de Maven..."
    sudo apt-get install -y maven
    success "Maven installé avec succès."
else
    info "Maven est déjà installé."
    mvn --version
fi

# Installation de Node.js si nécessaire
if ! command_exists node; then
    info "Installation de Node.js..."
    curl -fsSL https://deb.nodesource.com/setup_16.x | sudo -E bash -
    sudo apt-get install -y nodejs
    success "Node.js installé avec succès."
else
    info "Node.js est déjà installé."
    node --version
fi

# Installation des outils Android si nécessaire
if ! command_exists sdkmanager; then
    info "Installation des outils Android SDK..."
    sudo apt-get install -y android-sdk
    success "Outils Android SDK installés avec succès."
else
    info "Les outils Android SDK sont déjà installés."
fi

# Résumé
echo ""
echo "=== Configuration de l'environnement de test terminée ==="
echo ""
echo "Services disponibles :"
echo "- PostgreSQL : localhost:5432 (utilisateur: postgres, mot de passe: password, base de données: ecoguard)"
echo "- Redis : localhost:6379"
echo "- pgAdmin : http://localhost:5050 (email: admin@ecoguard.com, mot de passe: admin)"
echo "- Redis Commander : http://localhost:8081"
echo ""
echo "Pour démarrer les tests d'intégration, exécutez :"
echo "cd /home/ubuntu/ecogard/integration_tests && ./run_integration_tests.sh"
echo ""

success "Environnement de test configuré avec succès!"
