package com.ecoguard.tracking.util;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class GeoUtils {

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Create a JTS Point from longitude and latitude
     */
    public static Point createPoint(double longitude, double latitude) {
        return geometryFactory.createPoint(new Coordinate(longitude, latitude));
    }

    /**
     * Calculate distance between two points in kilometers
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    /**
     * Get address from coordinates using OpenStreetMap Nominatim API
     */
    public static String getAddressFromCoordinates(double latitude, double longitude) {
        try {
            String urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" 
                    + latitude + "&lon=" + longitude + "&zoom=18&addressdetails=1";
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "EcoGuardTrackingPortal");
            
            if (conn.getResponseCode() != 200) {
                log.error("Failed to get address: HTTP error code {}", conn.getResponseCode());
                return "Localisation inconnue";
            }
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            // Parse JSON response to extract address
            // This is a simplified version, in a real app you would use a JSON library
            String jsonResponse = response.toString();
            
            // Extract display_name field
            int displayNameStart = jsonResponse.indexOf("\"display_name\":\"") + 16;
            int displayNameEnd = jsonResponse.indexOf("\"", displayNameStart);
            
            if (displayNameStart >= 16 && displayNameEnd > displayNameStart) {
                return jsonResponse.substring(displayNameStart, displayNameEnd);
            } else {
                return "Localisation inconnue";
            }
            
        } catch (IOException e) {
            log.error("Error getting address from coordinates", e);
            return "Localisation inconnue";
        }
    }

    /**
     * Get coordinates from address using OpenStreetMap Nominatim API
     */
    public static double[] getCoordinatesFromAddress(String address) {
        try {
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String urlStr = "https://nominatim.openstreetmap.org/search?format=json&q=" + encodedAddress + "&limit=1";
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "EcoGuardTrackingPortal");
            
            if (conn.getResponseCode() != 200) {
                log.error("Failed to get coordinates: HTTP error code {}", conn.getResponseCode());
                return null;
            }
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            // Parse JSON response to extract coordinates
            // This is a simplified version, in a real app you would use a JSON library
            String jsonResponse = response.toString();
            
            if (jsonResponse.equals("[]")) {
                return null;
            }
            
            // Extract lat and lon fields
            int latStart = jsonResponse.indexOf("\"lat\":\"") + 7;
            int latEnd = jsonResponse.indexOf("\"", latStart);
            int lonStart = jsonResponse.indexOf("\"lon\":\"") + 7;
            int lonEnd = jsonResponse.indexOf("\"", lonStart);
            
            if (latStart >= 7 && latEnd > latStart && lonStart >= 7 && lonEnd > lonStart) {
                double lat = Double.parseDouble(jsonResponse.substring(latStart, latEnd));
                double lon = Double.parseDouble(jsonResponse.substring(lonStart, lonEnd));
                return new double[]{lat, lon};
            } else {
                return null;
            }
            
        } catch (IOException | NumberFormatException e) {
            log.error("Error getting coordinates from address", e);
            return null;
        }
    }
}
