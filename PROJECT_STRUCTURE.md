# Structure Complète du Projet EcoGuard

Ce document détaille la structure complète du projet EcoGuard, intégrant les trois composants principaux : frontend, backend et application mobile.

## Vue d'ensemble

```
ecoguard_unified/
├── frontend/           # Portail web de tracking
├── backend/            # API RESTful Spring Boot
└── mobile/             # Application Android
```

## 1. Frontend (ecoguard_tracking_portal)

```
frontend/
├── src/
│   ├── js/
│   │   ├── core/
│   │   │   ├── ApiClient.js          # Gestionnaire API centralisé
│   │   │   ├── StateManager.js       # Gestion d'état global
│   │   │   ├── EventBus.js           # Système d'événements
│   │   │   └── AuthManager.js        # Gestion authentification JWT
│   │   ├── components/
│   │   │   ├── Map/
│   │   │   │   ├── LeafletMap.js     # Intégration Leaflet
│   │   │   │   └── MarkerManager.js  # Gestion marqueurs dynamiques
│   │   │   ├── Auth/
│   │   │   │   ├── LoginForm.js      # Formulaire connexion
│   │   │   │   └── TokenHandler.js   # Gestion tokens JWT
│   │   │   └── Dashboard/
│   │   │       ├── DataGrid.js       # Affichage données tabulaires
│   │   │       └── StatsCards.js     # Cartes statistiques
│   │   ├── services/
│   │   │   ├── TrackingService.js    # Service de tracking
│   │   │   ├── ReportService.js      # Service rapports
│   │   │   └── UserService.js        # Service utilisateurs
│   │   └── utils/
│   │       ├── validators.js         # Validations formulaires
│   │       ├── formatters.js         # Formatage données
│   │       └── constants.js          # Constantes application
│   ├── css/
│   │   ├── main.css                  # Styles principaux
│   │   ├── components.css            # Styles composants
│   │   └── responsive.css            # Styles responsifs
│   ├── assets/
│   │   ├── images/
│   │   └── icons/
│   └── index.html                    # Point d'entrée principal
├── package.json                      # Dépendances et scripts
└── README.md                         # Documentation
```

## 2. Backend (ecoguard-backend)

```
backend/
├── gateway-service/                  # API Gateway (Port 8080)
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/ecoguard/gateway/
│   │       │       ├── config/
│   │       │       └── filter/
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
├── auth-service/                     # Service d'authentification (Port 8081)
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/ecoguard/auth/
│   │       │       ├── config/
│   │       │       ├── controller/
│   │       │       ├── dto/
│   │       │       ├── entity/
│   │       │       ├── repository/
│   │       │       ├── security/
│   │       │       └── service/
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
├── tracking-service/                 # Service de tracking (Port 8082)
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/ecoguard/tracking/
│   │       │       ├── controller/
│   │       │       ├── dto/
│   │       │       ├── entity/
│   │       │       ├── repository/
│   │       │       └── service/
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
├── report-service/                   # Service de rapports (Port 8083)
├── notification-service/             # Service de notifications (Port 8084)
├── file-service/                     # Service de gestion fichiers (Port 8085)
├── common/                           # Librairies communes
│   ├── security-common/
│   ├── database-common/
│   └── utils-common/
├── pom.xml                           # POM parent
└── README.md                         # Documentation
```

## 3. Application Mobile (ecoguard_v2_proactive)

```
mobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/ecoguard/mobile/
│   │   │   │       ├── data/
│   │   │   │       │   ├── local/
│   │   │   │       │   │   ├── database/
│   │   │   │       │   │   │   ├── dao/
│   │   │   │       │   │   │   └── entities/
│   │   │   │       │   │   └── preferences/
│   │   │   │       │   ├── remote/
│   │   │   │       │   │   ├── api/
│   │   │   │       │   │   ├── dto/
│   │   │   │       │   │   └── interceptors/
│   │   │   │       │   └── repository/
│   │   │   │       ├── domain/
│   │   │   │       │   ├── model/
│   │   │   │       │   ├── usecase/
│   │   │   │       │   └── repository/
│   │   │   │       ├── presentation/
│   │   │   │       │   ├── ui/
│   │   │   │       │   │   ├── auth/
│   │   │   │       │   │   ├── main/
│   │   │   │       │   │   ├── tracking/
│   │   │   │       │   │   └── profile/
│   │   │   │       │   └── viewmodel/
│   │   │   │       ├── di/
│   │   │   │       ├── service/
│   │   │   │       │   ├── ProactiveBroadcastWorker.kt
│   │   │   │       │   ├── OptimizedAudioEngine_V2.kt
│   │   │   │       │   ├── StolenDeviceAlertReceiver.kt
│   │   │   │       │   └── AnonymousReportWorker.kt
│   │   │   │       └── utils/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle
├── build.gradle
└── README.md                         # Documentation
```

## Intégration des Composants

### Communication Frontend-Backend
- Le frontend communique avec le backend via l'API REST
- L'authentification se fait via JWT
- Les données sont échangées au format JSON

### Communication Mobile-Backend
- L'application mobile communique avec le backend via l'API REST
- Les rapports anonymes sont envoyés via des endpoints spécifiques
- Les notifications push sont reçues via Firebase Cloud Messaging

### Flux de Données
1. L'application mobile émet sa signature acoustique en continu
2. D'autres appareils détectent cette signature et l'enregistrent localement
3. En cas de vol, l'utilisateur déclare son appareil volé via le portail web
4. Le backend envoie une alerte via FCM à tous les appareils
5. Les appareils ayant détecté l'appareil volé envoient des rapports anonymes
6. Le backend agrège ces rapports et les affiche sur le portail web

## Base de Données

### Schéma PostgreSQL
- Tables utilisateurs et authentification
- Tables appareils et partitions
- Tables points de tracking et observations
- Tables rapports et notifications

### Base de Données Locale (Room)
- Table des points de tracking
- Table des observations locales
- Table des paramètres de partition
- Table des rapports en attente d'envoi
