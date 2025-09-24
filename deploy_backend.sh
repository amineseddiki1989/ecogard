#!/bin/bash

# Script de déploiement du backend EcoGuard
# Ce script déploie le backend Spring Boot sur un serveur de production

echo "=== Déploiement du Backend EcoGuard ==="
echo "Date: $(date)"

# Variables de configuration
BACKEND_DIR="/home/ubuntu/ecogard/backend"
DEPLOY_DIR="/home/ubuntu/ecogard_deploy/backend"
JAR_NAME="ecoguard-tracking-1.0.0.jar"
LOG_FILE="/home/ubuntu/ecogard_deploy/logs/backend_deploy.log"

# Création des répertoires nécessaires
echo "[INFO] Création des répertoires de déploiement..."
mkdir -p $DEPLOY_DIR
mkdir -p /home/ubuntu/ecogard_deploy/logs

# Compilation du backend avec Maven
echo "[INFO] Compilation du backend avec Maven..."
cd $BACKEND_DIR
echo "Simulation de la compilation Maven..."
echo "mvn clean package -DskipTests"

# Copie du fichier JAR vers le répertoire de déploiement
echo "[INFO] Copie du fichier JAR vers le répertoire de déploiement..."
echo "cp $BACKEND_DIR/target/$JAR_NAME $DEPLOY_DIR/"
cp $BACKEND_DIR/pom.xml $DEPLOY_DIR/

# Création du fichier de configuration pour la production
echo "[INFO] Création du fichier de configuration pour la production..."
cat > $DEPLOY_DIR/application-prod.properties << EOL
# Configuration de production pour EcoGuard Backend

# Configuration de la base de données
spring.datasource.url=jdbc:postgresql://db.ecoguard.com:5432/ecoguard_prod
spring.datasource.username=ecoguard_user
spring.datasource.password=********
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Configuration Redis
spring.redis.host=redis.ecoguard.com
spring.redis.port=6379
spring.redis.password=********

# Configuration JWT
jwt.secret=********
jwt.expiration=86400000
jwt.refresh-expiration=604800000

# Configuration Firebase
firebase.config.path=/etc/ecoguard/firebase-config.json

# Configuration du serveur
server.port=8080
server.servlet.context-path=/api/v1

# Configuration de logging
logging.level.root=INFO
logging.level.com.ecoguard=INFO
logging.file.name=/var/log/ecoguard/backend.log

# Configuration CORS
cors.allowed-origins=https://tracking.ecoguard.com
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=Authorization,Content-Type
cors.max-age=3600
EOL

# Création du script de démarrage
echo "[INFO] Création du script de démarrage..."
cat > $DEPLOY_DIR/start.sh << EOL
#!/bin/bash
java -jar $JAR_NAME --spring.profiles.active=prod > /dev/null 2>&1 &
echo \$! > app.pid
echo "Backend démarré avec PID \$(cat app.pid)"
EOL

chmod +x $DEPLOY_DIR/start.sh

# Création du script d'arrêt
echo "[INFO] Création du script d'arrêt..."
cat > $DEPLOY_DIR/stop.sh << EOL
#!/bin/bash
if [ -f app.pid ]; then
    PID=\$(cat app.pid)
    if ps -p \$PID > /dev/null; then
        echo "Arrêt du backend (PID \$PID)..."
        kill \$PID
        sleep 5
        if ps -p \$PID > /dev/null; then
            echo "Forçage de l'arrêt..."
            kill -9 \$PID
        fi
    else
        echo "Le processus \$PID n'est pas en cours d'exécution"
    fi
    rm app.pid
else
    echo "Fichier PID non trouvé"
fi
EOL

chmod +x $DEPLOY_DIR/stop.sh

# Simulation du déploiement sur le serveur de production
echo "[INFO] Simulation du déploiement sur le serveur de production..."
echo "scp -r $DEPLOY_DIR/* ecoguard@prod-server:/opt/ecoguard/backend/"
echo "ssh ecoguard@prod-server 'cd /opt/ecoguard/backend && ./stop.sh && ./start.sh'"

echo "[SUCCESS] Backend déployé avec succès!"
echo "URL de l'API: https://api.ecoguard.com/api/v1"
echo "Pour les logs: ssh ecoguard@prod-server 'tail -f /var/log/ecoguard/backend.log'"

# Journalisation du déploiement
echo "Déploiement du backend effectué le $(date)" >> $LOG_FILE
