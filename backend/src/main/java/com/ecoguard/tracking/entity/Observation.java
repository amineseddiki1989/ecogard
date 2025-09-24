package com.ecoguard.tracking.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Point;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "observations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "observation_time", nullable = false)
    private LocalDateTime observationTime;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Type(type = "org.locationtech.jts.geom.Point")
    private Point location;

    @Column(nullable = false)
    private Double accuracy;

    @Column(nullable = false)
    private Integer confidence;

    @Column(name = "reporter_hash")
    private String reporterHash;

    @Column(name = "is_ghost")
    private boolean ghost;

    @Column(name = "signal_strength")
    private Integer signalStrength;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "network_type")
    private String networkType;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (observationTime == null) {
            observationTime = LocalDateTime.now();
        }
    }
}
