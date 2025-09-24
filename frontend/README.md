# EcoGuard Tracking Portal - Prototype

Ce prototype présente une interface web permettant aux utilisateurs de tracker leurs appareils mobiles après les avoir déclarés volés, en s'appuyant sur l'architecture EcoGuard V2 avec sa stratégie proactive.

## Fonctionnalités implémentées

### 1. Tableau de bord
- Vue d'ensemble des appareils enregistrés
- Statistiques sur les observations récentes
- Carte avec la dernière position connue
- Alertes pour les appareils volés

### 2. Gestion des appareils
- Liste des appareils enregistrés
- Statut de chaque appareil (actif, volé)
- Actions rapides (signaler un vol, voir les détails)

### 3. Déclaration de vol
- Formulaire de déclaration avec sélection d'appareil
- Champs pour date, lieu et circonstances du vol
- Option pour indiquer si une plainte a été déposée
- Confirmation et activation du tracking

### 4. Tracking d'appareil
- Carte interactive avec historique des positions
- Filtres temporels pour les observations
- Statistiques détaillées (confiance, nombre d'observations)
- Liste chronologique des observations
- Actions recommandées (verrouiller, effacer, faire sonner)

### 5. Paramètres
- Informations du compte
- Préférences de notification
- Sécurité et authentification

## Architecture technique

Ce prototype est construit avec les technologies suivantes :
- **Frontend** : HTML5, CSS3, JavaScript
- **Frameworks** : Bootstrap 5 pour l'interface utilisateur
- **Cartographie** : Leaflet pour les cartes interactives
- **Authentification** : Simulée pour le prototype

## Intégration avec EcoGuard V2

L'interface s'intègre avec l'architecture EcoGuard V2 de la manière suivante :
- Utilisation des données collectées par le système d'écoute passive
- Exploitation des rapports anonymes générés par AnonymousReportWorker
- Déclenchement des alertes communautaires via le WebPortalAPI

## Prochaines étapes

Pour transformer ce prototype en une solution complète, les étapes suivantes seraient nécessaires :
1. Développer le backend avec une API RESTful
2. Implémenter l'authentification sécurisée
3. Créer la base de données pour stocker les observations
4. Développer les intégrations avec les services externes (police, assurances)
5. Ajouter des fonctionnalités avancées (analyse prédictive, zones de chaleur)

## Comment tester

1. Ouvrir le fichier `index.html` dans un navigateur web
2. Explorer les différentes sections via le menu latéral
3. Tester le formulaire de déclaration de vol
4. Visualiser la carte de tracking avec les données simulées

---

*Ce prototype a été développé pour illustrer le concept d'interface de tracking pour EcoGuard V2.*
