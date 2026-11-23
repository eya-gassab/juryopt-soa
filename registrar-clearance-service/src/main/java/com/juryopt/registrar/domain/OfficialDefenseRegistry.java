package com.juryopt.registrar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "official_defense_registry")
public class OfficialDefenseRegistry {

    @Id
    @Column(name = "registry_code", length = 30)
    private String registryCode;

    @Column(name = "student_matricule", length = 20, nullable = false, unique = true)
    private String studentMatricule;

    @Column(name = "clearance_token", nullable = false)
    private String clearanceToken;

    @Column(name = "slot_code", nullable = false)
    private String slotCode;

    @Column(name = "room_code", nullable = false)
    private String roomCode;

    @Column(name = "jury_summary", nullable = false)
    private String jurySummary;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    protected OfficialDefenseRegistry() {
    }

    public OfficialDefenseRegistry(String registryCode, String studentMatricule, String clearanceToken,
                                   String slotCode, String roomCode, String jurySummary,
                                   LocalDateTime registeredAt) {
        this.registryCode = registryCode;
        this.studentMatricule = studentMatricule;
        this.clearanceToken = clearanceToken;
        this.slotCode = slotCode;
        this.roomCode = roomCode;
        this.jurySummary = jurySummary;
        this.registeredAt = registeredAt;
    }

    public String getRegistryCode() {
        return registryCode;
    }

    public String getStudentMatricule() {
        return studentMatricule;
    }

    public String getClearanceToken() {
        return clearanceToken;
    }

    public String getSlotCode() {
        return slotCode;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getJurySummary() {
        return jurySummary;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}