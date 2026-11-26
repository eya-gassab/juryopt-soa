package com.juryopt.optimizer.engine;

import com.juryopt.optimizer.grpc.DefenseItem;
import com.juryopt.optimizer.grpc.OptimizationProgress;
import com.juryopt.optimizer.grpc.OptimizationStage;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import com.juryopt.optimizer.grpc.SlotItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Optimiseur déterministe en 2 phases :
 * 1. Placement glouton (salle libre + 3 enseignants disponibles).
 * 2. 3 passes d'échange (swap) pour éliminer les conflits résiduels.
 */
public class GreedySwapOptimizer {

    private static final int BALANCING_PASSES = 3;
    private final ConflictEvaluator evaluator;

    public GreedySwapOptimizer(ConflictEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Résumé final retourné au service gRPC pour la sauvegarde en base (optimization_runs).
     */
    public record OptimizationSummary(
            int requestCount,
            int assignedCount,
            int initialConflicts,
            int finalConflicts,
            double balanceScore
    ) {}

    public OptimizationSummary optimize(
            List<DefenseItem> defenses,
            List<SlotItem> slots,
            Consumer<OptimizationProgress> progressListener) {

        // --- Phase 1 : Placement glouton ---
        List<ProposedAssignment> current = new ArrayList<>();
        Set<Integer> usedSlotIndexes = new HashSet<>();
        Map<String, Set<String>> teachersBySlot = new HashMap<>();

        for (DefenseItem defense : defenses) {
            int chosenIndex = findConflictFreeSlot(defense, slots, usedSlotIndexes, teachersBySlot);
            if (chosenIndex == -1) {
                // Repli : premier créneau libre même s'il crée un conflit enseignant (corrigé en phase 2)
                chosenIndex = findFirstFreeSlot(slots, usedSlotIndexes);
            }
            if (chosenIndex != -1) {
                usedSlotIndexes.add(chosenIndex);
                SlotItem slot = slots.get(chosenIndex);
                current.add(buildAssignment(defense, slot));
                markTeachers(teachersBySlot, slot.getSlotCode(), defense);
            }
        }

        int initialConflicts = evaluator.evaluate(current).size();
        double balanceScore = evaluator.computeBalanceScore(current);

        progressListener.accept(OptimizationProgress.newBuilder()
                .setStage(OptimizationStage.GREEDY_PLACEMENT)
                .setPassNumber(0)
                .setAssignedCount(current.size())
                .setConflictCount(initialConflicts)
                .setBalanceScore(balanceScore)
                .build());

        // --- Phase 2 : 3 passes d'optimisation par échange (swap) ---
        int currentConflicts = initialConflicts;
        for (int pass = 1; pass <= BALANCING_PASSES; pass++) {
            if (currentConflicts > 0) {
                current = runSwapPass(current);
                currentConflicts = evaluator.evaluate(current).size();
            }
            balanceScore = evaluator.computeBalanceScore(current);

            progressListener.accept(OptimizationProgress.newBuilder()
                    .setStage(OptimizationStage.LOAD_BALANCING_PASS)
                    .setPassNumber(pass)
                    .setAssignedCount(current.size())
                    .setConflictCount(currentConflicts)
                    .setBalanceScore(balanceScore)
                    .build());
        }

        // --- Phase 3 : État final avec toutes les affectations ---
        progressListener.accept(OptimizationProgress.newBuilder()
                .setStage(OptimizationStage.COMPLETED)
                .setPassNumber(BALANCING_PASSES)
                .setAssignedCount(current.size())
                .setConflictCount(currentConflicts)
                .setBalanceScore(balanceScore)
                .addAllAssignments(current)
                .build());

        return new OptimizationSummary(
                defenses.size(),
                current.size(),
                initialConflicts,
                currentConflicts,
                balanceScore
        );
    }

    private int findConflictFreeSlot(
            DefenseItem defense,
            List<SlotItem> slots,
            Set<Integer> usedSlotIndexes,
            Map<String, Set<String>> teachersBySlot) {

        for (int i = 0; i < slots.size(); i++) {
            if (usedSlotIndexes.contains(i)) continue;
            Set<String> busy = teachersBySlot.getOrDefault(slots.get(i).getSlotCode(), Set.of());
            if (!busy.contains(defense.getSupervisorCode())
                    && !busy.contains(defense.getPresidentCode())
                    && !busy.contains(defense.getRapporteurCode())) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstFreeSlot(List<SlotItem> slots, Set<Integer> usedSlotIndexes) {
        for (int i = 0; i < slots.size(); i++) {
            if (!usedSlotIndexes.contains(i)) return i;
        }
        return -1;
    }

    private void markTeachers(Map<String, Set<String>> teachersBySlot, String slotCode, DefenseItem d) {
        Set<String> busy = teachersBySlot.computeIfAbsent(slotCode, k -> new HashSet<>());
        busy.add(d.getSupervisorCode());
        busy.add(d.getPresidentCode());
        busy.add(d.getRapporteurCode());
    }

    private List<ProposedAssignment> runSwapPass(List<ProposedAssignment> assignments) {
        List<ProposedAssignment> best = new ArrayList<>(assignments);
        int bestConflicts = evaluator.evaluate(best).size();

        for (int i = 0; i < best.size(); i++) {
            for (int j = i + 1; j < best.size(); j++) {
                List<ProposedAssignment> candidate = swapSlots(best, i, j);
                int candidateConflicts = evaluator.evaluate(candidate).size();
                if (candidateConflicts < bestConflicts) {
                    best = candidate;
                    bestConflicts = candidateConflicts;
                }
            }
        }
        return best;
    }

    private List<ProposedAssignment> swapSlots(List<ProposedAssignment> list, int i, int j) {
        List<ProposedAssignment> copy = new ArrayList<>(list);
        ProposedAssignment a = copy.get(i);
        ProposedAssignment b = copy.get(j);

        copy.set(i, a.toBuilder().setSlotCode(b.getSlotCode()).setRoomCode(b.getRoomCode()).build());
        copy.set(j, b.toBuilder().setSlotCode(a.getSlotCode()).setRoomCode(a.getRoomCode()).build());
        return copy;
    }

    private ProposedAssignment buildAssignment(DefenseItem d, SlotItem s) {
        return ProposedAssignment.newBuilder()
                .setRequestId(d.getRequestId())
                .setSlotCode(s.getSlotCode())
                .setRoomCode(s.getRoomCode())
                .setSupervisorCode(d.getSupervisorCode())
                .setPresidentCode(d.getPresidentCode())
                .setRapporteurCode(d.getRapporteurCode())
                .build();
    }
}