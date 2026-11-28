git diff --statpackage com.juryopt.optimizer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.juryopt.optimizer.domain.OptimizationRun;
import com.juryopt.optimizer.repository.OptimizationRunRepository;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleOptimizerGrpcServiceInProcessTest {

    @Mock
    private OptimizationRunRepository runRepository;

    private Server server;
    private ManagedChannel channel;
    private ScheduleOptimizerGrpc.ScheduleOptimizerBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new ScheduleOptimizerGrpcService(runRepository))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        blockingStub = ScheduleOptimizerGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void evaluateSchedule_withConflicts_shouldReturnRoomAndTeacherConflicts() {
        EvaluateScheduleRequest request = EvaluateScheduleRequest.newBuilder()
                .addAssignments(assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"))
                .addAssignments(assignment("2", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-04", "PROF-05"))
                .build();

        EvaluateScheduleResponse response = blockingStub.evaluateSchedule(request);

        assertThat(response.getConflictsList())
                .hasSize(2)
                .extracting(Conflict::getType, Conflict::getResourceCode)
                .containsExactlyInAnyOrder(
                        tuple(ConflictType.ROOM_DOUBLE_BOOKING, "SALLE-A1"),
                        tuple(ConflictType.TEACHER_DOUBLE_BOOKING, "PROF-01")
                );
    }

    @Test
    void evaluateSchedule_withoutConflicts_shouldReturnEmptyList() {
        EvaluateScheduleRequest request = EvaluateScheduleRequest.newBuilder()
                .addAssignments(assignment("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"))
                .addAssignments(assignment("2", "2026-06-15_10:00", "SALLE-A1", "PROF-01", "PROF-04", "PROF-05"))
                .build();

        EvaluateScheduleResponse response = blockingStub.evaluateSchedule(request);

        assertThat(response.getConflictsList()).isEmpty();
    }

    @Test
    void evaluateSchedule_withEmptyAssignments_shouldReturnInvalidArgument() {
        EvaluateScheduleRequest request = EvaluateScheduleRequest.newBuilder().build();

        assertThatThrownBy(() -> blockingStub.evaluateSchedule(request))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void optimizeSchedule_shouldStreamFiveStagesAndPersistRun() {
        OptimizeScheduleRequest request = OptimizeScheduleRequest.newBuilder()
                .addDefenses(defense("1", "PROF-01", "PROF-02", "PROF-03"))
                .addDefenses(defense("2", "PROF-04", "PROF-05", "PROF-06"))
                .addDefenses(defense("3", "PROF-01", "PROF-07", "PROF-08"))
                .addDefenses(defense("4", "PROF-07", "PROF-04", "PROF-09"))
                .addSlots(slot("2026-06-15_09:00", "SALLE-A1"))
                .addSlots(slot("2026-06-15_09:00", "SALLE-A2"))
                .addSlots(slot("2026-06-15_10:00", "SALLE-A1"))
                .addSlots(slot("2026-06-15_10:00", "SALLE-A2"))
                .build();

        List<OptimizationProgress> progressEvents = new ArrayList<>();
        Iterator<OptimizationProgress> stream = blockingStub.optimizeSchedule(request);
        while (stream.hasNext()) {
            progressEvents.add(stream.next());
        }

        assertThat(progressEvents)
                .hasSize(5)
                .extracting(OptimizationProgress::getStage)
                .containsExactly(
                        OptimizationStage.GREEDY_PLACEMENT,
                        OptimizationStage.LOAD_BALANCING_PASS,
                        OptimizationStage.LOAD_BALANCING_PASS,
                        OptimizationStage.LOAD_BALANCING_PASS,
                        OptimizationStage.COMPLETED
                );

        OptimizationProgress completed = progressEvents.get(4);
        assertThat(completed.getAssignedCount()).isEqualTo(4);
        assertThat(completed.getConflictCount()).isZero();
        assertThat(completed.getAssignmentsList()).hasSize(4);

        ArgumentCaptor<OptimizationRun> captor = ArgumentCaptor.forClass(OptimizationRun.class);
        verify(runRepository).save(captor.capture());
        OptimizationRun saved = captor.getValue();
        assertThat(saved.getRequestCount()).isEqualTo(4);
        assertThat(saved.getAssignedCount()).isEqualTo(4);
        assertThat(saved.getInitialConflicts()).isEqualTo(1);
        assertThat(saved.getFinalConflicts()).isZero();
        assertThat(saved.getExecutedAt()).isNotNull();
    }

    @Test
    void optimizeSchedule_withEmptyDefensesOrSlots_shouldReturnInvalidArgument() {
        OptimizeScheduleRequest request = OptimizeScheduleRequest.newBuilder()
                .addSlots(slot("2026-06-15_09:00", "SALLE-A1"))
                .build();

        assertThatThrownBy(() -> {
            Iterator<OptimizationProgress> stream = blockingStub.optimizeSchedule(request);
            while (stream.hasNext()) {
                stream.next();
            }
        })
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);

        verifyNoInteractions(runRepository);
    }

    @Test
    void optimizeSchedule_withInsufficientSlots_shouldReturnFailedPrecondition() {
        OptimizeScheduleRequest request = OptimizeScheduleRequest.newBuilder()
                .addDefenses(defense("1", "PROF-01", "PROF-02", "PROF-03"))
                .addDefenses(defense("2", "PROF-04", "PROF-05", "PROF-06"))
                .addSlots(slot("2026-06-15_09:00", "SALLE-A1"))
                .build();

        assertThatThrownBy(() -> {
            Iterator<OptimizationProgress> stream = blockingStub.optimizeSchedule(request);
            while (stream.hasNext()) {
                stream.next();
            }
        })
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);

        verifyNoInteractions(runRepository);
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