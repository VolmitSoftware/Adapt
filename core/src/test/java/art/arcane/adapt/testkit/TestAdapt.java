package art.arcane.adapt.testkit;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.mutation.signature.BuiltinPairs;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Runs a real {@link AdaptCore} runtime (ticker, player server, skills, attributes, advancements, bus listeners) on a
 * {@link TestPlatform}, skipping the services half (language download, HUD, SQL). Close it after the test.
 */
public final class TestAdapt implements AutoCloseable {
  private final TestPlatform platform;
  private final AdaptEventBus bus;
  private final AdaptCore core;
  private final List<Throwable> handlerFailures = new ArrayList<>();
  private final List<AddonHandle> addons;

  private TestAdapt(TestPlatform platform, List<AddonHandle> addons) {
    this.platform = platform;
    this.addons = addons;
    bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> handlerFailures.add(error));
    ComponentRegistrar registrar = new ComponentRegistrar(bus);
    core = new AdaptCore(platform.platform(), bus, registrar);
  }

  /**
   * Installs the environment for {@code platform} and starts the core runtime with {@code skillTypes}, installed as
   * one addon per namespace. A skill type is either built in or declares its id in a static {@code ID} field.
   */
  public static TestAdapt start(TestPlatform platform, List<Class<? extends SimpleSkill<?>>> skillTypes) {
    Map<String, List<BuiltinAddon.Entry>> byNamespace = new LinkedHashMap<>();
    for (Class<? extends SimpleSkill<?>> type : skillTypes) {
      BuiltinAddon.Entry entry = entry(type);
      byNamespace.computeIfAbsent(entry.id().namespace(), ignored -> new ArrayList<>()).add(entry);
    }
    List<AdaptAddon> addons = new ArrayList<>();
    for (Map.Entry<String, List<BuiltinAddon.Entry>> group : byNamespace.entrySet()) {
      addons.add(new SkillList(group.getKey(), List.copyOf(group.getValue())));
    }
    return start(platform, addons.toArray(new AdaptAddon[0]));
  }

  /**
   * Installs the environment for {@code platform}, installs {@code addons} (an {@code adapt} one as the built-in
   * addon) beside any already installed, and starts the core runtime. Closing uninstalls every addon.
   */
  public static TestAdapt start(TestPlatform platform, AdaptAddon... addons) {
    Path data = platform.host().dataDirectory();
    AdaptEnvironment.install(platform.log(), () -> data);
    List<AddonHandle> handles = new ArrayList<>();
    for (AdaptAddon addon : addons) {
      Engine.addons().handle(HostKeys.key(), addon.namespace()).ifPresent(AddonHandle::uninstall);
      handles.add(Id.ADAPT.equals(addon.namespace())
          ? Engine.addons().installBuiltin(HostKeys.key(), addon)
          : Engine.addons().install(addon));
    }
    TestAdapt adapt = new TestAdapt(platform, handles);
    try {
      adapt.core.startRuntime();
    } catch (RuntimeException | Error failure) {
      adapt.close();
      throw failure;
    }
    return adapt;
  }

  private static BuiltinAddon.Entry entry(Class<? extends SimpleSkill<?>> type) {
    for (BuiltinAddon.Entry builtin : BuiltinAddon.skills()) {
      if (builtin.type() == type) {
        return builtin;
      }
    }
    try {
      Field field = type.getField("ID");
      field.setAccessible(true);
      Id id = (Id) field.get(null);
      Constructor<? extends SimpleSkill<?>> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return new BuiltinAddon.Entry(id, type, () -> {
        try {
          return constructor.newInstance();
        } catch (ReflectiveOperationException failure) {
          throw new IllegalStateException("Cannot build " + type.getName(), failure);
        }
      });
    } catch (ReflectiveOperationException missing) {
      throw new IllegalArgumentException(type.getName() + " is not built in and declares no static ID field", missing);
    }
  }

  /** The handles of the addons this runtime was started with. */
  public List<AddonHandle> addons() {
    return addons;
  }

  public AdaptCore core() {
    return core;
  }

  public TestPlatform platform() {
    return platform;
  }

  public AdaptEventBus bus() {
    return bus;
  }

  /** Posts {@code event} through every priority, like a platform bridge. */
  public <E extends AdaptEvent> E post(E event) {
    return bus.post(event);
  }

  /**
   * Advances the scheduler one tick at a time until {@code condition} holds; work from other threads (player data
   * claims) gets real time to land. Throws when it does not hold within {@code timeoutMillis}.
   */
  public void awaitTicks(BooleanSupplier condition, long timeoutMillis) {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Condition not reached within " + timeoutMillis + " ms");
      }
      platform.scheduler().advance(1);
      try {
        Thread.sleep(2L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting", interrupted);
      }
    }
  }

  /** Posts the join for an online player and waits until its Adapt profile is loaded. */
  public AdaptPlayer join(PlayerRef player) {
    post(new PlayerJoinEvent(player));
    awaitTicks(() -> core.getAdaptServer().getPlayer(player) != null, 10_000L);
    return core.getAdaptServer().getPlayer(player);
  }

  /** Exceptions thrown by bus handlers so far. */
  public List<Throwable> handlerFailures() {
    return List.copyOf(handlerFailures);
  }

  @Override
  public void close() {
    try {
      core.stopRuntime();
    } finally {
      for (AddonHandle addon : Engine.addons().snapshot(HostKeys.key())) {
        addon.uninstall();
        if (!Id.ADAPT.equals(addon.namespace())) {
          AdaptLanguage.texts().uninstall(addon.namespace());
        }
      }
      platform.scheduler().cancelAll();
      AdaptPorts.clear();
    }
  }

  private record SkillList(String namespace, List<BuiltinAddon.Entry> skills) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
      for (BuiltinAddon.Entry skill : skills) {
        registrar.skill(skill.id(), skill.factory());
      }
      if (Id.ADAPT.equals(namespace)) {
        BuiltinAddon.messages().forEach(registrar::messages);
        BuiltinPairs.register(registrar);
      }
    }
  }
}
