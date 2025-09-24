#!/bin/bash

# Script de vérification pour PostgreSQL et Redis
# Ce script vérifie que PostgreSQL et Redis sont correctement installés et configurés

echo "=== Vérification des services PostgreSQL et Redis ==="
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

# Fonction pour afficher les messages d'avertissement
warning() {
    echo -e "\e[1;33m[WARNING]\e[0m $1"
}

# Fonction pour vérifier si une commande existe
command_exists() {
    command -v "$1" &> /dev/null
}

# Vérification des outils clients
info "Vérification des outils clients..."

if ! command_exists psql; then
    warning "Client PostgreSQL (psql) non trouvé. Installation..."
    sudo apt-get update && sudo apt-get install -y postgresql-client
else
    success "Client PostgreSQL (psql) trouvé."
fi

if ! command_exists redis-cli; then
    warning "Client Redis (redis-cli) non trouvé. Installation..."
    sudo apt-get update && sudo apt-get install -y redis-tools
else
    success "Client Redis (redis-cli) trouvé."
fi

# Vérification de Docker
info "Vérification de Docker..."

if ! command_exists docker; then
    error "Docker n'est pas installé. Veuillez exécuter le script setup_test_environment.sh."
    exit 1
else
    success "Docker est installé."
fi

if ! command_exists docker-compose; then
    error "Docker Compose n'est pas installé. Veuillez exécuter le script setup_test_environment.sh."
    exit 1
else
    success "Docker Compose est installé."
fi

# Vérification des conteneurs Docker
info "Vérification des conteneurs Docker..."

if ! docker ps &> /dev/null; then
    error "Impossible d'exécuter la commande docker ps. Vérifiez que le service Docker est en cours d'exécution et que vous avez les permissions nécessaires."
    exit 1
fi

# Vérification du conteneur PostgreSQL
info "Vérification du conteneur PostgreSQL..."

if docker ps | grep -q "ecoguard-postgres"; then
    success "Le conteneur PostgreSQL est en cours d'exécution."
    
    # Vérification de l'état du conteneur
    if docker inspect --format='{{.State.Health.Status}}' ecoguard-postgres 2>/dev/null | grep -q "healthy"; then
        success "Le conteneur PostgreSQL est en bon état."
    else
        warning "Le conteneur PostgreSQL pourrait ne pas être en bon état. Vérification de la connexion..."
    fi
else
    error "Le conteneur PostgreSQL n'est pas en cours d'exécution."
    
    # Vérification si le conteneur existe mais n'est pas en cours d'exécution
    if docker ps -a | grep -q "ecoguard-postgres"; then
        warning "Le conteneur PostgreSQL existe mais n'est pas en cours d'exécution. Tentative de démarrage..."
        docker start ecoguard-postgres
        sleep 5
        
        if docker ps | grep -q "ecoguard-postgres"; then
            success "Le conteneur PostgreSQL a été démarré avec succès."
        else
            error "Impossible de démarrer le conteneur PostgreSQL."
            docker logs ecoguard-postgres
            exit 1
        fi
    else
        error "Le conteneur PostgreSQL n'existe pas. Veuillez exécuter le script setup_test_environment.sh."
        exit 1
    fi
fi

# Vérification du conteneur Redis
info "Vérification du conteneur Redis..."

if docker ps | grep -q "ecoguard-redis"; then
    success "Le conteneur Redis est en cours d'exécution."
    
    # Vérification de l'état du conteneur
    if docker inspect --format='{{.State.Health.Status}}' ecoguard-redis 2>/dev/null | grep -q "healthy"; then
        success "Le conteneur Redis est en bon état."
    else
        warning "Le conteneur Redis pourrait ne pas être en bon état. Vérification de la connexion..."
    fi
else
    error "Le conteneur Redis n'est pas en cours d'exécution."
    
    # Vérification si le conteneur existe mais n'est pas en cours d'exécution
    if docker ps -a | grep -q "ecoguard-redis"; then
        warning "Le conteneur Redis existe mais n'est pas en cours d'exécution. Tentative de démarrage..."
        docker start ecoguard-redis
        sleep 5
        
        if docker ps | grep -q "ecoguard-redis"; then
            success "Le conteneur Redis a été démarré avec succès."
        else
            error "Impossible de démarrer le conteneur Redis."
            docker logs ecoguard-redis
            exit 1
        fi
    else
        error "Le conteneur Redis n'existe pas. Veuillez exécuter le script setup_test_environment.sh."
        exit 1
    fi
