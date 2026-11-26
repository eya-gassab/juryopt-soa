package com.juryopt.optimizer.repository;

import com.juryopt.optimizer.domain.OptimizationRun;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données de la table optimization_runs.
 */
public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
}