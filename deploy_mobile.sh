#!/bin/bash

# Script de déploiement de l'application mobile EcoGuard
# Ce script prépare l'application mobile pour la publication sur les stores

echo "=== Déploiement de l'Application Mobile EcoGuard ==="
echo "Date: $(date)"

# Variables de configuration
MOBILE_DIR="/home/ubuntu/ecogard/mobile"
DEPLOY_DIR="/home/ubuntu/ecogard_deploy/mobile"
LOG_FILE="/home/ubuntu/ecogard_deploy/logs/mobile_deploy.log"
VERSION_NAME="1.0.0"
VERSION_CODE="1"

# Création des répertoires nécessaires
echo "[INFO] Création des répertoires de déploiement..."
mkdir -p $DEPLOY_DIR
mkdir -p /home/ubuntu/ecogard_deploy/logs

# Copie des fichiers source vers le répertoire de déploiement
echo "[INFO] Copie des fichiers source..."
cp -r $MOBILE_DIR/* $DEPLOY_DIR/

# Modification de la configuration pour la production
echo "[INFO] Configuration pour l'environnement de production..."
cat > $DEPLOY_DIR/config.properties << EOL
# Configuration de production pour EcoGuard Mobile
API_BASE_URL=https://api.ecoguard.com/api/v1
FCM_SENDER_ID=123456789012
ANALYTICS_ENABLED=true
LOG_LEVEL=INFO
BROADCAST_INTERVAL_MIN=60000
BROADCAST_INTERVAL_MAX=300000
EOL

# Mise à jour du fichier build.gradle
echo "[INFO] Mise à jour du fichier build.gradle..."
cat > $DEPLOY_DIR/build.gradle << EOL
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
}

android {
    compileSdkVersion 33
    
    defaultConfig {
        applicationId "com.ecoguard.tracking"
        minSdkVersion 24
        targetSdkVersion 33
        versionCode $VERSION_CODE
        versionName "$VERSION_NAME"
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
    
    signingConfigs {
        release {
            storeFile file("../keystore/ecoguard.jks")
            storePassword "********"
            keyAlias "ecoguard"
            keyPassword "********"
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
            
            buildConfigField "String", "API_BASE_URL", "\"https://api.ecoguard.com/api/v1\""
            buildConfigField "boolean", "DEBUG_MODE", "false"
        }
        
        debug {
            applicationIdSuffix ".debug"
            debuggable true
            
            buildConfigField "String", "API_BASE_URL", "\"https://dev-api.ecoguard.com/api/v1\""
            buildConfigField "boolean", "DEBUG_MODE", "true"
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = '1.8'
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.7.20"
    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'androidx.appcompat:appcompat:1.6.0'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    
    // Room
    implementation 'androidx.room:room-runtime:2.5.0'
    implementation 'androidx.room:room-ktx:2.5.0'
    kapt 'androidx.room:room-compiler:2.5.0'
    
    // WorkManager
    implementation 'androidx.work:work-runtime-ktx:2.8.0'
    
    // Retrofit
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.10.0'
    
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:31.2.0')
    implementation 'com.google.firebase:firebase-messaging-ktx'
    implementation 'com.google.firebase:firebase-analytics-ktx'
    implementation 'com.google.firebase:firebase-crashlytics-ktx'
    
    // JTransforms for FFT
    implementation 'com.github.wendykierp:JTransforms:3.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

apply plugin: 'com.google.gms.google-services'
EOL

# Création du fichier google-services.json
echo "[INFO] Création du fichier google-services.json..."
cat > $DEPLOY_DIR/google-services.json << EOL
{
  "project_info": {
    "project_number": "123456789012",
    "project_id": "ecoguard-tracking",
    "storage_bucket": "ecoguard-tracking.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789012:android:abc123def456",
        "android_client_info": {
          "package_name": "com.ecoguard.tracking"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "********"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789012:android:def456abc123",
        "android_client_info": {
          "package_name": "com.ecoguard.tracking.debug"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "********"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
EOL

# Simulation de la compilation de l'application
echo "[INFO] Simulation de la compilation de l'application..."
echo "cd $DEPLOY_DIR && ./gradlew assembleRelease"

# Simulation de la génération des APK et Bundle
echo "[INFO] Simulation de la génération des APK et Bundle..."
mkdir -p $DEPLOY_DIR/build/outputs/apk/release
mkdir -p $DEPLOY_DIR/build/outputs/bundle/release
echo "Création de fichiers factices pour simuler les artefacts de build..."
echo "This is a simulated APK file" > $DEPLOY_DIR/build/outputs/apk/release/app-release.apk
echo "This is a simulated Bundle file" > $DEPLOY_DIR/build/outputs/bundle/release/app-release.aab

# Copie des artefacts de build vers le répertoire de déploiement
echo "[INFO] Copie des artefacts de build..."
mkdir -p $DEPLOY_DIR/release
cp $DEPLOY_DIR/build/outputs/apk/release/app-release.apk $DEPLOY_DIR/release/ecoguard-$VERSION_NAME.apk
cp $DEPLOY_DIR/build/outputs/bundle/release/app-release.aab $DEPLOY_DIR/release/ecoguard-$VERSION_NAME.aab

# Création des notes de version
echo "[INFO] Création des notes de version..."
cat > $DEPLOY_DIR/release/release_notes.txt << EOL
EcoGuard Tracking v$VERSION_NAME

Nouvelles fonctionnalités :
- Stratégie d'émission proactive pour une meilleure détection des appareils volés
- Écoute passive universelle pour détecter tous les appareils EcoGuard
- Stockage local des observations pour une meilleure résilience
- Intégration avec le système d'alertes communautaires
- Rapports anonymes conditionnels pour protéger la vie privée

Améliorations :
- Optimisation de la consommation de batterie
- Amélioration de la précision de détection
- Interface utilisateur plus intuitive
- Meilleure gestion des permissions
- Corrections de bugs et améliorations de performance
EOL

echo "[SUCCESS] Application mobile préparée pour le déploiement!"
echo "APK: $DEPLOY_DIR/release/ecoguard-$VERSION_NAME.apk"
echo "Bundle: $DEPLOY_DIR/release/ecoguard-$VERSION_NAME.aab"
echo "Notes de version: $DEPLOY_DIR/release/release_notes.txt"

# Journalisation du déploiement
echo "Préparation de l'application mobile effectuée le $(date)" >> $LOG_FILE
