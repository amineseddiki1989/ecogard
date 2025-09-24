#!/bin/bash

# Script principal pour exécuter tous les tests d'intégration EcoGuard
# Ce script coordonne l'exécution des tests pour les trois composants

echo "=== Démarrage des tests d'intégration complets du système EcoGuard ==="
echo "Date: $(date)"

# Définition des variables
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_TEST_SCRIPT="$SCRIPT_DIR/backend_tests.sh"
FRONTEND_TEST_SCRIPT="$SCRIPT_DIR/frontend_tests.sh"
MOBILE_TEST_SCRIPT="$SCRIPT_DIR/mobile_tests.sh"
REPORT_DIR="$SCRIPT_DIR/reports"
LOG_DIR="$SCRIPT_DIR/logs"

# Création des répertoires pour les rapports et logs
mkdir -p "$REPORT_DIR"
mkdir -p "$LOG_DIR"

# Fonction pour exécuter un test et générer un rapport
run_test() {
    local test_name="$1"
    local test_script="$2"
    local log_file="$LOG_DIR/${test_name}_$(date +%Y%m%d_%H%M%S).log"
    local report_file="$REPORT_DIR/${test_name}_report_$(date +%Y%m%d_%H%M%S).md"
    
    echo "Exécution des tests pour $test_name..."
    echo "Log: $log_file"
    
    # Rendre le script exécutable
    chmod +x "$test_script"
    
    # Exécuter le script de test et capturer la sortie
    "$test_script" | tee "$log_file"
    local test_result=${PIPESTATUS[0]}
    
    # Générer le rapport de test
    echo "# Rapport de Test d'Intégration - $test_name" > "$report_file"
    echo "" >> "$report_file"
    echo "**Date:** $(date)" >> "$report_file"
    echo "**Résultat:** $([ $test_result -eq 0 ] && echo 'SUCCÈS' || echo 'ÉCHEC')" >> "$report_file"
    echo "" >> "$report_file"
    echo "## Détails" >> "$report_file"
    echo "" >> "$report_file"
    echo "```" >> "$report_file"
    cat "$log_file" >> "$report_file"
    echo "```" >> "$report_file"
    
    return $test_result
}

# Exécution des tests backend
run_test "Backend" "$BACKEND_TEST_SCRIPT"
BACKEND_RESULT=$?

# Exécution des tests frontend
run_test "Frontend" "$FRONTEND_TEST_SCRIPT"
FRONTEND_RESULT=$?

# Exécution des tests mobile
run_test "Mobile" "$MOBILE_TEST_SCRIPT"
MOBILE_RESULT=$?

# Génération du rapport de synthèse
SUMMARY_REPORT="$REPORT_DIR/summary_report_$(date +%Y%m%d_%H%M%S).md"

echo "# Rapport de Synthèse des Tests d'Intégration EcoGuard" > "$SUMMARY_REPORT"
echo "" >> "$SUMMARY_REPORT"
echo "**Date:** $(date)" >> "$SUMMARY_REPORT"
echo "" >> "$SUMMARY_REPORT"
echo "## Résultats par Composant" >> "$SUMMARY_REPORT"
echo "" >> "$SUMMARY_REPORT"
echo "| Composant | Résultat |" >> "$SUMMARY_REPORT"
echo "|-----------|----------|" >> "$SUMMARY_REPORT"
echo "| Backend   | $([ $BACKEND_RESULT -eq 0 ] && echo '✅ SUCCÈS' || echo '❌ ÉCHEC') |" >> "$SUMMARY_REPORT"
echo "| Frontend  | $([ $FRONTEND_RESULT -eq 0 ] && echo '✅ SUCCÈS' || echo '❌ ÉCHEC') |" >> "$SUMMARY_REPORT"
echo "| Mobile    | $([ $MOBILE_RESULT -eq 0 ] && echo '✅ SUCCÈS' || echo '❌ ÉCHEC') |" >> "$SUMMARY_REPORT"
echo "" >> "$SUMMARY_REPORT"

# Calcul du résultat global
if [ $BACKEND_RESULT -eq 0 ] && [ $FRONTEND_RESULT -eq 0 ] && [ $MOBILE_RESULT -eq 0 ]; then
    GLOBAL_RESULT=0
    echo "## Résultat Global: ✅ SUCCÈS" >> "$SUMMARY_REPORT"
else
    GLOBAL_RESULT=1
    echo "## Résultat Global: ❌ ÉCHEC" >> "$SUMMARY_REPORT"
    
    # Détails des échecs
    echo "" >> "$SUMMARY_REPORT"
    echo "### Détails des Échecs" >> "$SUMMARY_REPORT"
    echo "" >> "$SUMMARY_REPORT"
    
    if [ $BACKEND_RESULT -ne 0 ]; then
        echo "- **Backend**: Échec des tests. Consultez le rapport détaillé pour plus d'informations." >> "$SUMMARY_REPORT"
    fi
    
    if [ $FRONTEND_RESULT -ne 0 ]; then
        echo "- **Frontend**: Échec des tests. Consultez le rapport détaillé pour plus d'informations." >> "$SUMMARY_REPORT"
    fi
    
    if [ $MOBILE_RESULT -ne 0 ]; then
        echo "- **Mobile**: Échec des tests. Consultez le rapport détaillé pour plus d'informations." >> "$SUMMARY_REPORT"
    fi
fi

echo "" >> "$SUMMARY_REPORT"
echo "## Prochaines Étapes" >> "$SUMMARY_REPORT"
echo "" >> "$SUMMARY_REPORT"
echo "1. Examiner les rapports détaillés pour chaque composant" >> "$SUMMARY_REPORT"
echo "2. Corriger les problèmes identifiés" >> "$SUMMARY_REPORT"
echo "3. Réexécuter les tests d'intégration" >> "$SUMMARY_REPORT"

# Affichage du résultat
echo ""
echo "=== Résumé des Tests d'Intégration ==="
echo "Backend: $([ $BACKEND_RESULT -eq 0 ] && echo 'SUCCÈS' || echo 'ÉCHEC')"
echo "Frontend: $([ $FRONTEND_RESULT -eq 0 ] && echo 'SUCCÈS' || echo 'ÉCHEC')"
echo "Mobile: $([ $MOBILE_RESULT -eq 0 ] && echo 'SUCCÈS' || echo 'ÉCHEC')"
echo ""
echo "Résultat global: $([ $GLOBAL_RESULT -eq 0 ] && echo 'SUCCÈS' || echo 'ÉCHEC')"
echo ""
echo "Rapports générés dans: $REPORT_DIR"
echo "Logs disponibles dans: $LOG_DIR"

exit $GLOBAL_RESULT
