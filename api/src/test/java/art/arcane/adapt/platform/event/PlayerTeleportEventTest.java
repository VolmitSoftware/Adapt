package art.arcane.adapt.platform.event;

import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerTeleportEventTest {
  @Test
  void moveHandlersDoNotReceiveTeleports() {
    AdaptEventBus bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> { });
    int[] hits = new int[2];
    bus.register(this, PlayerMoveEvent.class, EventPriority.NORMAL, (PlayerMoveEvent event) -> hits[0]++);
    bus.register(this, PlayerTeleportEvent.class, EventPriority.NORMAL, (PlayerTeleportEvent event) -> hits[1]++);
    WorldRef world = (WorldRef) Proxy.newProxyInstance(WorldRef.class.getClassLoader(), new Class<?>[] {WorldRef.class},
        (Object proxy, Method method, Object[] args) -> switch (method.getName()) {
          case "equals" -> proxy == args[0];
          case "hashCode" -> 1;
          default -> null;
        });
    Loc origin = new Loc(world, 0, 64, 0);

    bus.post(new PlayerTeleportEvent(Stubs.player(), origin, origin.add(5, 0, 0), TeleportCause.COMMAND));

    assertEquals(0, hits[0]);
    assertEquals(1, hits[1]);
  }
}
