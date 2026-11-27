package com.juryopt.orchestrator.client;

import com.juryopt.optimizer.grpc.Conflict;
import com.juryopt.optimizer.grpc.DefenseItem;
import com.juryopt.optimizer.grpc.EvaluateScheduleRequest;
import com.juryopt.optimizer.grpc.EvaluateScheduleResponse;
import com.juryopt.optimizer.grpc.OptimizationProgress;
import com.juryopt.optimizer.grpc.OptimizationStage;
import com.juryopt.optimizer.grpc.OptimizeScheduleRequest;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import com.juryopt.optimizer.grpc.ScheduleOptimizerGrpc;
import com.juryopt.optimizer.grpc.SlotItem;
import com.juryopt.orchestrator.dto.ConflictDto;
import com.juryopt.orchestrator.dto.OptimizationStepDto;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OptimizerGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(OptimizerGrpcClient.class);

    @GrpcClient("schedule-optimizer")
    private ScheduleOptimizerGrpc.ScheduleOptimizerBlockingStub blockingStub;

    private final long deadlineSeconds;

    public OptimizerGrpcClient(
            @Value("${juryopt.grpc.optimizer-deadline-seconds:5}") long deadlineSeconds) {
        this.deadlineSeconds = deadlineSeconds;
    }

    public void setBlockingStub(ScheduleOptimizerGrpc.ScheduleOptimizerBlockingStub blockingStub) {
        this.blockingStub = blockingStub;
    }

    public record DefenseInput(
            String requestId,
            String supervisorCode,
            String presidentCode,
            String rapporteurCode
    ) {
    }

    public record SlotInput(String slotCode, String roomCode) {
    }

    public record OptimizationResult(
            boolean fallbackUsed,
            int assignedCount,
            int conflictCount,
            double balanceScore,
            List<OptimizationStepDto> steps,
            List<ProposedAssignmentDto> assignments
    ) {
    }

    @CircuitBreaker(name = "optimizerGrpc", fallbackMethod = "optimizeScheduleFallback")
    public OptimizationResult optimizeSchedule(List<DefenseInput> defenses, List<SlotInput> slots) {
        log.info("Appel gRPC streaming OptimizeSchedule ({} soutenances, {} créneaux, deadline={}s)",
                defenses.size(), slots.size(), deadlineSeconds);

        OptimizeScheduleRequest.Builder reqBuilder = OptimizeScheduleRequest.newBuilder();
        for (DefenseInput defense : defenses) {
            reqBuilder.addDefenses(DefenseItem.newBuilder()
                    .setRequestId(defense.requestId())
                    .setSupervisorCode(defense.supervisorCode())
                    .setPresidentCode(defense.presidentCode())
                    .setRapporteurCode(defense.rapporteurCode())
                    .build());
        }
        for (SlotInput slot : slots) {
            reqBuilder.addSlots(SlotItem.newBuilder()
                    .setSlotCode(slot.slotCode())
                    .setRoomCode(slot.roomCode())
                    .build());
        }

        try {
            Iterator<OptimizationProgress> stream = blockingStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .optimizeSchedule(reqBuilder.build());

            List<OptimizationStepDto> steps = new ArrayList<>();
            OptimizationProgress completed = null;

            while (stream.hasNext()) {
                OptimizationProgress progress = stream.next();
                steps.add(new OptimizationStepDto(
                        progress.getStage().name(),
                        progress.getPassNumber(),
                        progress.getAssignedCount(),
                        progress.getConflictCount(),
                        progress.getBalanceScore()
                ));
                if (progress.getStage() == OptimizationStage.COMPLETED) {
                    completed = progress;
                }
            }

            if (completed == null) {
                throw new ExternalServiceUnavailableException(
                        "Le flux gRPC d'optimisation s'est arrêté avant l'étape COMPLETED."
                );
            }

            List<ProposedAssignmentDto> assignments = completed.getAssignmentsList().stream()
                    .map(a -> new ProposedAssignmentDto(
                            a.getRequestId(),
                            a.getSlotCode(),
                            a.getRoomCode(),
                            a.getSupervisorCode(),
                            a.getPresidentCode(),
                            a.getRapporteurCode()
                    ))
                    .toList();

            return new OptimizationResult(
                    false,
                    completed.getAssignedCount(),
                    completed.getConflictCount(),
                    completed.getBalanceScore(),
                    steps,
                    assignments
            );
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("optimisation", ex);
        }
    }

    @CircuitBreaker(name = "optimizerGrpc", fallbackMethod = "evaluateScheduleFallback")
    public List<ConflictDto> evaluateSchedule(List<ProposedAssignmentDto> assignments) {
        log.info("Appel gRPC unaire EvaluateSchedule ({} affectations, deadline={}s)",
                assignments.size(), deadlineSeconds);

        EvaluateScheduleRequest.Builder reqBuilder = EvaluateScheduleRequest.newBuilder();
        for (ProposedAssignmentDto a : assignments) {
            reqBuilder.addAssignments(ProposedAssignment.newBuilder()
                    .setRequestId(a.requestId())
                    .setSlotCode(a.slotCode())
                    .setRoomCode(a.roomCode())
                    .setSupervisorCode(a.supervisorCode())
                    .setPresidentCode(a.presidentCode())
                    .setRapporteurCode(a.rapporteurCode())
                    .build());
        }

        try {
            EvaluateScheduleResponse response = blockingStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .evaluateSchedule(reqBuilder.build());

            return response.getConflictsList().stream()
                    .map(this::toConflictDto)
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw translateGrpcException("évaluation", ex);
        }
    }

    private OptimizationResult optimizeScheduleFallback(List<DefenseInput> defenses,
                                                        List<SlotInput> slots,
                                                        Throwable ex) {
        rethrowIfBusinessException(ex);

        if (slots.size() < defenses.size()) {
            throw new BusinessRuleException(
                    "Créneaux insuffisants : " + slots.size() + " disponibles pour " + defenses.size() + " soutenances."
            );
        }

        log.warn("Repli Resilience4j gRPC OptimizeSchedule ({}: {}) -> affectation séquentielle UNOPTIMIZED_FALLBACK",
                ex.getClass().getSimpleName(), ex.getMessage());

        List<ProposedAssignmentDto> assignments = new ArrayList<>(defenses.size());
        for (int i = 0; i < defenses.size(); i++) {
            DefenseInput d = defenses.get(i);
            SlotInput s = slots.get(i);
            assignments.add(new ProposedAssignmentDto(
                    d.requestId(),
                    s.slotCode(),
                    s.roomCode(),
                    d.supervisorCode(),
                    d.presidentCode(),
                    d.rapporteurCode()
            ));
        }

        int conflictCount = countFallbackConflicts(assignments);
        double balanceScore = computeFallbackBalanceScore(assignments);

        return new OptimizationResult(
                true,
                assignments.size(),
                conflictCount,
                balanceScore,
                List.of(),
                assignments
        );
    }

    private List<ConflictDto> evaluateScheduleFallback(List<ProposedAssignmentDto> assignments, Throwable ex) {
        rethrowIfBusinessException(ex);
        log.warn("Service gRPC EvaluateSchedule indisponible ({}: {}) -> 503",
                ex.getClass().getSimpleName(), ex.getMessage());
        if (ex instanceof ExternalServiceUnavailableException unavailable) {
            throw unavailable;
        }
        throw new ExternalServiceUnavailableException(
                "Le service d'optimisation gRPC est temporairement indisponible pour évaluer le planning.",
                ex
        );
    }

    private void rethrowIfBusinessException(Throwable ex) {
        if (ex instanceof BusinessRuleException e) {
            throw e;
        }
        if (ex instanceof ResourceNotFoundException e) {
            throw e;
        }
        if (ex instanceof DuplicateResourceException e) {
            throw e;
        }
    }

    private RuntimeException translateGrpcException(String operation, StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        String description = ex.getStatus().getDescription();
        String message = (description != null && !description.isBlank())
                ? description
                : "Erreur gRPC (" + code + ") lors de l'opération d'" + operation + ".";

        if (code == Status.Code.INVALID_ARGUMENT || code == Status.Code.FAILED_PRECONDITION) {
            return new BusinessRuleException(message);
        }

        return new ExternalServiceUnavailableException(
                "Service d'optimisation indisponible (" + code + ") : " + message,
                ex
        );
    }

    private ConflictDto toConflictDto(Conflict conflict) {
        return new ConflictDto(
                conflict.getType().name(),
                conflict.getSlotCode(),
                conflict.getResourceCode(),
                List.copyOf(conflict.getRequestIdsList())
        );
    }

    private int countFallbackConflicts(List<ProposedAssignmentDto> assignments) {
        Map<String, Integer> roomCounts = new HashMap<>();
        Map<String, Integer> teacherCounts = new HashMap<>();

        for (ProposedAssignmentDto a : assignments) {
            roomCounts.merge(a.slotCode() + "|" + a.roomCode(), 1, Integer::sum);
            teacherCounts.merge(a.slotCode() + "|" + a.supervisorCode(), 1, Integer::sum);
            teacherCounts.merge(a.slotCode() + "|" + a.presidentCode(), 1, Integer::sum);
            teacherCounts.merge(a.slotCode() + "|" + a.rapporteurCode(), 1, Integer::sum);
        }

        long roomConflicts = roomCounts.values().stream().filter(c -> c > 1).count();
        long teacherConflicts = teacherCounts.values().stream().filter(c -> c > 1).count();
        return (int) (roomConflicts + teacherConflicts);
    }

    private double computeFallbackBalanceScore(List<ProposedAssignmentDto> assignments) {
        if (assignments.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> loadByTeacher = new HashMap<>();
        for (ProposedAssignmentDto a : assignments) {
            loadByTeacher.merge(a.supervisorCode(), 1, Integer::sum);
            loadByTeacher.merge(a.presidentCode(), 1, Integer::sum);
            loadByTeacher.merge(a.rapporteurCode(), 1, Integer::sum);
        }
        double mean = loadByTeacher.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double variance = loadByTeacher.values().stream()
                .mapToDouble(c -> Math.pow(c - mean, 2))
                .average()
                .orElse(0.0);
        return Math.round(Math.sqrt(variance) * 1000.0) / 1000.0;
    }
}