fi

# Vérification de la connexion à PostgreSQL
info "Vérification de la connexion à PostgreSQL..."

if PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT version();" &> /dev/null; then
    success "Connexion à PostgreSQL réussie."
    
    # Affichage de la version de PostgreSQL
    echo "Version PostgreSQL:"
    PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT version();"
    
    # Vérification de l'extension PostGIS
    echo "Extension PostGIS:"
    if PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT PostGIS_Version();" &> /dev/null; then
        success "L'extension PostGIS est correctement installée."
        PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT PostGIS_Version();"
    else
        error "L'extension PostGIS n'est pas installée correctement."
        exit 1
    fi
    
    # Vérification des tables
    echo "Tables dans la base de données:"
    PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "\dt"
    
    # Vérification de l'utilisateur de test
    echo "Utilisateur de test:"
    if PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT username, email FROM users WHERE username = 'testuser';" | grep -q "testuser"; then
        success "L'utilisateur de test existe."
        PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT username, email FROM users WHERE username = 'testuser';"
    else
        warning "L'utilisateur de test n'existe pas."
    fi
else
    error "Échec de la connexion à PostgreSQL."
    exit 1
fi

# Vérification de la connexion à Redis
info "Vérification de la connexion à Redis..."

if redis-cli ping | grep -q "PONG"; then
    success "Connexion à Redis réussie."
    
    # Affichage des informations Redis
    echo "Informations Redis:"
    redis-cli info | grep -E "redis_version|connected_clients|used_memory_human|total_connections_received"
    
    # Test d'écriture/lecture
    echo "Test d'écriture/lecture Redis:"
    redis-cli set ecoguard_test "Test réussi à $(date)"
    redis-cli get ecoguard_test
    
    # Vérification de la persistance
    echo "Configuration de persistance Redis:"
    redis-cli config get appendonly
else
    error "Échec de la connexion à Redis."
    exit 1
fi

# Vérification des interfaces web
info "Vérification des interfaces web..."

# pgAdmin
if curl -s -o /dev/null -w "%{http_code}" http://localhost:5050 | grep -q "200\|302"; then
    success "L'interface pgAdmin est accessible à http://localhost:5050"
else
    warning "L'interface pgAdmin n'est pas accessible. Vérifiez que le conteneur ecoguard-pgadmin est en cours d'exécution."
fi

# Redis Commander
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8081 | grep -q "200\|302"; then
    success "L'interface Redis Commander est accessible à http://localhost:8081"
else
    warning "L'interface Redis Commander n'est pas accessible. Vérifiez que le conteneur ecoguard-redis-commander est en cours d'exécution."
fi

# Résumé
echo ""
echo "=== Résumé de la vérification ==="
echo ""
echo "PostgreSQL:"
echo "- Conteneur: $(docker ps | grep -q "ecoguard-postgres" && echo "En cours d'exécution" || echo "Arrêté")"
echo "- Connexion: $(PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT 1;" &> /dev/null && echo "Réussie" || echo "Échouée")"
echo "- Extension PostGIS: $(PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT PostGIS_Version();" &> /dev/null && echo "Installée" || echo "Non installée")"
echo ""
echo "Redis:"
echo "- Conteneur: $(docker ps | grep -q "ecoguard-redis" && echo "En cours d'exécution" || echo "Arrêté")"
echo "- Connexion: $(redis-cli ping | grep -q "PONG" && echo "Réussie" || echo "Échouée")"
echo ""
echo "Interfaces Web:"
echo "- pgAdmin: $(curl -s -o /dev/null -w "%{http_code}" http://localhost:5050 | grep -q "200\|302" && echo "Accessible" || echo "Non accessible")"
echo "- Redis Commander: $(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081 | grep -q "200\|302" && echo "Accessible" || echo "Non accessible")"
echo ""

# Conclusion
if docker ps | grep -q "ecoguard-postgres" && docker ps | grep -q "ecoguard-redis" && \
   PGPASSWORD=password psql -h localhost -U postgres -d ecoguard -c "SELECT 1;" &> /dev/null && \
   redis-cli ping | grep -q "PONG"; then
    success "PostgreSQL et Redis sont correctement installés et configurés!"
    exit 0
else
    error "Des problèmes ont été détectés avec PostgreSQL et/ou Redis. Veuillez consulter les messages ci-dessus."
    exit 1
fi
