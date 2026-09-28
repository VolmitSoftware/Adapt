package art.arcane.adapt.util.reflect.events;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReflectiveEventsTest extends AdaptTestBase {
    private final NativeListener listener = new NativeListener();

    @AfterEach
    void unregister() {
        HandlerList.unregisterAll(listener);
    }

    @Test
    void nativeStareDispatchesAndCancellationChangesTheRealEvent() throws EventException {
        Player player = mock(Player.class);
        Enderman enderman = mock(Enderman.class);
        EndermanAttackPlayerEvent event = new EndermanAttackPlayerEvent(enderman, player);
        ReflectiveEvents.register(listener);

        dispatch(event);

        assertThat(listener.player).isSameAs(player);
        assertThat(listener.source).isSameAs(enderman);
        assertThat(listener.asynchronous).isFalse();
        assertThat(event.isCancelled()).isTrue();
        assertThat(listener.stares).isEqualTo(1);
        dispatch(event);
        assertThat(listener.stares).isEqualTo(1);
    }

    @Test
    void mountAndDismountReadTheirNativeEntities() throws EventException {
        Entity rider = mock(Entity.class);
        Entity mount = mock(Entity.class);
        ReflectiveEvents.register(listener);

        dispatch(new EntityMountEvent(rider, mount));
        dispatch(new EntityDismountEvent(rider, mount));

        assertThat(listener.source).isSameAs(rider);
        assertThat(listener.mounted).isSameAs(mount);
        assertThat(listener.dismounted).isSameAs(mount);
    }

    private void dispatch(Event event) throws EventException {
        int registered = 0;
        for (RegisteredListener registration : event.getHandlers().getRegisteredListeners()) {
            if (registration.getListener() == listener) {
                registration.callEvent(event);
                registered++;
            }
        }
        assertThat(registered).isEqualTo(1);
    }

    public static class NativeListener implements Listener {
        private Player player;
        private Entity source;
        private Entity mounted;
        private Entity dismounted;
        private boolean asynchronous;
        private int stares;

        @ReflectiveHandler(ignoreCancelled = true)
        public void onStare(art.arcane.adapt.util.reflect.events.api.entity.EndermanAttackPlayerEvent event) {
            player = event.getPlayer();
            source = event.getEntity();
            asynchronous = event.isAsynchronous();
            stares++;
            event.setCancelled(true);
        }

        @ReflectiveHandler
        public void onMount(art.arcane.adapt.util.reflect.events.api.entity.EntityMountEvent event) {
            source = event.getEntity();
            mounted = event.getMount();
        }

        @ReflectiveHandler
        public void onDismount(art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent event) {
            source = event.getEntity();
            dismounted = event.getDismounted();
        }
    }
}
