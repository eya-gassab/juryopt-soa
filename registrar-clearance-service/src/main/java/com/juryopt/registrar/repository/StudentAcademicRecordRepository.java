package com.juryopt.registrar.repository;

import com.juryopt.registrar.domain.StudentAcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAcademicRecordRepository extends JpaRepository<StudentAcademicRecord, String> {
}