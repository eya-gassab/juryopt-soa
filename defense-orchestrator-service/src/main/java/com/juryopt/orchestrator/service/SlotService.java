package com.juryopt.orchestrator.service;

import com.juryopt.orchestrator.domain.DefenseSlot;
import com.juryopt.orchestrator.dto.SlotRequestDto;
import com.juryopt.orchestrator.dto.SlotResponseDto;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService {

    private final DefenseSlotRepository slotRepository;

    public SlotService(DefenseSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Transactional
    public SlotResponseDto create(SlotRequestDto dto) {
        if (slotRepository.existsBySlotCodeAndRoomCode(dto.slotCode(), dto.roomCode())) {
            throw new DuplicateResourceException(
                    "Le créneau " + dto.slotCode() + " dans la salle " + dto.roomCode() + " existe déjà."
            );
        }

        boolean available = dto.available() == null || dto.available();
        DefenseSlot slot = new DefenseSlot(dto.slotCode(), dto.roomCode(), available);
        return SlotResponseDto.from(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public List<SlotResponseDto> findAll() {
        return slotRepository.findAll().stream()
                .map(SlotResponseDto::from)
                .toList();
    }
}