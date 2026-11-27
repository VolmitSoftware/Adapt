package art.arcane.adapt.engine;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.damage.DamageEventDispatcher;
import art.arcane.adapt.engine.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.engine.telemetry.AdaptRuntimeTelemetry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.EventListener;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the event handlers of runtime components (skills, adaptations, ticked services and their companions)
 * and removes them again. Their {@code @Handle} methods go on the {@link AdaptEventBus}.
 *
 * <p>Skills and adaptations get the component gating: protection probes are skipped, cancelled events are skipped
 * unless the method carries
 * {@link ReceiveCancelledEvents} (the {@code @Handle} cancelled flag is not consulted for them, as the Bukkit
 * registrar never consulted {@code @EventHandler}'s), adaptation handlers count toward the ability telemetry and the
 * event-handler op count, move and jump handlers of an adaptation only run for online learners unless marked
 * {@link RunsWithoutLearnedAdaptation} (a mutation block's learners are the players holding a learned mutated
 * adaptation that uses it, see {@link AdaptServer#hasOnlineLearner(java.util.UUID, String)}), and {@link EventPriority#HIGHEST} damage handlers run inside the
 * {@link DamageEventDispatcher} so death saves keep their order. Interact handlers receive cancelled interactions
 * (Bukkit reports every air click cancelled) and, unless marked {@link ReceiveCancelledEvents}, skip the ones whose
 * relevant use was denied ({@code PlayerInteractEvent#isUseDenied}).
 * Every other object registers its {@code @Handle} methods with their declared cancelled filter, as Bukkit
 * {@code @EventHandler(ignoreCancelled)} did: {@link ReceiveCancelledEvents} also opts in, and a handler that ignores
 * cancelled events also skips deaths the platform cancelled.
 */
public class ComponentRegistrar {
  private static final Set<String> INTROSPECTION_WARNED = ConcurrentHashMap.newKeySet();

  private final AdaptEventBus bus;
  private final DamageEventDispatcher damageDispatcher;
  private final Set<Object> owners = ConcurrentHashMap.newKeySet();
  private boolean damageDispatcherRegistered;

  public ComponentRegistrar(AdaptEventBus bus) {
    this.bus = Objects.requireNonNull(bus, "bus");
    this.damageDispatcher = new DamageEventDispatcher();
  }

  public final AdaptEventBus bus() {
    return bus;
  }

  public final DamageEventDispatcher damageDispatcher() {
    return damageDispatcher;
  }

  /** True for skills and adaptations, which get the component gating. */
  public static boolean isComponent(Object owner) {
    return owner instanceof ContentComponent;
  }

  /**
   * Registers every handler of the object. Returns false when nothing was registered, including when its handler
   * methods cannot be read on this platform.
   */
  public boolean register(Object owner) {
    Objects.requireNonNull(owner, "owner");
    return registerHandles(owner);
  }

  public void unregister(Object owner) {
    if (owner == null) {
      return;
    }
    if (owners.remove(owner)) {
      bus.unregisterAll(owner);
    }
    damageDispatcher.unregister(owner);
  }

  public void unregisterAll() {
    for (Object owner : List.copyOf(owners)) {
      owners.remove(owner);
      bus.unregisterAll(owner);
    }
    synchronized (this) {
      damageDispatcher.clear();
      if (damageDispatcherRegistered) {
        bus.unregisterAll(damageDispatcher);
        damageDispatcherRegistered = false;
      }
    }
  }

  /** The decorator used for an owner's {@code @Handle} methods. Platforms may override to add their own gating. */
  protected AnnotatedHandlers.Decorator decorator(Object owner) {
    return isComponent(owner) ? new ComponentDecorator() : HandlerDecorator.INSTANCE;
  }

  private boolean registerHandles(Object owner) {
    Class<?> type = owner.getClass();
    boolean declares;
    try {
      declares = AnnotatedHandlers.declaresHandlers(type);
    } catch (LinkageError | TypeNotPresentException error) {
      warnIntrospection(type, error);
      return false;
    }
    if (!declares) {
      return false;
    }
    owners.add(owner);
    AnnotatedHandlers.register(bus, owner, decorator(owner));
    return true;
  }

  private synchronized void ensureDamageDispatcher() {
    if (damageDispatcherRegistered) {
      return;
    }
    bus.register(damageDispatcher, EntityDamageEvent.class, EventPriority.HIGHEST, false, damageDispatcher::onDamage);
    damageDispatcherRegistered = true;
  }

  /** Cancelled events reach a component handler only when it is marked {@link ReceiveCancelledEvents}. */
  public static boolean ignoresCancelled(HandlerMethod method) {
    if (!Cancellable.class.isAssignableFrom(method.eventType())) {
      return method.ignoreCancelled();
    }
    if (PlayerInteractEvent.class.isAssignableFrom(method.eventType())) {
      return false;
    }
    return !method.isAnnotationPresent(ReceiveCancelledEvents.class);
  }

  /**
   * Deaths a platform cancelled (Paper's cancellable death event) are still posted; component death handlers skip
   * them unless marked {@link ReceiveCancelledEvents}, as the Bukkit registrar did for that cancellable event.
   */
  public static boolean skipsPlatformCancelledDeaths(HandlerMethod method) {
    return EntityDeathEvent.class.isAssignableFrom(method.eventType())
        && !method.isAnnotationPresent(ReceiveCancelledEvents.class);
  }

  /** Cancelled-event filter of a handler on an object that is not a skill or adaptation. */
  public static boolean handlerIgnoresCancelled(HandlerMethod method) {
    return method.ignoreCancelled() && !method.isAnnotationPresent(ReceiveCancelledEvents.class);
  }

  /** Wraps a handler of an object that is not a skill or adaptation with its platform-cancelled death filter. */
  public static EventListener<AdaptEvent> handlerFilter(HandlerMethod method, EventListener<AdaptEvent> handler) {
    if (EntityDeathEvent.class.isAssignableFrom(method.eventType()) && handlerIgnoresCancelled(method)) {
      return platformCancelledDeathFilter(handler);
    }
    return handler;
  }

  /**
   * Interact handlers receive every interaction, as cancelled air clicks are normal on Bukkit; unless marked
   * {@link ReceiveCancelledEvents} they skip the ones whose relevant use was denied.
   */
  public static boolean enforcesInteractionValidity(HandlerMethod method) {
    return PlayerInteractEvent.class.isAssignableFrom(method.eventType())
        && !method.isAnnotationPresent(ReceiveCancelledEvents.class);
  }

  public static boolean gatesOnLearner(Object owner, HandlerMethod method) {
    return abilityOf(owner) != null
        && (method.eventType() == PlayerMoveEvent.class || method.eventType() == PlayerJumpEvent.class)
        && !method.isAnnotationPresent(RunsWithoutLearnedAdaptation.class);
  }

  public static boolean routesToDamageDispatcher(HandlerMethod method) {
    return method.priority() == EventPriority.HIGHEST && EntityDamageEvent.class.isAssignableFrom(method.eventType());
  }

  /** Wraps a component handler with the protection-probe skip, learner gating and ability telemetry. */
  public static EventListener<AdaptEvent> gate(Object owner, HandlerMethod method, EventListener<AdaptEvent> handler) {
    EventListener<AdaptEvent> call = cancellationFilter(method, handler);
    String ability = abilityOf(owner);
    if (ability == null) {
      return event -> {
        if (!event.protectionProbe()) {
          call.handle(event);
        }
      };
    }
    boolean gated = gatesOnLearner(owner, method);
    return event -> {
      if (event.protectionProbe() || gated && missingLearner(ability, event) && !raisedForNonLearner(owner, event)) {
        return;
      }
      AdaptRuntimeTelemetry.recordEventHandlerOp();
      long started = AbilityCheckTelemetry.beginExecution(ability);
      try {
        call.handle(event);
      } finally {
        AbilityCheckTelemetry.endExecution(ability, started);
      }
    };
  }

  private static String abilityOf(Object owner) {
    String ability = owner instanceof Adaptation<?> adaptation ? adaptation.getId().toString() : null;
    return ability == null || ability.isBlank() ? null : ability;
  }

  private static EventListener<AdaptEvent> cancellationFilter(HandlerMethod method, EventListener<AdaptEvent> handler) {
    if (enforcesInteractionValidity(method)) {
      return event -> {
        if (!((PlayerInteractEvent) event).isUseDenied()) {
          handler.handle(event);
        }
      };
    }
    if (skipsPlatformCancelledDeaths(method)) {
      return platformCancelledDeathFilter(handler);
    }
    return handler;
  }

  private static EventListener<AdaptEvent> platformCancelledDeathFilter(EventListener<AdaptEvent> handler) {
    return event -> {
      if (!((EntityDeathEvent) event).platformCancelled()) {
        handler.handle(event);
      }
    };
  }

  private static boolean missingLearner(String ability, AdaptEvent event) {
    if (!(event instanceof PlayerEvent playerEvent)) {
      return false;
    }
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server != null && !server.hasOnlineLearner(playerEvent.player().uuid(), ability);
  }

  /** Whether the adaptation's {@code effectiveLevel} lets a player who has not learned it act. */
  private static boolean raisedForNonLearner(Object owner, AdaptEvent event) {
    return event instanceof PlayerEvent playerEvent && owner instanceof Adaptation<?> adaptation
        && ComponentHost.find(adaptation) instanceof ComponentHost host
        && host.effectiveLevel(playerEvent.player(), 0) > 0;
  }

  private static void warnIntrospection(Class<?> type, Throwable error) {
    String message = error.getMessage();
    String key = type.getName() + ":" + error.getClass().getName() + ":" + (message == null ? "" : message);
    if (INTROSPECTION_WARNED.add(key)) {
      AdaptLog.warn("Skipping listener registration for " + type.getName()
          + " due to missing/incompatible event class: " + error.getClass().getSimpleName()
          + (message == null ? "" : " (" + message + ")"));
    }
  }

  private final class ComponentDecorator implements AnnotatedHandlers.Decorator {
    @Override
    public EventListener<AdaptEvent> decorate(Object owner, HandlerMethod method, EventListener<AdaptEvent> call) {
      EventListener<AdaptEvent> gated = gate(owner, method, call);
      if (!routesToDamageDispatcher(method)) {
        return gated;
      }
      ensureDamageDispatcher();
      damageDispatcher.register(owner, method.eventType().asSubclass(EntityDamageEvent.class), gated,
          ignoresCancelled(method));
      return null;
    }

    @Override
    public boolean ignoreCancelled(Object owner, HandlerMethod method) {
      return ignoresCancelled(method);
    }
  }

  private static final class HandlerDecorator implements AnnotatedHandlers.Decorator {
    private static final HandlerDecorator INSTANCE = new HandlerDecorator();

    @Override
    public EventListener<AdaptEvent> decorate(Object owner, HandlerMethod method, EventListener<AdaptEvent> call) {
      return handlerFilter(method, call);
    }

    @Override
    public boolean ignoreCancelled(Object owner, HandlerMethod method) {
      return handlerIgnoresCancelled(method);
    }
  }
}
