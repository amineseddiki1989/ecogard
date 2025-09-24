package com.ecoguard.tracking.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "theft_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TheftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "theft_date", nullable = false)
    private LocalDateTime theftDate;

    @Column(name = "theft_location")
    private String theftLocation;

    @Column(name = "theft_latitude")
    private Double theftLatitude;

    @Column(name = "theft_longitude")
    private Double theftLongitude;

    @Column(name = "circumstances", columnDefinition = "TEXT")
    private String circumstances;

    @Column(name = "police_report_number")
    private String policeReportNumber;

    @Column(name = "police_report_filed")
    private boolean policeReportFiled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TheftReportStatus status;

    @Column(name = "alert_sent")
    private boolean alertSent;

    @Column(name = "alert_sent_at")
    private LocalDateTime alertSentAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TheftReportStatus {
        ACTIVE,
        RESOLVED,
        CANCELLED
    }

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = TheftReportStatus.ACTIVE;
        }
        if (reportedAt == null) {
            reportedAt = LocalDateTime.now();
        }
    }
}
