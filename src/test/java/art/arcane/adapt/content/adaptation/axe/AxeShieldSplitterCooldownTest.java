package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeShieldSplitterCooldownTest extends AdaptTestBase {
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
        AxeShieldSplitter adaptation = spy(new QuietSplitter());
        Player attacker = mock(Player.class);
        Player blocker = mock(Player.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(blocker.isBlocking()).thenReturn(true);
        when(blocker.isOnline()).thenReturn(true);
        when(blocker.isValid()).thenReturn(true);
        when(blocker.getLocation()).thenReturn(new Location(mock(World.class), 0, 100, 0));
        when(event.getDamage()).thenReturn(6D);
        doReturn(true).when(adaptation).isRuntimeRegistered();
        doReturn(new Adaptation.MeleeContext(attacker, blocker, mock(ItemStack.class), 4))
                .when(adaptation).resolveMeleeContext(eq(event), any());
        List<Runnable> pending = new ArrayList<>();
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(same(blocker), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
                pending.add(invocation.getArgument(1));
                return true;
            });
            assertThatThrownBy(() -> adaptation.on(event)).isInstanceOf(FeedbackReached.class);
            assertThat(pending).hasSize(1);
            verify(blocker, never()).setCooldown(eq(Material.SHIELD), anyInt());
            when(blocker.getCooldown(Material.SHIELD)).thenReturn(vanillaRemaining);
            switch (state) {
                case "cancelled" -> when(event.isCancelled()).thenReturn(true);
                case "offline" -> when(blocker.isOnline()).thenReturn(false);
                case "invalid" -> when(blocker.isValid()).thenReturn(false);
                case "dead" -> when(blocker.isDead()).thenReturn(true);
                case "unregistered" -> doReturn(false).when(adaptation).isRuntimeRegistered();
                default -> { }
            }
            pending.getFirst().run();
            if (expected >= 0) verify(blocker).setCooldown(Material.SHIELD, expected);
            else verify(blocker, never()).setCooldown(eq(Material.SHIELD), anyInt());
        }
    }

    private static final class FeedbackReached extends RuntimeException {
    }

    private static final class QuietSplitter extends AxeShieldSplitter {
        @Override
        protected FxEmitter fx(Location location, FxPriority priority) {
            throw new FeedbackReached();
        }
    }
}
