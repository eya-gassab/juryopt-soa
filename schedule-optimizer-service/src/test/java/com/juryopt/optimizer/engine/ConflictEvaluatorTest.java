package com.juryopt.optimizer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.juryopt.optimizer.grpc.Conflict;
import com.juryopt.optimizer.grpc.ConflictType;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflictEvaluatorTest {

    private final ConflictEvaluator evaluator = new ConflictEvaluator();

    @Test
    void evaluate_withNoConflicts_shouldReturnEmptyList() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_09:00", "SALLE-A2", "PROF-04", "PROF-05", "PROF-06"),
                assignment("3", "2026-06-15_10:00", "SALLE-A1", "PROF-01", "PROF-04", "PROF-07")
        );

        List<Conflict> conflicts = evaluator.evaluate(assignments);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void evaluate_shouldDetectRoomDoubleBooking() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_09:00", "SALLE-A1", "PROF-04", "PROF-05", "PROF-06")
        );

        List<Conflict> conflicts = evaluator.evaluate(assignments);

        assertThat(conflicts).hasSize(1);
        Conflict conflict = conflicts.get(0);
        assertThat(conflict.getType()).isEqualTo(ConflictType.ROOM_DOUBLE_BOOKING);
        assertThat(conflict.getSlotCode()).isEqualTo("2026-06-15_09:00");
        assertThat(conflict.getResourceCode()).isEqualTo("SALLE-A1");
        assertThat(conflict.getRequestIdsList()).containsExactly("1", "2");
    }

    @Test
    void evaluate_shouldDetectTeacherDoubleBookingAcrossRoles() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_09:00", "SALLE-A2", "PROF-04", "PROF-01", "PROF-06")
        );

        List<Conflict> conflicts = evaluator.evaluate(assignments);

        assertThat(conflicts).hasSize(1);
        Conflict conflict = conflicts.get(0);
        assertThat(conflict.getType()).isEqualTo(ConflictType.TEACHER_DOUBLE_BOOKING);
        assertThat(conflict.getSlotCode()).isEqualTo("2026-06-15_09:00");
        assertThat(conflict.getResourceCode()).isEqualTo("PROF-01");
        assertThat(conflict.getRequestIdsList()).containsExactly("1", "2");
    }

    @Test
    void evaluate_shouldDetectRoomAndTeacherConflictsSimultaneously() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-04", "PROF-05")
        );

        List<Conflict> conflicts = evaluator.evaluate(assignments);

        assertThat(conflicts).hasSize(2);
        assertThat(conflicts)
                .extracting(Conflict::getType, Conflict::getResourceCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ConflictType.ROOM_DOUBLE_BOOKING, "SALLE-A1"),
                        org.assertj.core.groups.Tuple.tuple(ConflictType.TEACHER_DOUBLE_BOOKING, "PROF-01")
                );
    }

    @Test
    void computeBalanceScore_withEmptyList_shouldReturnZero() {
        assertThat(evaluator.computeBalanceScore(List.of())).isEqualTo(0.0);
    }

    @Test
    void computeBalanceScore_withUniformTeacherLoad_shouldReturnZero() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_10:00", "SALLE-A1", "PROF-04", "PROF-05", "PROF-06")
        );

        assertThat(evaluator.computeBalanceScore(assignments)).isEqualTo(0.0);
    }

    @Test
    void computeBalanceScore_withUnevenTeacherLoad_shouldReturnStandardDeviation() {
        List<ProposedAssignment> assignments = List.of(
                assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"),
                assignment("2", "2026-06-15_10:00", "SALLE-A1", "PROF-01", "PROF-04", "PROF-05")
        );

        assertThat(evaluator.computeBalanceScore(assignments)).isEqualTo(0.4);
    }

    private ProposedAssignment assignment(String id, String slot, String room,
                                          String sup, String pres, String rap) {
        return ProposedAssignment.newBuilder()
                .setRequestId(id)
                .setSlotCode(slot)
                .setRoomCode(room)
                .setSupervisorCode(sup)
                .setPresidentCode(pres)
                .setRapporteurCode(rap)
                .build();
    }
}