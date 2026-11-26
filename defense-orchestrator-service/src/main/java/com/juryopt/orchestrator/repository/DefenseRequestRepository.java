package com.juryopt.orchestrator.repository;

import com.juryopt.orchestrator.domain.DefenseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenseRequestRepository extends JpaRepository<DefenseRequest, Long> {

    boolean existsByStudentMatricule(String studentMatricule);

    boolean existsByStudentMatriculeAndIdNot(String studentMatricule, Long id);
}