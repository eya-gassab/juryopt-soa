package com.juryopt.optimizer.grpc;

import com.juryopt.optimizer.domain.OptimizationRun;
import com.juryopt.optimizer.engine.ConflictEvaluator;
import com.juryopt.optimizer.engine.GreedySwapOptimizer;
import com.juryopt.optimizer.engine.GreedySwapOptimizer.OptimizationSummary;
import com.juryopt.optimizer.repository.OptimizationRunRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDateTime;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Exposition gRPC du moteur d'optimisation (port 9090).
 */
@GrpcService
public class ScheduleOptimizerGrpcService extends ScheduleOptimizerGrpc.ScheduleOptimizerImplBase {

    private final ConflictEvaluator conflictEvaluator;
    private final GreedySwapOptimizer optimizer;
    private final OptimizationRunRepository runRepository;

    public ScheduleOptimizerGrpcService(OptimizationRunRepository runRepository) {
        this.conflictEvaluator = new ConflictEvaluator();
        this.optimizer = new GreedySwapOptimizer(this.conflictEvaluator);
        this.runRepository = runRepository;
    }

    /**
     * RPC Unaire : détecte les conflits d'un planning proposé.
     */
    @Override
    public void evaluateSchedule(
            EvaluateScheduleRequest request,
            StreamObserver<EvaluateScheduleResponse> responseObserver) {

        if (request.getAssignmentsList().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("La liste des affectations ne peut pas être vide.")
                    .asRuntimeException());
            return;
        }

        try {
            List<Conflict> conflicts = conflictEvaluator.evaluate(request.getAssignmentsList());

            EvaluateScheduleResponse response = EvaluateScheduleResponse.newBuilder()
                    .addAllConflicts(conflicts)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erreur lors de l'évaluation du planning : " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * RPC Streaming serveur : optimise le planning en diffusant chaque étape
     * et archive le résumé dans optimizer_db.
     */
    @Override
    public void optimizeSchedule(
            OptimizeScheduleRequest request,
            StreamObserver<OptimizationProgress> responseObserver) {

        if (request.getDefensesList().isEmpty() || request.getSlotsList().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Les listes de soutenances et de créneaux sont obligatoires.")
                    .asRuntimeException());
            return;
        }

        if (request.getSlotsCount() < request.getDefensesCount()) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("Créneaux insuffisants : " + request.getSlotsCount()
                            + " disponibles pour " + request.getDefensesCount() + " soutenances.")
                    .asRuntimeException());
            return;
        }

        try {
            OptimizationSummary summary = optimizer.optimize(
                    request.getDefensesList(),
                    request.getSlotsList(),
                    responseObserver::onNext
            );

            runRepository.save(new OptimizationRun(
                    summary.requestCount(),
                    summary.assignedCount(),
                    summary.initialConflicts(),
                    summary.finalConflicts(),
                    summary.balanceScore(),
                    LocalDateTime.now()
            ));

            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erreur lors de l'optimisation : " + e.getMessage())
                    .asRuntimeException());
        }
    }
}