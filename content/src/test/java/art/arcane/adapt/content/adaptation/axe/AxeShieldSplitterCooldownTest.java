package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeShieldSplitterCooldownTest extends AdaptTestBase {
    private static final Id SHIELD = Id.minecraft("shield");

    @ParameterizedTest
    @CsvSource({"99,119", "179,179", "0,119"})
    void cooldownWaitsForVanillaDisableAndPreservesLongerCurrentCooldown(int vanillaRemaining, int expected) {
        assertCooldown(vanillaRemaining, expected, "active");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "offline", "invalid", "dead", "unregistered"})
    void pendingCooldownDoesNotApplyAfterCancellationOrOwnerRetirement(String state) {
        assertCooldown(99, -1, state);
    }

    private void assertCooldown(int vanillaRemaining, int expected, String state) {
        AxeShieldSplitter original = TestContent.adaptation(Id.adapt("axes"), AxeShieldSplitter::new);
        AxeShieldSplitter adaptation = spy(original);
        AdaptationHost runtime = RuntimeSpy.install(original, adaptation);
        doThrow(new FeedbackReached()).when(runtime).fx(any(Loc.class), any(FxPriority.class));
        PlayerRef attacker = mock(PlayerRef.class);
        PlayerRef blocker = mock(PlayerRef.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(attacker.uuid()).thenReturn(UUID.randomUUID());
        when(blocker.uuid()).thenReturn(UUID.randomUUID());
        when(blocker.isBlocking()).thenReturn(true);
        when(blocker.isOnline()).thenReturn(true);
        when(blocker.isValid()).thenReturn(true);
        when(blocker.location()).thenReturn(new Loc(mock(WorldRef.class), 0, 100, 0));
        when(event.damage()).thenReturn(6D);
        when(event.entity()).thenReturn(blocker);
        when(event.damager()).thenReturn(attacker);
        doReturn(true).when(adaptation).isRuntimeRegistered();
        doReturn(new Adaptation.MeleeContext(attacker, blocker, mock(ItemRef.class), 4))
                .when(adaptation).resolveMeleeContext(eq(event), any());
        List<Runnable> pending = new ArrayList<>();
        try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
            scheduling.when(() -> Tasks.runEntity(same(blocker), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
                pending.add(invocation.getArgument(1));
                return true;
            });
            assertThatThrownBy(() -> adaptation.on(event)).isInstanceOf(FeedbackReached.class);
            assertThat(pending).hasSize(1);
            verify(blocker, never()).setCooldown(eq(SHIELD), anyInt());
            when(blocker.cooldownTicks(SHIELD)).thenReturn(vanillaRemaining);
            switch (state) {
                case "cancelled" -> when(event.isCancelled()).thenReturn(true);
                case "offline" -> when(blocker.isOnline()).thenReturn(false);
                case "invalid" -> when(blocker.isValid()).thenReturn(false);
                case "dead" -> when(blocker.isDead()).thenReturn(true);
                case "unregistered" -> doReturn(false).when(adaptation).isRuntimeRegistered();
                default -> { }
            }
            adaptation.onOutcome(event);
            pending.getFirst().run();
            if (expected >= 0) verify(blocker).setCooldown(SHIELD, expected);
            else verify(blocker, never()).setCooldown(eq(SHIELD), anyInt());
        }
    }

    private static final class FeedbackReached extends RuntimeException {
    }
}
