package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.events.ReflectiveEvents;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

class NetherStriderBondCancellationTest extends AdaptTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeCancelledDismountCannotScheduleRescue(boolean cancelled) throws EventException {
        NetherStriderBond bond = spy(new NetherStriderBond());
        Player rider = mock(Player.class);
        doReturn(5).when(bond).getActiveLevel(rider);
        doReturn(true).when(bond).preferenceEnabled(rider, NetherStriderBond.RESCUE);
        EntityDismountEvent event = new EntityDismountEvent(rider, mock(Strider.class));
        event.setCancelled(cancelled);
        AtomicBoolean scheduled = new AtomicBoolean();
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(same(rider), any(Runnable.class), eq(2))).thenAnswer(call -> {
                scheduled.set(true);
                return true;
            });
            ReflectiveEvents.register(bond);
            int handlers = 0;
            for (RegisteredListener registration : event.getHandlers().getRegisteredListeners()) {
                if (registration.getListener() != bond) {
                    continue;
                }
                assertThat(registration.getPriority()).isEqualTo(EventPriority.MONITOR);
                registration.callEvent(event);
                handlers++;
            }
            assertThat(handlers).isEqualTo(1);
            assertThat(scheduled.get()).isEqualTo(!cancelled);
        } finally {
            HandlerList.unregisterAll(bond);
        }
    }
}
