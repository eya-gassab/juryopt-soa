package com.juryopt.optimizer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.juryopt.optimizer.engine.GreedySwapOptimizer.OptimizationSummary;
import com.juryopt.optimizer.grpc.DefenseItem;
import com.juryopt.optimizer.grpc.OptimizationProgress;
import com.juryopt.optimizer.grpc.OptimizationStage;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import com.juryopt.optimizer.grpc.SlotItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreedySwapOptimizerTest {

    private final ConflictEvaluator evaluator = new ConflictEvaluator();
    private final GreedySwapOptimizer optimizer = new GreedySwapOptimizer(evaluator);

    @Test
    void optimize_whenGreedyFindsConflictFreePlacement_shouldEmitFiveStagesWithAssignmentsOnlyOnCompleted() {
        List<DefenseItem> defenses = List.of(
                defense("1", "PROF-01", "PROF-02", "PROF-03"),
                defense("2", "PROF-01", "PROF-04", "PROF-05")
        );
        List<SlotItem> slots = List.of(
                slot("2026-06-15_09:00", "SALLE-A1"),
                slot("2026-06-15_09:00", "SALLE-A2"),
                slot("2026-06-15_10:00", "SALLE-A1")
        );
        List<OptimizationProgress> events = new ArrayList<>();

        OptimizationSummary summary = optimizer.optimize(defenses, slots, events::add);

        assertThat(summary.requestCount()).isEqualTo(2);
        assertThat(summary.assignedCount()).isEqualTo(2);
        assertThat(summary.initialConflicts()).isZero();
        assertThat(summary.finalConflicts()).isZero();
        assertThat(summary.balanceScore()).isEqualTo(0.4);

        assertThat(events).hasSize(5);
        assertThat(events).extracting(OptimizationProgress::getStage).containsExactly(
                OptimizationStage.GREEDY_PLACEMENT,
                OptimizationStage.LOAD_BALANCING_PASS,
                OptimizationStage.LOAD_BALANCING_PASS,
                OptimizationStage.LOAD_BALANCING_PASS,
                OptimizationStage.COMPLETED
        );
        assertThat(events.subList(0, 4))
                .allSatisfy(step -> assertThat(step.getAssignmentsList()).isEmpty());

        OptimizationProgress completed = events.get(4);
        assertThat(completed.getAssignmentsList()).hasSize(2);
        assertThat(evaluator.evaluate(completed.getAssignmentsList())).isEmpty();
    }

    @Test
    void optimize_whenGreedyLeavesConflict_shouldResolveViaSwapAndKeepBalanceScoreUnchanged() {
        List<DefenseItem> defenses = List.of(
                defense("1", "PROF-01", "PROF-02", "PROF-03"),
                defense("2", "PROF-04", "PROF-05", "PROF-06"),
                defense("3", "PROF-01", "PROF-07", "PROF-08"),
                defense("4", "PROF-07", "PROF-04", "PROF-09")
        );
        List<SlotItem> slots = List.of(
                slot("2026-06-15_09:00", "SALLE-A1"),
                slot("2026-06-15_09:00", "SALLE-A2"),
                slot("2026-06-15_10:00", "SALLE-A1"),
                slot("2026-06-15_10:00", "SALLE-A2")
        );
        List<OptimizationProgress> events = new ArrayList<>();

        OptimizationSummary summary = optimizer.optimize(defenses, slots, events::add);

        assertThat(summary.assignedCount()).isEqualTo(4);
        assertThat(summary.initialConflicts()).isEqualTo(1);
        assertThat(summary.finalConflicts()).isZero();

        double initialBalance = events.get(0).getBalanceScore();
        assertThat(events).allSatisfy(step -> assertThat(step.getBalanceScore()).isEqualTo(initialBalance));

        List<ProposedAssignment> finalAssignments = events.get(4).getAssignmentsList();
        assertThat(finalAssignments).hasSize(4);
        assertThat(evaluator.evaluate(finalAssignments)).isEmpty();
    }

    @Test
    void optimize_swapsDoNotMoveDefenseToUnusedFreeSlotAndKeepBalanceScoreConstant() {
        List<DefenseItem> defenses = List.of(
                defense("1", "PROF-01", "PROF-02", "PROF-03"),
                defense("2", "PROF-04", "PROF-05", "PROF-06"),
                defense("3", "PROF-01", "PROF-07", "PROF-08"),
                defense("4", "PROF-07", "PROF-04", "PROF-09")
        );
        List<SlotItem> slots = List.of(
                slot("2026-06-15_09:00", "SALLE-A1"),
                slot("2026-06-15_09:00", "SALLE-A2"),
                slot("2026-06-15_10:00", "SALLE-A1"),
                slot("2026-06-15_09:00", "SALLE-A3"),
                slot("2026-06-15_10:00", "SALLE-A2")
        );
        List<OptimizationProgress> events = new ArrayList<>();

        OptimizationSummary summary = optimizer.optimize(defenses, slots, events::add);

        assertThat(summary.initialConflicts()).isEqualTo(1);
        assertThat(summary.finalConflicts()).isEqualTo(1);
        assertThat(events).allSatisfy(step -> assertThat(step.getBalanceScore()).isEqualTo(summary.balanceScore()));

        List<ProposedAssignment> finalAssignments = events.get(4).getAssignmentsList();
        assertThat(finalAssignments)
                .noneMatch(a -> a.getSlotCode().equals("2026-06-15_10:00")
                        && a.getRoomCode().equals("SALLE-A2"));
    }

    private DefenseItem defense(String id, String sup, String pres, String rap) {
        return DefenseItem.newBuilder()
                .setRequestId(id)
                .setSupervisorCode(sup)
                .setPresidentCode(pres)
                .setRapporteurCode(rap)
                .build();
    }

    private SlotItem slot(String slotCode, String roomCode) {
        return SlotItem.newBuilder()
                .setSlotCode(slotCode)
                .setRoomCode(roomCode)
                .build();
    }
}