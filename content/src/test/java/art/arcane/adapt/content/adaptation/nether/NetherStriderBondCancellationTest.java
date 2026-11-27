package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Subscription;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class NetherStriderBondCancellationTest extends EngineTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeCancelledDismountCannotScheduleRescue(boolean cancelled) {
        NetherStriderBond bond = spy(TestContent.adaptation(Id.adapt("nether"), NetherStriderBond::new));
        PlayerRef rider = mock(PlayerRef.class);
        when(rider.uuid()).thenReturn(UUID.randomUUID());
        EntityRef strider = mock(EntityRef.class);
        when(strider.type()).thenReturn(Id.minecraft("strider"));
        doReturn(5).when(bond).getActiveLevel(rider);
        doReturn(true).when(bond).preferenceEnabled(rider, NetherStriderBond.RESCUE);
        EntityDismountEvent event = new EntityDismountEvent(rider, strider, true);
        event.setCancelled(cancelled);
        AtomicBoolean scheduled = new AtomicBoolean();
        AdaptScheduler scheduler = core.getPlatform().scheduler();
        when(scheduler.runEntity(same(rider), any(Runnable.class), eq(2L), any())).thenAnswer(call -> {
            scheduled.set(true);
            return true;
        });

        AdaptEventBus bus = core.getBus();
        core.getRegistrar().register(bond);
        try {
            int handlers = 0;
            for (Subscription subscription : bus.subscriptions(bond)) {
                if (subscription.eventType() != EntityDismountEvent.class) {
                    continue;
                }
                assertThat(subscription.priority()).isEqualTo(EventPriority.MONITOR);
                handlers++;
            }
            bus.post(event);

            assertThat(handlers).isEqualTo(1);
            assertThat(scheduled.get()).isEqualTo(!cancelled);
        } finally {
            core.getRegistrar().unregister(bond);
        }
    }
}
