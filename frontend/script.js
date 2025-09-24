// EcoGuard Tracking Portal - Main JavaScript

document.addEventListener('DOMContentLoaded', function() {
    // Initialize maps
    initMaps();
    
    // Navigation between pages
    initNavigation();
    
    // Form handlers
    initForms();
    
    // Show login modal on page load (commented out for demo)
    // showLoginModal();
});

// Initialize Leaflet maps
function initMaps() {
    // Mini map on dashboard
    if (document.getElementById('mini-map')) {
        const miniMap = L.map('mini-map').setView([48.8566, 2.3522], 13);
        
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(miniMap);
        
        // Add marker for last known position
        const marker = L.marker([48.8566, 2.3522]).addTo(miniMap);
        marker.bindPopup("<b>Dernière position</b><br>Samsung Galaxy S22<br>14:37").openPopup();
    }
    
    // Tracking map on tracking page
    if (document.getElementById('tracking-map')) {
        const trackingMap = L.map('tracking-map').setView([48.8566, 2.3522], 14);
        
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(trackingMap);
        
        // Sample data for device tracking
        const trackingData = [
            { lat: 48.8566, lng: 2.3522, time: '14:37', confidence: 92 },
            { lat: 48.8580, lng: 2.3510, time: '14:12', confidence: 89 },
            { lat: 48.8605, lng: 2.3522, time: '13:45', confidence: 76 },
            { lat: 48.8628, lng: 2.3542, time: '13:20', confidence: 85 },
            { lat: 48.8641, lng: 2.3580, time: '12:58', confidence: 72 }
        ];
        
        // Add markers for each observation
        const markers = [];
        trackingData.forEach(point => {
            const marker = L.marker([point.lat, point.lng]).addTo(trackingMap);
            marker.bindPopup(`
                <b>Observation à ${point.time}</b><br>
                Confiance: ${point.confidence}%<br>
                Coordonnées: ${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}
            `);
            markers.push(marker);
        });
        
        // Add path line connecting the points
        const pathLine = L.polyline(
            trackingData.map(point => [point.lat, point.lng]),
            { color: '#4CAF50', weight: 3, opacity: 0.7, dashArray: '5, 10' }
        ).addTo(trackingMap);
        
        // Add circle for the most recent position
        const recentPosition = trackingData[0];
        const circle = L.circle([recentPosition.lat, recentPosition.lng], {
            color: '#4CAF50',
            fillColor: '#4CAF50',
            fillOpacity: 0.2,
            radius: 200
        }).addTo(trackingMap);
        
        // Fit map to show all markers
        const group = new L.featureGroup(markers);
        trackingMap.fitBounds(group.getBounds().pad(0.1));
    }
}

// Initialize navigation between pages
function initNavigation() {
    // Sidebar navigation
    document.querySelectorAll('[data-page]').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            const targetPage = this.getAttribute('data-page');
            navigateToPage(targetPage);
        });
    });
    
    // Sidebar toggle for mobile
    document.getElementById('sidebar-toggle').addEventListener('click', function() {
        document.querySelector('.sidebar').classList.toggle('active');
        document.querySelector('.main-content').classList.toggle('active');
    });
    
    // Logout button
    document.getElementById('logout-btn').addEventListener('click', function(e) {
        e.preventDefault();
        showLoginModal();
    });
}

// Navigate to a specific page
function navigateToPage(pageId) {
    // Hide all pages
    document.querySelectorAll('.content-page').forEach(page => {
        page.classList.remove('active');
    });
    
    // Show target page
    document.getElementById(`${pageId}-page`).classList.add('active');
    
    // Update active state in sidebar
    document.querySelectorAll('.sidebar ul li').forEach(item => {
        item.classList.remove('active');
    });
    
    document.querySelector(`.sidebar ul li a[data-page="${pageId}"]`)?.parentElement.classList.add('active');
    
    // Close sidebar on mobile after navigation
    if (window.innerWidth <= 768) {
        document.querySelector('.sidebar').classList.remove('active');
        document.querySelector('.main-content').classList.remove('active');
    }
}

// Initialize form handlers
function initForms() {
    // Theft report form
    const theftReportForm = document.getElementById('theft-report-form');
    if (theftReportForm) {
        theftReportForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // Show success modal
            const successModal = new bootstrap.Modal(document.getElementById('theftReportSuccessModal'));
            successModal.show();
        });
    }
    
    // Police report checkbox
    const policeReportCheck = document.getElementById('police-report-check');
    if (policeReportCheck) {
        policeReportCheck.addEventListener('change', function() {
            const policeReportDetails = document.getElementById('police-report-details');
            policeReportDetails.style.display = this.checked ? 'block' : 'none';
        });
    }
    
    // Login form
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // Hide login modal
            const loginModal = bootstrap.Modal.getInstance(document.getElementById('loginModal'));
            loginModal.hide();
            
            // Navigate to dashboard
            navigateToPage('dashboard');
        });
    }
    
    // Account settings form
    const accountSettingsForm = document.getElementById('account-settings-form');
    if (accountSettingsForm) {
        accountSettingsForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // Show success alert
            showAlert('Paramètres du compte mis à jour avec succès', 'success');
        });
    }
    
    // Notification settings form
    const notificationSettingsForm = document.getElementById('notification-settings-form');
    if (notificationSettingsForm) {
        notificationSettingsForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // Show success alert
            showAlert('Préférences de notification mises à jour avec succès', 'success');
        });
    }
    
    // Security settings form
    const securitySettingsForm = document.getElementById('security-settings-form');
    if (securitySettingsForm) {
        securitySettingsForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // Show success alert
            showAlert('Mot de passe mis à jour avec succès', 'success');
        });
    }
}

// Show login modal
function showLoginModal() {
    const loginModal = new bootstrap.Modal(document.getElementById('loginModal'));
    loginModal.show();
}

// Show alert message
function showAlert(message, type = 'info') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
    alertDiv.setAttribute('role', 'alert');
    
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;
    
    // Insert at the top of the current active page
    const activePage = document.querySelector('.content-page.active');
    activePage.insertBefore(alertDiv, activePage.firstChild);
    
    // Auto-dismiss after 5 seconds
    setTimeout(() => {
        alertDiv.classList.remove('show');
        setTimeout(() => alertDiv.remove(), 150);
    }, 5000);
}
