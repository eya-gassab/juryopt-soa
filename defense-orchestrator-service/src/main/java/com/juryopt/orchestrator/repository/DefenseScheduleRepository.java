package com.juryopt.orchestrator.repository;

import com.juryopt.orchestrator.domain.DefenseSchedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenseScheduleRepository extends JpaRepository<DefenseSchedule, Long> {

    Optional<DefenseSchedule> findTopByOrderByIdDesc();
}