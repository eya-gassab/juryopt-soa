package com.juryopt.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "defense_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_defense_slots_slot_room",
                columnNames = {"slot_code", "room_code"}
        )
)
public class DefenseSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_code", length = 30, nullable = false)
    private String slotCode;

    @Column(name = "room_code", length = 30, nullable = false)
    private String roomCode;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    protected DefenseSlot() {
    }

    public DefenseSlot(String slotCode, String roomCode, boolean available) {
        this.slotCode = slotCode;
        this.roomCode = roomCode;
        this.available = available;
    }

    public Long getId() { return id; }
    public String getSlotCode() { return slotCode; }
    public String getRoomCode() { return roomCode; }
    public boolean isAvailable() { return available; }

    public void setAvailable(boolean available) { this.available = available; }
}