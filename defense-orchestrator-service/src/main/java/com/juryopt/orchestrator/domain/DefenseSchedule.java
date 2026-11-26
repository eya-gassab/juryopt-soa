package com.juryopt.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "defense_schedules")
public class DefenseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ScheduleStatus status;

    @Column(name = "total_assigned", nullable = false)
    private int totalAssigned;

    @Column(name = "conflict_count", nullable = false)
    private int conflictCount;

    @Column(name = "balance_score", nullable = false)
    private double balanceScore;

    protected DefenseSchedule() {
    }

    public DefenseSchedule(LocalDateTime createdAt, ScheduleStatus status,
                           int totalAssigned, int conflictCount, double balanceScore) {
        this.createdAt = createdAt;
        this.status = status;
        this.totalAssigned = totalAssigned;
        this.conflictCount = conflictCount;
        this.balanceScore = balanceScore;
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ScheduleStatus getStatus() { return status; }
    public int getTotalAssigned() { return totalAssigned; }
    public int getConflictCount() { return conflictCount; }
    public double getBalanceScore() { return balanceScore; }

    public void setStatus(ScheduleStatus status) { this.status = status; }
}