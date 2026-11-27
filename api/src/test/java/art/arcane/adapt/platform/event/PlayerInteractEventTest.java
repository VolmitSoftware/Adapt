package art.arcane.adapt.platform.event;

import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInteractEventTest {
  @Test
  void cancellationFollowsBlockUseAsOnBukkit() {
    PlayerInteractEvent event = new PlayerInteractEvent(Stubs.player(), InteractAction.RIGHT_CLICK_AIR, null, null, null,
        null, null, UseResult.DENY, UseResult.DEFAULT);
    assertTrue(event.isCancelled());
    assertFalse(event.isUseDenied());
    event.setUseItem(UseResult.DENY);
    assertTrue(event.isUseDenied());
    event.setUseBlock(UseResult.ALLOW);
    assertFalse(event.isCancelled());
  }

  @Test
  void cancelDeniesBothAndUncancelResetsDenied() {
    PlayerInteractEvent event = new PlayerInteractEvent(Stubs.player(), InteractAction.RIGHT_CLICK_AIR, null, null, null,
        null, null, UseResult.ALLOW, UseResult.DEFAULT);
    event.setCancelled(true);
    assertEquals(UseResult.DENY, event.useBlock());
    assertEquals(UseResult.DENY, event.useItem());
    event.setCancelled(false);
    assertEquals(UseResult.DEFAULT, event.useBlock());
    assertEquals(UseResult.DEFAULT, event.useItem());
  }

  @Test
  void busSkipsVetoedInteractionsForIgnoreCancelledHandlers() {
    AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> { });
    int[] hits = new int[2];
    bus.register(this, PlayerInteractEvent.class, EventPriority.NORMAL, true, e -> hits[0]++);
    bus.register(this, PlayerInteractEvent.class, EventPriority.NORMAL, false, e -> hits[1]++);
    PlayerInteractEvent event = new PlayerInteractEvent(Stubs.player(), InteractAction.RIGHT_CLICK_AIR, null, null, null,
        null, null, UseResult.DENY, UseResult.DEFAULT);

    bus.post(event);

    assertEquals(0, hits[0]);
    assertEquals(1, hits[1]);
  }
}
