package com.juryopt.orchestrator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juryopt.optimizer.grpc.OptimizationProgress;
import com.juryopt.optimizer.grpc.OptimizationStage;
import com.juryopt.optimizer.grpc.ProposedAssignment;
import com.juryopt.optimizer.grpc.ScheduleOptimizerGrpc;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.DefenseInput;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.OptimizationResult;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.SlotInput;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.grpc.Status;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = OptimizerGrpcClientResilienceTest.TestConfig.class)
class OptimizerGrpcClientResilienceTest {

    @Configuration
    @Import({
            AopAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            OptimizerGrpcClient.class
    })
    static class TestConfig {
    }

    @Autowired
    private OptimizerGrpcClient optimizerGrpcClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private ScheduleOptimizerGrpc.ScheduleOptimizerBlockingStub blockingStub;

    private final List<DefenseInput> defenses = List.of(
            new DefenseInput("1", "PROF-01", "PROF-02", "PROF-03"),
            new DefenseInput("2", "PROF-01", "PROF-04", "PROF-05")
    );

    private final List<SlotInput> slots = List.of(
            new SlotInput("2026-06-15_09:00", "SALLE-A1"),
            new SlotInput("2026-06-15_10:00", "SALLE-A1")
    );

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("optimizerGrpc").reset();
        blockingStub = mock(ScheduleOptimizerGrpc.ScheduleOptimizerBlockingStub.class);
        when(blockingStub.withDeadlineAfter(eq(5L), eq(TimeUnit.SECONDS))).thenReturn(blockingStub);
        optimizerGrpcClient.setBlockingStub(blockingStub);
    }

    @Test
    void optimizeSchedule_onSuccess_shouldApplyDeadlineAndCollectStreamSteps() {
        OptimizationProgress step1 = OptimizationProgress.newBuilder()
                .setStage(OptimizationStage.GREEDY_PLACEMENT)
                .setPassNumber(0)
                .setAssignedCount(2)
                .setConflictCount(0)
                .setBalanceScore(0.4)
                .build();
        OptimizationProgress completed = OptimizationProgress.newBuilder()
                .setStage(OptimizationStage.COMPLETED)
                .setPassNumber(3)
                .setAssignedCount(2)
                .setConflictCount(0)
                .setBalanceScore(0.4)
                .addAssignments(ProposedAssignment.newBuilder()
                        .setRequestId("1").setSlotCode("2026-06-15_09:00").setRoomCode("SALLE-A1")
                        .setSupervisorCode("PROF-01").setPresidentCode("PROF-02").setRapporteurCode("PROF-03")
                        .build())
                .addAssignments(ProposedAssignment.newBuilder()
                        .setRequestId("2").setSlotCode("2026-06-15_10:00").setRoomCode("SALLE-A1")
                        .setSupervisorCode("PROF-01").setPresidentCode("PROF-04").setRapporteurCode("PROF-05")
                        .build())
                .build();

        when(blockingStub.optimizeSchedule(any())).thenReturn(List.of(step1, completed).iterator());

        OptimizationResult result = optimizerGrpcClient.optimizeSchedule(defenses, slots);

        verify(blockingStub).withDeadlineAfter(5L, TimeUnit.SECONDS);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.steps()).hasSize(2);
        assertThat(result.assignments()).hasSize(2);
        assertThat(result.conflictCount()).isZero();
    }

    @Test
    void optimizeSchedule_onUnavailable_shouldTriggerSequentialFallback() {
        when(blockingStub.optimizeSchedule(any()))
                .thenThrow(Status.UNAVAILABLE.withDescription("Connection refused").asRuntimeException());

        OptimizationResult result = optimizerGrpcClient.optimizeSchedule(defenses, slots);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.assignments()).hasSize(2);
        assertThat(result.assignments().get(0).slotCode()).isEqualTo("2026-06-15_09:00");
        assertThat(result.assignments().get(1).slotCode()).isEqualTo("2026-06-15_10:00");
        assertThat(circuitBreakerRegistry.circuitBreaker("optimizerGrpc")
                .getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void optimizeSchedule_whenStreamEndsWithoutCompleted_shouldTriggerSequentialFallback() {
        OptimizationProgress incompleteStep = OptimizationProgress.newBuilder()
                .setStage(OptimizationStage.GREEDY_PLACEMENT)
                .setPassNumber(0)
                .setAssignedCount(2)
                .setConflictCount(1)
                .setBalanceScore(0.5)
                .build();

        when(blockingStub.optimizeSchedule(any())).thenReturn(List.of(incompleteStep).iterator());

        OptimizationResult result = optimizerGrpcClient.optimizeSchedule(defenses, slots);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.assignments()).hasSize(2);
        assertThat(circuitBreakerRegistry.circuitBreaker("optimizerGrpc")
                .getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void optimizeSchedule_onFailedPrecondition_shouldNotFallbackAndNotOpenCircuit() {
        when(blockingStub.optimizeSchedule(any()))
                .thenAnswer(inv -> {
                    throw Status.FAILED_PRECONDITION
                            .withDescription("Créneaux insuffisants")
                            .asRuntimeException();
                });

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> optimizerGrpcClient.optimizeSchedule(defenses, slots))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Créneaux insuffisants");
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("optimizerGrpc");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void evaluateSchedule_onDeadlineExceeded_shouldThrow503WithoutFakeValidResult() {
        when(blockingStub.evaluateSchedule(any()))
                .thenThrow(Status.DEADLINE_EXCEEDED.withDescription("Deadline exceeded").asRuntimeException());

        List<ProposedAssignmentDto> assignments = List.of(
                new ProposedAssignmentDto("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03")
        );

        assertThatThrownBy(() -> optimizerGrpcClient.evaluateSchedule(assignments))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }
}