package com.juryopt.orchestrator.repository;

import com.juryopt.orchestrator.domain.DefenseSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenseSlotRepository extends JpaRepository<DefenseSlot, Long> {

    boolean existsBySlotCodeAndRoomCode(String slotCode, String roomCode);
}