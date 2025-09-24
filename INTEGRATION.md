# Guide d'Intégration des Composants EcoGuard

Ce document explique comment intégrer les trois composants du système EcoGuard (frontend, backend et application mobile) pour créer une solution complète et fonctionnelle.

## Prérequis

Avant de commencer l'intégration, assurez-vous d'avoir installé et configuré :

1. **Environnement de développement**
   - Java 17+
   - Node.js 16+
   - Android Studio Arctic Fox+
   - Maven 3.8+
   - Git

2. **Services externes**
   - PostgreSQL 13+ avec extension PostGIS
   - Redis 6+
   - Firebase (pour les notifications push)

## Étape 1 : Configuration du Backend

### 1.1 Configuration de la Base de Données

```bash
# Créer la base de données PostgreSQL
sudo -u postgres createdb ecoguard
sudo -u postgres psql -d ecoguard -c "CREATE EXTENSION postgis;"

# Exécuter les scripts de migration
cd backend
mvn flyway:migrate
```

### 1.2 Configuration des Services

Modifiez les fichiers `application.yml` de chaque service pour configurer :

- **Connexion à la base de données**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecoguard
    username: postgres
    password: votre_mot_de_passe
```

- **Connexion à Redis**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

- **Configuration JWT**
```yaml
jwt:
  secret: votre_clé_secrète_très_longue_et_aléatoire
  expirationMs: 86400000  # 24 heures
  refreshExpirationMs: 604800000  # 7 jours
```

- **Configuration Firebase**
Placez votre fichier de configuration Firebase (`firebase-service-account.json`) dans `notification-service/src/main/resources/`.

### 1.3 Démarrage des Services

```bash
# Compiler tous les services
cd backend
mvn clean install

# Démarrer les services dans l'ordre
cd gateway-service
mvn spring-boot:run

# Dans de nouveaux terminaux, démarrer les autres services
cd auth-service
mvn spring-boot:run

cd tracking-service
mvn spring-boot:run

# etc.
```

## Étape 2 : Configuration du Frontend

### 2.1 Installation des Dépendances

```bash
cd frontend
npm install
```

### 2.2 Configuration de l'API

Modifiez le fichier `src/js/core/ApiClient.js` pour pointer vers votre backend :

```javascript
constructor() {
    // Pour le développement local
    this.baseURL = 'http://localhost:8080/api';
    
    // Pour la production
    // this.baseURL = 'https://api.ecoguard.com/api';
}
```

### 2.3 Démarrage du Frontend

```bash
cd frontend
npm run dev
```

Le portail web sera accessible à l'adresse `http://localhost:3000`.

## Étape 3 : Configuration de l'Application Mobile

### 3.1 Configuration de l'API

Modifiez le fichier `mobile/app/src/main/java/com/ecoguard/mobile/data/remote/api/WebPortalAPI.kt` :

```kotlin
companion object {
    // Pour l'émulateur Android
    const val BASE_URL = "http://10.0.2.2:8080/api/"
    
    // Pour un appareil physique en développement
    // const val BASE_URL = "http://192.168.1.X:8080/api/"
    
    // Pour la production
    // const val BASE_URL = "https://api.ecoguard.com/api/"
}
```

### 3.2 Configuration Firebase

1. Téléchargez le fichier `google-services.json` depuis la console Firebase
2. Placez-le dans le dossier `mobile/app/`

### 3.3 Compilation et Installation

Ouvrez le projet dans Android Studio, puis :

1. Synchronisez avec Gradle
2. Compilez l'application
3. Installez-la sur un émulateur ou un appareil physique

## Étape 4 : Test de l'Intégration

### 4.1 Création d'un Compte Utilisateur

1. Accédez au portail web (`http://localhost:3000`)
2. Créez un nouveau compte utilisateur
3. Connectez-vous avec ce compte

### 4.2 Enregistrement d'un Appareil

1. Dans le portail web, accédez à "Gestion des appareils"
2. Cliquez sur "Ajouter un appareil"
3. Notez l'identifiant de partition généré

### 4.3 Configuration de l'Application Mobile

1. Ouvrez l'application mobile
2. Connectez-vous avec le même compte
3. Configurez l'appareil avec l'identifiant de partition

### 4.4 Test du Flux Complet

1. **Émission proactive** : Vérifiez que l'application mobile émet sa signature acoustique
2. **Simulation de vol** : Dans le portail web, déclarez l'appareil comme volé
3. **Réception d'alerte** : Vérifiez que l'application mobile reçoit l'alerte FCM
4. **Rapport anonyme** : Simulez la détection de l'appareil volé
5. **Visualisation** : Vérifiez que les observations apparaissent sur le portail web

## Étape 5 : Déploiement en Production

### 5.1 Backend

1. Configurez un serveur avec Java 17+
2. Déployez chaque service en tant que conteneur Docker ou JAR exécutable
3. Configurez un équilibreur de charge pour le routage vers les services
4. Mettez en place un certificat SSL pour HTTPS

### 5.2 Frontend

1. Construisez la version de production : `npm run build`
2. Déployez les fichiers statiques sur un serveur web (Nginx, Apache)
3. Configurez HTTPS et la mise en cache

### 5.3 Application Mobile

1. Signez l'APK avec votre clé de production
2. Déployez sur Google Play Store

## Dépannage

### Problèmes de Connexion API

- Vérifiez que tous les services backend sont en cours d'exécution
- Vérifiez les paramètres CORS dans l'API Gateway
- Assurez-vous que les URLs sont correctement configurées

### Problèmes d'Authentification

- Vérifiez la validité des tokens JWT
- Assurez-vous que les clés secrètes sont cohérentes entre les services
- Vérifiez les logs du service d'authentification

### Problèmes de Notifications Push

- Vérifiez la configuration Firebase
- Assurez-vous que l'appareil est enregistré pour les notifications
- Vérifiez les logs du service de notifications
