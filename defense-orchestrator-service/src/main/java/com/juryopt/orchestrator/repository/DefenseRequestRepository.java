package com.juryopt.orchestrator.repository;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenseRequestRepository extends JpaRepository<DefenseRequest, Long> {

    boolean existsByStudentMatricule(String studentMatricule);

    boolean existsByStudentMatriculeAndIdNot(String studentMatricule, Long id);

    List<DefenseRequest> findByStatusInOrderByIdAsc(Collection<DefenseStatus> statuses);

    List<DefenseRequest> findByStatusOrderByIdAsc(DefenseStatus status);
}