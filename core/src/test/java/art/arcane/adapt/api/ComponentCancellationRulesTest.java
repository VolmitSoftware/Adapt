package art.arcane.adapt.api;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.content.adaptation.chronos.ChronosInstantRecall;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.EventListener;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ComponentCancellationRulesTest {
  @Test
  void componentInteractHandlersSeeCancelledAirClicksButNotDeniedUses() throws Exception {
    HandlerMethod plain = method("plain");
    assertFalse(ComponentRegistrar.ignoresCancelled(plain));
    List<PlayerInteractEvent> seen = new ArrayList<>();
    EventListener<AdaptEvent> gated = ComponentRegistrar.gate(new Object(), plain, event -> seen.add((PlayerInteractEvent) event));

    PlayerInteractEvent air = air(UseResult.DENY, UseResult.DEFAULT);
    gated.handle(air);
    PlayerInteractEvent deniedItem = air(UseResult.DENY, UseResult.DENY);
    gated.handle(deniedItem);

    assertEquals(List.of(air), seen);
  }

  @Test
  void receiveCancelledInteractHandlersSeeDeniedUses() throws Exception {
    HandlerMethod receiving = method("receiving");
    assertFalse(ComponentRegistrar.ignoresCancelled(receiving));
    List<PlayerInteractEvent> seen = new ArrayList<>();
    EventListener<AdaptEvent> gated = ComponentRegistrar.gate(new Object(), receiving, event -> seen.add((PlayerInteractEvent) event));

    PlayerInteractEvent deniedItem = air(UseResult.DENY, UseResult.DENY);
    gated.handle(deniedItem);

    assertEquals(List.of(deniedItem), seen);
  }

  @Test
  void componentDeathHandlersSkipPlatformCancelledDeathsUnlessMarked() throws Exception {
    List<String> seen = new ArrayList<>();
    EventListener<AdaptEvent> plain = ComponentRegistrar.gate(new Object(), method("death"), event -> seen.add("plain"));
    EventListener<AdaptEvent> receiving = ComponentRegistrar.gate(new Object(), method("receivingDeath"), event -> seen.add("receiving"));
    EntityDeathEvent cancelled = death();
    cancelled.setPlatformCancelled(true);

    plain.handle(cancelled);
    receiving.handle(cancelled);
    plain.handle(death());

    assertEquals(List.of("receiving", "plain"), seen);
  }

  @Test
  void otherHandlersKeepTheirDeclaredFilterOnPlatformCancelledDeathsAndCancelledEvents() {
    AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> {
      throw new AssertionError(error);
    });
    ServiceHandlers service = new ServiceHandlers();
    assertTrue(new ComponentRegistrar(bus).register(service));
    EntityDeathEvent cancelledDeath = death();
    cancelledDeath.setPlatformCancelled(true);
    Ping cancelledPing = new Ping();
    cancelledPing.setCancelled(true);

    bus.post(cancelledDeath);
    bus.post(death());
    bus.post(cancelledPing);

    assertEquals(List.of("death-receiving", "death-marked",
        "death-skipping", "death-receiving", "death-marked",
        "ping-receiving", "ping-marked"), service.seen);
  }

  @Test
  void protectionProbesReachOtherHandlersButNotComponents() throws Exception {
    AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> {
      throw new AssertionError(error);
    });
    ServiceHandlers service = new ServiceHandlers();
    new ComponentRegistrar(bus).register(service);
    List<String> component = new ArrayList<>();
    EventListener<AdaptEvent> gated = ComponentRegistrar.gate(new Object(), method("death"), event -> component.add("component"));
    EntityDeathEvent probe = death();
    probe.markProtectionProbe();

    bus.post(probe);
    gated.handle(probe);
    gated.handle(death());

    assertEquals(List.of("death-skipping", "death-receiving", "death-marked"), service.seen);
    assertEquals(List.of("component"), component);
  }

  @Test
  void serviceHandlersFilterCancelledEventsAsTheirBukkitHandlersDid() throws Exception {
    Map<String, Boolean> expected = new LinkedHashMap<>();
    expected.put("art.arcane.adapt.api.golem.GolemRegistry#EntityDeathEvent", true);
    expected.put("art.arcane.adapt.api.attribute.AdaptAttributeService#EntityDeathEvent", true);
    expected.put("art.arcane.adapt.api.attribute.AdaptAttributeService#PlayerDeathEvent", true);
    expected.put("art.arcane.adapt.content.adaptation.golemancy.RancherActions#EntityDeathEvent", true);
    expected.put("art.arcane.adapt.api.minion.MinionBurden#PlayerDeathEvent", false);
    expected.put("art.arcane.adapt.api.golem.reveal.RevealService#PlayerDeathEvent", false);
    expected.put("art.arcane.adapt.api.golem.reveal.RevealService#EntityDeathEvent", false);
    expected.put("art.arcane.adapt.api.adaptation.VelocityBurstRuntime#PlayerDeathEvent", false);
    expected.put("art.arcane.adapt.content.adaptation.taming.TameableOwnershipIndex#EntityDeathEvent", false);
    expected.put("art.arcane.adapt.api.recipe.RecipeCraftCounter#InventoryClickEvent", false);
    expected.put("art.arcane.adapt.api.recipe.RecipeCraftCounter#InventoryDragEvent", false);
    expected.put("art.arcane.adapt.api.recipe.RecipeCraftCounter#CraftItemEvent", true);
    for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
      String[] parts = entry.getKey().split("#");
      HandlerMethod found = null;
      for (HandlerMethod method : AnnotatedHandlers.of(Class.forName(parts[0]))) {
        if (method.eventType().getSimpleName().equals(parts[1])) {
          found = method;
        }
      }
      assertTrue(found != null, entry.getKey());
      assertEquals(entry.getValue(), ComponentRegistrar.handlerIgnoresCancelled(found), entry.getKey());
    }
  }

  @Test
  void chronosHandlersFilterCancelledEventsAsTheirBukkitHandlersDid() {
    for (HandlerMethod method : AnnotatedHandlers.of(SkillChronos.class)) {
      if (method.eventType() == ProjectileLaunchEvent.class || method.eventType() == PlayerTeleportEvent.class) {
        assertTrue(ComponentRegistrar.ignoresCancelled(method), method.describe());
      }
    }
    for (HandlerMethod method : AnnotatedHandlers.of(ChronosInstantRecall.class)) {
      if (method.eventType() == PlayerMoveEvent.class) {
        assertTrue(ComponentRegistrar.ignoresCancelled(method), method.describe());
      }
      if (method.eventType() == PlayerInteractEvent.class) {
        assertTrue(ComponentRegistrar.enforcesInteractionValidity(method), method.describe());
      }
    }
  }

  private static EntityDeathEvent death() {
    return new EntityDeathEvent(mock(LivingRef.class), null, null, null, DamageCause.CUSTOM, List.of(), 0);
  }

  private static HandlerMethod method(String name) {
    for (HandlerMethod method : AnnotatedHandlers.of(Handlers.class)) {
      if (method.method().getName().equals(name)) {
        return method;
      }
    }
    throw new AssertionError(name);
  }

  private static PlayerInteractEvent air(UseResult useBlock, UseResult useItem) {
    return new PlayerInteractEvent(mock(PlayerRef.class), InteractAction.RIGHT_CLICK_AIR, null, null, null, null, null,
        useBlock, useItem);
  }

  static final class Ping extends CancellableEvent {
  }

  static final class ServiceHandlers {
    private final List<String> seen = new ArrayList<>();

    @Handle(priority = EventPriority.LOW)
    public void skipping(EntityDeathEvent event) {
      seen.add("death-skipping");
    }

    @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void receiving(EntityDeathEvent event) {
      seen.add("death-receiving");
    }

    @ReceiveCancelledEvents
    @Handle(priority = EventPriority.HIGH)
    public void marked(EntityDeathEvent event) {
      seen.add("death-marked");
    }

    @Handle(priority = EventPriority.LOW)
    public void skipping(Ping event) {
      seen.add("ping-skipping");
    }

    @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void receiving(Ping event) {
      seen.add("ping-receiving");
    }

    @ReceiveCancelledEvents
    @Handle(priority = EventPriority.HIGH)
    public void marked(Ping event) {
      seen.add("ping-marked");
    }
  }

  static final class Handlers {
    @Handle
    public void plain(PlayerInteractEvent event) {
    }

    @ReceiveCancelledEvents
    @Handle
    public void receiving(PlayerInteractEvent event) {
    }

    @Handle
    public void death(EntityDeathEvent event) {
    }

    @ReceiveCancelledEvents
    @Handle
    public void receivingDeath(EntityDeathEvent event) {
    }
  }
}
