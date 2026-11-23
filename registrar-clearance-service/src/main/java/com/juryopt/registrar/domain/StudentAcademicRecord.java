package com.juryopt.registrar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_academic_records")
public class StudentAcademicRecord {

    @Id
    @Column(name = "student_matricule", length = 20)
    private String studentMatricule;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "credits_earned", nullable = false)
    private int creditsEarned;

    @Column(name = "required_credits", nullable = false)
    private int requiredCredits;

    @Column(name = "has_admin_hold", nullable = false)
    private boolean hasAdminHold;

    protected StudentAcademicRecord() {
    }

    public String getStudentMatricule() {
        return studentMatricule;
    }

    public String getFullName() {
        return fullName;
    }

    public int getCreditsEarned() {
        return creditsEarned;
    }

    public int getRequiredCredits() {
        return requiredCredits;
    }

    public boolean isHasAdminHold() {
        return hasAdminHold;
    }
}