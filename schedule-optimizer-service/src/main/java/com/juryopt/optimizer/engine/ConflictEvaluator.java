package com.juryopt.optimizer.engine;

import com.juryopt.optimizer.grpc.Conflict;
import com.juryopt.optimizer.grpc.ConflictType;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Évalue un planning proposé et détecte les conflits :
 * - Double réservation d'une même salle sur un même créneau.
 * - Double réservation d'un même enseignant sur un même créneau.
 */
public class ConflictEvaluator {

    public List<Conflict> evaluate(List<ProposedAssignment> assignments) {
        List<Conflict> conflicts = new ArrayList<>();

        // Clé : "slotCode|roomCode" -> liste des request_id affectés
        Map<String, List<String>> roomBookings = new HashMap<>();
        // Clé : "slotCode|teacherCode" -> liste des request_id affectés
        Map<String, List<String>> teacherBookings = new HashMap<>();

        for (ProposedAssignment a : assignments) {
            String roomKey = a.getSlotCode() + "|" + a.getRoomCode();
            roomBookings.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(a.getRequestId());

            for (String teacher : extractTeachers(a)) {
                String teacherKey = a.getSlotCode() + "|" + teacher;
                teacherBookings.computeIfAbsent(teacherKey, k -> new ArrayList<>()).add(a.getRequestId());
            }
        }

        // 1. Conflits de salle
        for (Map.Entry<String, List<String>> entry : roomBookings.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("\\|", 2);
                conflicts.add(Conflict.newBuilder()
                        .setType(ConflictType.ROOM_DOUBLE_BOOKING)
                        .setSlotCode(parts[0])
                        .setResourceCode(parts[1])
                        .addAllRequestIds(entry.getValue())
                        .build());
            }
        }

        // 2. Conflits d'enseignant
        for (Map.Entry<String, List<String>> entry : teacherBookings.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("\\|", 2);
                conflicts.add(Conflict.newBuilder()
                        .setType(ConflictType.TEACHER_DOUBLE_BOOKING)
                        .setSlotCode(parts[0])
                        .setResourceCode(parts[1])
                        .addAllRequestIds(entry.getValue())
                        .build());
            }
        }

        return conflicts;
    }

    /**
     * Calcule l'écart-type de la charge de jury par enseignant (0.0 = parfaitement équilibré).
     */
    public double computeBalanceScore(List<ProposedAssignment> assignments) {
        if (assignments.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> loadByTeacher = new HashMap<>();
        for (ProposedAssignment a : assignments) {
            for (String teacher : extractTeachers(a)) {
                loadByTeacher.merge(teacher, 1, Integer::sum);
            }
        }

        double mean = loadByTeacher.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        double variance = loadByTeacher.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average()
                .orElse(0.0);

        return Math.round(Math.sqrt(variance) * 1000.0) / 1000.0;
    }

    private List<String> extractTeachers(ProposedAssignment a) {
        List<String> teachers = new ArrayList<>(3);
        if (!a.getSupervisorCode().isBlank()) teachers.add(a.getSupervisorCode());
        if (!a.getPresidentCode().isBlank()) teachers.add(a.getPresidentCode());
        if (!a.getRapporteurCode().isBlank()) teachers.add(a.getRapporteurCode());
        return teachers;
    }
}