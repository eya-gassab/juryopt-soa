package com.juryopt.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "defense_requests")
public class DefenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_matricule", length = 20, nullable = false, unique = true)
    private String studentMatricule;

    @Column(name = "student_name", length = 120)
    private String studentName;

    @Column(name = "project_title", length = 200, nullable = false)
    private String projectTitle;

    @Column(name = "supervisor_code", length = 20, nullable = false)
    private String supervisorCode;

    @Column(name = "president_code", length = 20, nullable = false)
    private String presidentCode;

    @Column(name = "rapporteur_code", length = 20, nullable = false)
    private String rapporteurCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    private DefenseStatus status;

    @Column(name = "clearance_token", length = 64)
    private String clearanceToken;

    @Column(name = "registry_code", length = 30)
    private String registryCode;

    @Column(name = "assigned_slot_code", length = 30)
    private String assignedSlotCode;

    @Column(name = "assigned_room_code", length = 30)
    private String assignedRoomCode;

    protected DefenseRequest() {
    }

    public DefenseRequest(String studentMatricule, String studentName, String projectTitle,
                          String supervisorCode, String presidentCode, String rapporteurCode,
                          DefenseStatus status) {
        this.studentMatricule = studentMatricule;
        this.studentName = studentName;
        this.projectTitle = projectTitle;
        this.supervisorCode = supervisorCode;
        this.presidentCode = presidentCode;
        this.rapporteurCode = rapporteurCode;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getStudentMatricule() { return studentMatricule; }
    public String getStudentName() { return studentName; }
    public String getProjectTitle() { return projectTitle; }
    public String getSupervisorCode() { return supervisorCode; }
    public String getPresidentCode() { return presidentCode; }
    public String getRapporteurCode() { return rapporteurCode; }
    public DefenseStatus getStatus() { return status; }
    public String getClearanceToken() { return clearanceToken; }
    public String getRegistryCode() { return registryCode; }
    public String getAssignedSlotCode() { return assignedSlotCode; }
    public String getAssignedRoomCode() { return assignedRoomCode; }

    public void setStudentMatricule(String studentMatricule) { this.studentMatricule = studentMatricule; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
    public void setSupervisorCode(String supervisorCode) { this.supervisorCode = supervisorCode; }
    public void setPresidentCode(String presidentCode) { this.presidentCode = presidentCode; }
    public void setRapporteurCode(String rapporteurCode) { this.rapporteurCode = rapporteurCode; }
    public void setStatus(DefenseStatus status) { this.status = status; }
    public void setClearanceToken(String clearanceToken) { this.clearanceToken = clearanceToken; }
    public void setRegistryCode(String registryCode) { this.registryCode = registryCode; }
    public void setAssignedSlotCode(String assignedSlotCode) { this.assignedSlotCode = assignedSlotCode; }
    public void setAssignedRoomCode(String assignedRoomCode) { this.assignedRoomCode = assignedRoomCode; }
}