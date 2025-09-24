# Guide d'Utilisation de l'Environnement de Test EcoGuard

Ce guide explique comment configurer, utiliser et maintenir l'environnement de test pour le système EcoGuard.

## 1. Configuration de l'Environnement

### 1.1 Installation Automatique

Pour configurer automatiquement l'environnement de test avec toutes les dépendances nécessaires, exécutez le script d'installation :

```bash
cd /home/ubuntu/ecogard/integration_tests
./setup_test_environment.sh
```

Ce script installe et configure :
- PostgreSQL 13 avec l'extension PostGIS
- Redis 6
- pgAdmin (interface web pour PostgreSQL)
- Redis Commander (interface web pour Redis)
- Java 17
- Maven
- Node.js 16
- Outils Android SDK (si disponibles)

### 1.2 Installation Manuelle

Si vous préférez une installation manuelle, suivez ces étapes :

1. **PostgreSQL avec PostGIS**
   ```bash
   sudo apt-get update
   sudo apt-get install -y postgresql postgresql-contrib postgis
   sudo -u postgres createdb ecoguard
   sudo -u postgres psql -d ecoguard -c "CREATE EXTENSION postgis;"
   ```

2. **Redis**
   ```bash
   sudo apt-get install -y redis-server
   sudo systemctl enable redis-server
   sudo systemctl start redis-server
   ```

3. **Java et Maven**
   ```bash
   sudo apt-get install -y openjdk-17-jdk maven
   ```

4. **Node.js**
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_16.x | sudo -E bash -
   sudo apt-get install -y nodejs
   ```

## 2. Accès aux Services

### 2.1 PostgreSQL

- **Hôte** : localhost
- **Port** : 5432
- **Utilisateur** : postgres
- **Mot de passe** : password
- **Base de données** : ecoguard

Pour se connecter via la ligne de commande :
```bash
PGPASSWORD=password psql -h localhost -U postgres -d ecoguard
```

### 2.2 Redis

- **Hôte** : localhost
- **Port** : 6379

Pour se connecter via la ligne de commande :
```bash
redis-cli
```

### 2.3 Interfaces Web

- **pgAdmin** : http://localhost:5050
  - Email : admin@ecoguard.com
  - Mot de passe : admin

- **Redis Commander** : http://localhost:8081

## 3. Exécution des Tests d'Intégration

### 3.1 Tests Complets

Pour exécuter tous les tests d'intégration :

```bash
cd /home/ubuntu/ecogard/integration_tests
./run_integration_tests.sh
```

### 3.2 Tests Individuels

Pour exécuter les tests d'un composant spécifique :

- **Backend** :
  ```bash
  ./backend_tests.sh
  ```

- **Frontend** :
  ```bash
  ./frontend_tests.sh
  ```

- **Mobile** :
  ```bash
  ./mobile_tests.sh
  ```

## 4. Structure de la Base de Données

La base de données PostgreSQL est initialisée avec les tables suivantes :

- **users** : Informations sur les utilisateurs
- **devices** : Appareils enregistrés
- **tracking_points** : Points de localisation des appareils
- **theft_reports** : Déclarations de vol
- **observations** : Observations d'appareils volés
- **notifications** : Notifications système

Un utilisateur de test est créé automatiquement :
- **Nom d'utilisateur** : testuser
- **Email** : test@example.com
- **Mot de passe** : password

## 5. Maintenance de l'Environnement

### 5.1 Gestion des Conteneurs Docker

- **Démarrer les services** :
  ```bash
  cd /home/ubuntu/ecogard
  docker-compose up -d
  ```

- **Arrêter les services** :
  ```bash
  cd /home/ubuntu/ecogard
  docker-compose down
  ```

- **Voir les logs** :
  ```bash
  docker-compose logs
  ```

### 5.2 Réinitialisation de la Base de Données

Pour réinitialiser la base de données à son état initial :

```bash
cd /home/ubuntu/ecogard
docker-compose down
rm -rf docker_data/postgres/*
docker-compose up -d
```

### 5.3 Sauvegarde et Restauration

- **Sauvegarde** :
  ```bash
  docker exec ecoguard-postgres pg_dump -U postgres ecoguard > backup.sql
  ```

- **Restauration** :
  ```bash
  cat backup.sql | docker exec -i ecoguard-postgres psql -U postgres -d ecoguard
  ```

## 6. Dépannage

### 6.1 Problèmes de Connexion à PostgreSQL

Si vous ne pouvez pas vous connecter à PostgreSQL :

1. Vérifiez que le conteneur est en cours d'exécution :
   ```bash
   docker ps | grep postgres
   ```

2. Vérifiez les logs :
   ```bash
   docker logs ecoguard-postgres
   ```

3. Redémarrez le conteneur :
   ```bash
   docker restart ecoguard-postgres
   ```

### 6.2 Problèmes de Connexion à Redis

Si vous ne pouvez pas vous connecter à Redis :

1. Vérifiez que le conteneur est en cours d'exécution :
   ```bash
   docker ps | grep redis
   ```

2. Vérifiez les logs :
   ```bash
   docker logs ecoguard-redis
   ```

3. Redémarrez le conteneur :
   ```bash
   docker restart ecoguard-redis
   ```

### 6.3 Problèmes avec les Tests

Si les tests échouent :

1. Vérifiez les rapports de test dans le dossier `reports/`
2. Vérifiez les logs dans le dossier `logs/`
3. Assurez-vous que tous les services sont en cours d'exécution
4. Vérifiez que la base de données est correctement initialisée
