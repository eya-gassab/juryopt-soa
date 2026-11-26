package com.juryopt.optimizer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Trace une exécution du moteur d'optimisation dans optimizer_db.
 */
@Entity
@Table(name = "optimization_runs")
public class OptimizationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "assigned_count", nullable = false)
    private int assignedCount;

    @Column(name = "initial_conflicts", nullable = false)
    private int initialConflicts;

    @Column(name = "final_conflicts", nullable = false)
    private int finalConflicts;

    @Column(name = "balance_score", nullable = false)
    private double balanceScore;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    protected OptimizationRun() {
    }

    public OptimizationRun(int requestCount, int assignedCount, int initialConflicts,
                           int finalConflicts, double balanceScore, LocalDateTime executedAt) {
        this.requestCount = requestCount;
        this.assignedCount = assignedCount;
        this.initialConflicts = initialConflicts;
        this.finalConflicts = finalConflicts;
        this.balanceScore = balanceScore;
        this.executedAt = executedAt;
    }

    public Long getId() { return id; }
    public int getRequestCount() { return requestCount; }
    public int getAssignedCount() { return assignedCount; }
    public int getInitialConflicts() { return initialConflicts; }
    public int getFinalConflicts() { return finalConflicts; }
    public double getBalanceScore() { return balanceScore; }
    public LocalDateTime getExecutedAt() { return executedAt; }
}