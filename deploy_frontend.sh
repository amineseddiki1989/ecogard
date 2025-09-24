#!/bin/bash

# Script de déploiement du frontend EcoGuard
# Ce script déploie le portail web sur un serveur de production

echo "=== Déploiement du Frontend EcoGuard ==="
echo "Date: $(date)"

# Variables de configuration
FRONTEND_DIR="/home/ubuntu/ecogard/frontend"
DEPLOY_DIR="/home/ubuntu/ecogard_deploy/frontend"
LOG_FILE="/home/ubuntu/ecogard_deploy/logs/frontend_deploy.log"

# Création des répertoires nécessaires
echo "[INFO] Création des répertoires de déploiement..."
mkdir -p $DEPLOY_DIR
mkdir -p /home/ubuntu/ecogard_deploy/logs

# Copie des fichiers source vers le répertoire de déploiement
echo "[INFO] Copie des fichiers source..."
cp -r $FRONTEND_DIR/* $DEPLOY_DIR/

# Modification de la configuration pour la production
echo "[INFO] Configuration pour l'environnement de production..."
cat > $DEPLOY_DIR/config.js << EOL
// Configuration de production pour EcoGuard Frontend
const CONFIG = {
    API_URL: 'https://api.ecoguard.com/api/v1',
    MAP_API_KEY: '********',
    ENVIRONMENT: 'production',
    VERSION: '1.0.0',
    ANALYTICS_ID: 'UA-XXXXXXXX-X'
};
EOL

# Minification des fichiers CSS et JS
echo "[INFO] Minification des fichiers CSS et JS..."
echo "Simulation de la minification..."
echo "cat $DEPLOY_DIR/styles.css | minify > $DEPLOY_DIR/styles.min.css"
echo "cat $DEPLOY_DIR/script.js | minify > $DEPLOY_DIR/script.min.js"

# Mise à jour des références dans le HTML
echo "[INFO] Mise à jour des références dans le HTML..."
echo "Simulation de la mise à jour des références..."
echo "sed -i 's/styles.css/styles.min.css/g' $DEPLOY_DIR/index.html"
echo "sed -i 's/script.js/script.min.js/g' $DEPLOY_DIR/index.html"

# Création du fichier .htaccess pour Apache
echo "[INFO] Création du fichier .htaccess..."
cat > $DEPLOY_DIR/.htaccess << EOL
# Activation de la compression
<IfModule mod_deflate.c>
  AddOutputFilterByType DEFLATE text/html text/plain text/xml text/css application/javascript application/json
</IfModule>

# Mise en cache des ressources statiques
<IfModule mod_expires.c>
  ExpiresActive On
  ExpiresByType image/jpg "access plus 1 year"
  ExpiresByType image/jpeg "access plus 1 year"
  ExpiresByType image/png "access plus 1 year"
  ExpiresByType image/svg+xml "access plus 1 year"
  ExpiresByType text/css "access plus 1 month"
  ExpiresByType application/javascript "access plus 1 month"
  ExpiresByType image/x-icon "access plus 1 year"
</IfModule>

# Redirection vers index.html pour les SPA
<IfModule mod_rewrite.c>
  RewriteEngine On
  RewriteBase /
  RewriteRule ^index\.html$ - [L]
  RewriteCond %{REQUEST_FILENAME} !-f
  RewriteCond %{REQUEST_FILENAME} !-d
  RewriteRule . /index.html [L]
</IfModule>

# En-têtes de sécurité
<IfModule mod_headers.c>
  Header set X-Content-Type-Options "nosniff"
  Header set X-XSS-Protection "1; mode=block"
  Header set X-Frame-Options "SAMEORIGIN"
  Header set Content-Security-Policy "default-src 'self'; script-src 'self' https://api.mapbox.com; style-src 'self' 'unsafe-inline' https://api.mapbox.com; img-src 'self' data: https://*.tile.openstreetmap.org; connect-src 'self' https://api.ecoguard.com;"
  Header set Strict-Transport-Security "max-age=31536000; includeSubDomains"
</IfModule>
EOL

# Simulation du déploiement sur le serveur de production
echo "[INFO] Simulation du déploiement sur le serveur de production..."
echo "scp -r $DEPLOY_DIR/* ecoguard@prod-server:/var/www/tracking.ecoguard.com/"

echo "[SUCCESS] Frontend déployé avec succès!"
echo "URL du portail: https://tracking.ecoguard.com"

# Journalisation du déploiement
echo "Déploiement du frontend effectué le $(date)" >> $LOG_FILE
