package com.juryopt.orchestrator.repository;

import com.juryopt.orchestrator.domain.DefenseSlot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenseSlotRepository extends JpaRepository<DefenseSlot, Long> {

    boolean existsBySlotCodeAndRoomCode(String slotCode, String roomCode);

    List<DefenseSlot> findByAvailableTrueOrderByIdAsc();

    Optional<DefenseSlot> findBySlotCodeAndRoomCode(String slotCode, String roomCode);
}