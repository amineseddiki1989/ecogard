# EcoGuard Tracking Portal - Backend API

Ce projet est le backend de l'interface web de tracking pour l'application EcoGuard V2. Il fournit une API RESTful pour gérer les appareils, les déclarations de vol, les observations et les notifications.

## Architecture

Le backend est construit avec Spring Boot et utilise les technologies suivantes :

- **Spring Security** avec JWT pour l'authentification et l'autorisation
- **Spring Data JPA** avec PostgreSQL et PostGIS pour le stockage des données
- **Redis** pour la mise en cache
- **Firebase Cloud Messaging** pour les notifications push
- **Swagger/OpenAPI** pour la documentation de l'API

## Structure du Projet

```
src/main/java/com/ecoguard/tracking/
├── config/             # Configuration Spring Boot
├── controller/         # Contrôleurs REST
├── dto/                # Objets de transfert de données
├── entity/             # Entités JPA
├── exception/          # Exceptions personnalisées
├── mapper/             # Mappers DTO <-> Entity
├── repository/         # Repositories JPA
├── security/           # Configuration de sécurité et JWT
├── service/            # Services métier
└── util/               # Classes utilitaires
```

## Fonctionnalités Principales

### Gestion des Utilisateurs
- Inscription et connexion
- Authentification JWT
- Gestion des profils utilisateurs

### Gestion des Appareils
- Enregistrement des appareils
- Suivi des appareils
- Mise à jour des statuts

### Déclarations de Vol
- Création de déclarations de vol
- Suivi des déclarations
- Résolution et annulation

### Observations
- Traitement des rapports anonymes
- Stockage des observations
- Statistiques et analyses

### Notifications
- Notifications en temps réel
- Alertes communautaires
- Gestion des préférences de notification

## API Endpoints

### Authentification
- `POST /auth/login` - Connexion utilisateur
- `POST /auth/register` - Inscription utilisateur
- `POST /auth/refresh` - Rafraîchissement du token

### Appareils
- `GET /devices` - Liste des appareils de l'utilisateur
- `GET /devices/{id}` - Détails d'un appareil
- `POST /devices` - Enregistrement d'un appareil
- `PUT /devices/{id}` - Mise à jour d'un appareil
- `PUT /devices/{id}/status` - Mise à jour du statut d'un appareil
- `DELETE /devices/{id}` - Suppression d'un appareil

### Déclarations de Vol
- `GET /theft-reports` - Liste des déclarations de vol de l'utilisateur
- `GET /theft-reports/{id}` - Détails d'une déclaration
- `POST /theft-reports` - Création d'une déclaration
- `PUT /theft-reports/{id}` - Mise à jour d'une déclaration
- `PUT /theft-reports/{id}/resolve` - Résolution d'une déclaration
- `PUT /theft-reports/{id}/cancel` - Annulation d'une déclaration

### Observations
- `GET /observations/device/{deviceId}` - Liste des observations d'un appareil
- `GET /observations/device/{deviceId}/range` - Observations dans une plage de temps
- `GET /observations/stats/device/{deviceId}` - Statistiques des observations
- `POST /anonymous-reports` - Traitement d'un rapport anonyme

### Notifications
- `GET /notifications` - Liste des notifications de l'utilisateur
- `GET /notifications/{id}` - Détails d'une notification
- `GET /notifications/count/unread` - Nombre de notifications non lues
- `PUT /notifications/{id}/read` - Marquer une notification comme lue
- `PUT /notifications/read/all` - Marquer toutes les notifications comme lues
- `DELETE /notifications/{id}` - Suppression d'une notification

## Configuration

La configuration se fait via le fichier `application.properties`. Les principales propriétés sont :

- `spring.datasource.*` - Configuration de la base de données PostgreSQL
- `spring.redis.*` - Configuration du cache Redis
- `jwt.*` - Configuration JWT
- `firebase.*` - Configuration Firebase
- `ecoguard.*` - Configuration spécifique à EcoGuard

## Démarrage

1. Assurez-vous d'avoir Java 11+ installé
2. Configurez PostgreSQL avec l'extension PostGIS
3. Configurez Redis
4. Configurez Firebase et placez le fichier de service dans les ressources
5. Exécutez `mvn spring-boot:run` pour démarrer l'application

## Documentation API

La documentation Swagger est disponible à l'URL `/swagger-ui.html` lorsque l'application est en cours d'exécution.
