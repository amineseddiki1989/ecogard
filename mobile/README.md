# EcoGuard Mobile - Application Android

Ce dossier contient le code source de l'application mobile EcoGuard, une application Android développée en Kotlin qui implémente la stratégie proactive de détection et de signalement des appareils volés.

## Architecture MVVM

L'application suit l'architecture MVVM (Model-View-ViewModel) avec une approche MVI (Model-View-Intent) pour la gestion d'état :

```
app/
├── src/main/java/com/ecoguard/mobile/
│   ├── data/                  # Couche données
│   │   ├── local/             # Sources de données locales
│   │   ├── remote/            # Sources de données distantes
│   │   └── repository/        # Implémentations des repositories
│   ├── domain/                # Couche domaine
│   │   ├── model/             # Modèles métier
│   │   ├── usecase/           # Cas d'utilisation
│   │   └── repository/        # Interfaces repository
│   ├── presentation/          # Couche présentation
│   │   ├── ui/                # Fragments et activités
│   │   └── viewmodel/         # ViewModels
│   ├── di/                    # Injection de dépendances
│   ├── service/               # Services Android
│   └── utils/                 # Utilitaires
```

## Prérequis

- Android Studio Arctic Fox+
- Kotlin 1.6+
- Gradle 7.0+
- Android SDK 31+ (Android 12)

## Installation

1. Ouvrir le projet dans Android Studio
2. Synchroniser avec Gradle
3. Exécuter sur un émulateur ou un appareil physique

## Configuration

Les principales configurations se trouvent dans :

- `build.gradle` : Dépendances et configuration de build
- `AndroidManifest.xml` : Permissions et déclaration des composants
- `data/remote/api/WebPortalAPI.kt` : Configuration de l'URL de l'API

## Fonctionnalités Principales

### Stratégie Proactive
- Émission continue de la signature acoustique
- Adaptation de la fréquence d'émission selon le niveau de batterie
- Écoute passive universelle pour détecter tous les appareils

### Gestion des Observations
- Stockage local des observations dans une base de données Room
- Synchronisation avec le backend lorsque la connectivité est disponible
- Filtrage des observations pour réduire les faux positifs

### Système de Rapports Anonymes
- Génération de rapports anonymes pour les appareils volés détectés
- Ajout d'observations fantômes pour protéger la vie privée
- Délais aléatoires pour éviter la corrélation

### Notifications et Alertes
- Réception des alertes via Firebase Cloud Messaging
- Vérification locale des observations correspondantes
- Génération de rapports conditionnels

## Intégration avec le Backend

L'application communique avec le backend via l'API REST définie dans `WebPortalAPI.kt`. Les principales intégrations sont :

- Authentification et gestion des tokens JWT
- Synchronisation des paramètres de partition
- Envoi des rapports anonymes
- Réception des alertes via FCM

## Services en Arrière-plan

- `ProactiveBroadcastWorker` : Émission proactive continue
- `OptimizedAudioEngine_V2` : Moteur audio pour l'émission et la détection
- `StolenDeviceAlertReceiver` : Réception des alertes FCM
- `AnonymousReportWorker` : Génération et envoi des rapports anonymes

## Sécurité

- Stockage sécurisé des paramètres sensibles
- Chiffrement des données locales
- Anonymisation des rapports
- Protection contre la corrélation temporelle et spatiale
