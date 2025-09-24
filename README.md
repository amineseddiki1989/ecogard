# EcoGuard - Système Complet

Ce dépôt contient le code source complet du système EcoGuard, comprenant trois composants principaux :

1. **Frontend Web Portal** - Interface utilisateur web pour le tracking des appareils
2. **Backend API** - Services RESTful pour la gestion des données et l'authentification
3. **Application Mobile** - Application Android pour la détection et le signalement des appareils

## Structure du Projet

```
ecoguard_unified/
├── frontend/           # Portail web de tracking
├── backend/            # API RESTful Spring Boot
└── mobile/             # Application Android
```

## Prérequis

- Java 17+
- Node.js 16+
- Android Studio Arctic Fox+
- PostgreSQL 13+ avec extension PostGIS
- Redis 6+

## Installation et Configuration

Consultez les README spécifiques dans chaque dossier de composant pour les instructions détaillées d'installation et de configuration.

## Intégration

Les trois composants sont conçus pour fonctionner ensemble de manière transparente :

- L'application mobile envoie des données au backend via l'API REST
- Le frontend web récupère et affiche les données du backend
- Le système de notifications push permet la communication en temps réel

## Licence

Propriétaire - Tous droits réservés
