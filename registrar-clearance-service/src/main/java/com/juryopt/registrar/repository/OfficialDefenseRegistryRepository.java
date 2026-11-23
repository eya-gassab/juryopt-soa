package com.juryopt.registrar.repository;

import com.juryopt.registrar.domain.OfficialDefenseRegistry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialDefenseRegistryRepository extends JpaRepository<OfficialDefenseRegistry, String> {
    Optional<OfficialDefenseRegistry> findByStudentMatricule(String studentMatricule);
}