package com.juryopt.orchestrator.dto;

import com.juryopt.orchestrator.domain.DefenseSlot;

public record SlotResponseDto(
        Long id,
        String slotCode,
        String roomCode,
        boolean available
) {
    public static SlotResponseDto from(DefenseSlot entity) {
        return new SlotResponseDto(
                entity.getId(),
                entity.getSlotCode(),
                entity.getRoomCode(),
                entity.isAvailable()
        );
    }
}
