# Plan de Tests d'Intégration EcoGuard

Ce document décrit le plan de tests d'intégration pour valider le fonctionnement du système EcoGuard complet.

## 1. Objectifs des Tests

- Valider l'intégration entre les trois composants principaux (frontend, backend, mobile)
- Vérifier le bon fonctionnement des flux de données entre les composants
- S'assurer que les fonctionnalités principales répondent aux exigences

## 2. Environnement de Test

### 2.1 Configuration Requise

- **Backend** : 
  - Java 17
  - PostgreSQL 13+ avec extension PostGIS
  - Redis 6+

- **Frontend** :
  - Node.js 16+
  - Navigateur moderne (Chrome, Firefox)

- **Mobile** :
  - Android Studio Arctic Fox+
  - Émulateur Android ou appareil physique

### 2.2 Configuration de l'Environnement

```bash
# Configuration de la base de données
docker run --name postgres-ecoguard -e POSTGRES_PASSWORD=password -e POSTGRES_DB=ecoguard -d -p 5432:5432 postgres:13
docker exec -it postgres-ecoguard psql -U postgres -d ecoguard -c "CREATE EXTENSION postgis;"

# Configuration de Redis
docker run --name redis-ecoguard -d -p 6379:6379 redis:6

# Configuration du backend
cd backend
mvn clean install

# Configuration du frontend
cd frontend
npm install

# Configuration de l'application mobile
# (Via Android Studio)
```

## 3. Scénarios de Test

### 3.1 Test d'Authentification

| ID | Description | Étapes | Résultat Attendu |
|----|-------------|--------|-----------------|
| AUTH-01 | Inscription utilisateur | 1. Accéder au portail web<br>2. Cliquer sur "S'inscrire"<br>3. Remplir le formulaire<br>4. Soumettre | Compte créé avec succès |
| AUTH-02 | Connexion utilisateur | 1. Accéder au portail web<br>2. Entrer identifiants<br>3. Se connecter | Connexion réussie, accès au tableau de bord |
| AUTH-03 | Rafraîchissement token | 1. Se connecter<br>2. Attendre expiration token<br>3. Effectuer une action | Token rafraîchi automatiquement |
| AUTH-04 | Déconnexion | 1. Se connecter<br>2. Cliquer sur "Déconnexion" | Redirection vers page de connexion |

### 3.2 Test d'Enregistrement d'Appareil

| ID | Description | Étapes | Résultat Attendu |
|----|-------------|--------|-----------------|
| DEV-01 | Ajout d'appareil | 1. Se connecter au portail<br>2. Accéder à "Gestion des appareils"<br>3. Cliquer sur "Ajouter"<br>4. Remplir le formulaire | Appareil ajouté avec succès |
| DEV-02 | Configuration mobile | 1. Ouvrir l'application mobile<br>2. Se connecter<br>3. Configurer avec ID de partition | Configuration réussie |
| DEV-03 | Vérification synchronisation | 1. Ajouter appareil sur le portail<br>2. Vérifier dans l'application mobile | Appareil visible dans l'application |

### 3.3 Test de Déclaration de Vol

| ID | Description | Étapes | Résultat Attendu |
|----|-------------|--------|-----------------|
| THEFT-01 | Déclaration de vol | 1. Se connecter au portail<br>2. Accéder à "Gestion des appareils"<br>3. Sélectionner un appareil<br>4. Cliquer sur "Déclarer volé"<br>5. Remplir le formulaire | Déclaration créée avec succès |
| THEFT-02 | Réception alerte | 1. Déclarer un appareil volé<br>2. Vérifier l'application mobile | Notification d'alerte reçue |
| THEFT-03 | Annulation déclaration | 1. Accéder aux déclarations<br>2. Sélectionner une déclaration<br>3. Cliquer sur "Annuler" | Déclaration annulée avec succès |

### 3.4 Test de Tracking et Observations

| ID | Description | Étapes | Résultat Attendu |
|----|-------------|--------|-----------------|
| TRACK-01 | Émission signature | 1. Configurer l'application mobile<br>2. Vérifier les logs | Signature émise périodiquement |
| TRACK-02 | Détection signature | 1. Configurer deux appareils<br>2. Placer à proximité | Signature détectée et enregistrée |
| TRACK-03 | Rapport anonyme | 1. Déclarer un appareil volé<br>2. Simuler une détection<br>3. Vérifier les logs | Rapport anonyme envoyé |
| TRACK-04 | Visualisation tracking | 1. Déclarer un appareil volé<br>2. Générer des observations<br>3. Accéder au portail web | Observations visibles sur la carte |

### 3.5 Test de Notifications

| ID | Description | Étapes | Résultat Attendu |
|----|-------------|--------|-----------------|
| NOTIF-01 | Notification vol | 1. Déclarer un appareil volé<br>2. Vérifier les notifications | Notification reçue |
| NOTIF-02 | Notification observation | 1. Simuler une observation<br>2. Vérifier les notifications | Notification reçue |
| NOTIF-03 | Préférences notifications | 1. Accéder aux paramètres<br>2. Modifier préférences<br>3. Générer notification | Notification selon préférences |

## 4. Exécution des Tests

### 4.1 Tests Backend

```bash
# Exécuter les tests unitaires
cd backend
mvn test

# Exécuter les tests d'intégration
mvn verify
```

### 4.2 Tests Frontend

```bash
# Exécuter les tests unitaires
cd frontend
npm test

# Exécuter les tests end-to-end
npm run test:e2e
```

### 4.3 Tests Mobile

```bash
# Exécuter via Android Studio
# Tests unitaires : ./gradlew test
# Tests instrumentés : ./gradlew connectedAndroidTest
```

### 4.4 Tests d'Intégration Complets

Pour les tests d'intégration complets, suivre les scénarios décrits dans la section 3 manuellement ou via des scripts d'automatisation.

## 5. Rapports de Test

Les résultats des tests seront documentés dans des rapports de test individuels pour chaque scénario, incluant :

- ID du test
- Date d'exécution
- Résultat (Succès/Échec)
- Captures d'écran ou logs pertinents
- Notes et observations

## 6. Critères de Réussite

Les tests d'intégration seront considérés comme réussis si :

- Tous les scénarios de test sont exécutés avec succès
- Aucun bug critique ou bloquant n'est identifié
- Les performances sont acceptables (temps de réponse < 2s)
- La synchronisation des données entre les composants est fiable
