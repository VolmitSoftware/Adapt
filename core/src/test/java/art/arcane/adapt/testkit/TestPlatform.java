package art.arcane.adapt.testkit;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A complete {@link HostPlatform} for unit tests: fake host and worlds, a manual scheduler, recording FX and log, an
 * allow-all protection host that breaks blocks naturally, a permission host that asks the player, a recording recipe
 * host, and no-op viewer, menu and advancement hosts (the menu host can be replaced). Build one with {@link #builder(Path)}.
 */
public final class TestPlatform {
  private final ManualScheduler scheduler;
  private final RecordingFx fx;
  private final FakeItems items;
  private final FakeHost host;
  private final Protection protection;
  private final RecordingLog log;
  private final FakeRecipes recipes;
  private final HostPlatform platform;

  private TestPlatform(Builder builder) {
    scheduler = builder.scheduler;
    fx = builder.fx;
    items = builder.items;
    log = new RecordingLog();
    protection = new Protection();
    recipes = new FakeRecipes();
    host = new FakeHost(items, builder.dataDirectory, log, scheduler);
    platform = new HostPlatform(host, scheduler, fx, builder.viewers, builder.menus, builder.permissions,
        protection, recipes, noop(AdvancementHost.class));
  }

  public static Builder builder(Path dataDirectory) {
    return new Builder(dataDirectory);
  }

  public HostPlatform platform() {
    return platform;
  }

  public ManualScheduler scheduler() {
    return scheduler;
  }

  public RecordingFx fx() {
    return fx;
  }

  public FakeItems items() {
    return items;
  }

  public FakeHost host() {
    return host;
  }

  public Protection protection() {
    return protection;
  }

  public FakeRecipes recipes() {
    return recipes;
  }

  public RecordingLog log() {
    return log;
  }

  /** Overworld of this platform, created on first use. */
  public FakeWorld world() {
    return host.world("world", true);
  }

  /** Joins a survival player holding nothing at the given block position of {@link #world()}. */
  public FakePlayer player(String name, double x, double y, double z) {
    return host.join(new FakePlayer(name, new Loc(world(), x, y, z)));
  }

  /**
   * Interface instance whose methods do nothing and return false, zero, null or an empty collection. Default methods
   * keep their own behaviour.
   */
  public static <T> T noop(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
        (Object proxy, Method method, Object[] args) -> {
          if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
          }
          return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "noop " + type.getSimpleName();
            default -> neutral(method.getReturnType());
          };
        }));
  }

  private static Object neutral(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0D;
    }
    if (type == float.class) {
      return 0F;
    }
    if (type == List.class || type == Collection.class) {
      return List.of();
    }
    if (type == Set.class) {
      return Set.of();
    }
    if (type == Map.class) {
      return Map.of();
    }
    return null;
  }

  public static final class Builder {
    private final Path dataDirectory;
    private ManualScheduler scheduler = new ManualScheduler();
    private RecordingFx fx = new RecordingFx();
    private FakeItems items = new FakeItems();
    private ViewerHost viewers = noop(ViewerHost.class);
    private HostPermissions permissions = new Permissions();
    private HostMenus menus = noop(HostMenus.class);

    private Builder(Path dataDirectory) {
      this.dataDirectory = dataDirectory;
    }

    public Builder scheduler(ManualScheduler value) {
      scheduler = value;
      return this;
    }

    public Builder fx(RecordingFx value) {
      fx = value;
      return this;
    }

    public Builder items(FakeItems value) {
      items = value;
      return this;
    }

    public Builder viewers(ViewerHost value) {
      viewers = value;
      return this;
    }

    public Builder permissions(HostPermissions value) {
      permissions = value;
      return this;
    }

    public Builder menus(HostMenus value) {
      menus = value;
      return this;
    }

    public TestPlatform build() {
      return new TestPlatform(this);
    }
  }

  /**
   * Allows everything unless denied. Breaks post a {@link BlockBreakEvent} on the running core's bus first, like a
   * real player break, then break the block with the actor's main hand, record it and drop what the
   * {@link BlockDropItemEvent} handlers left, nothing when they cancelled it.
   */
  public static final class Protection implements ProtectionHost {
    private final List<BlockRef> broken = new ArrayList<>();
    private boolean deny;

    public void deny(boolean value) {
      deny = value;
    }

    public List<BlockRef> broken() {
      return List.copyOf(broken);
    }

    @Override
    public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, Id adaptation) {
      return !deny;
    }

    @Override
    public boolean allowsDamage(PlayerRef actor, EntityRef target, Id adaptation) {
      return !deny;
    }

    @Override
    public boolean attemptBreak(PlayerRef actor, BlockRef block) {
      if (deny || block.isAir()) {
        return false;
      }
      AdaptCore core = AdaptCore.current();
      if (core != null && core.getBus().post(new BlockBreakEvent(actor, block, true, 0)).isCancelled()) {
        return false;
      }
      broken.add(block);
      BlockStateRef state = block.state();
      List<ItemEntityRef> items = new ArrayList<>();
      for (ItemRef drop : block.drops(actor.inventory().mainHand(), actor)) {
        items.add(block.world().createItemEntity(block.center().position(), drop));
      }
      block.setType(Id.minecraft("air"), false);
      BlockDropItemEvent drops = new BlockDropItemEvent(actor, block, state, items);
      if (core != null && !items.isEmpty()) {
        core.getBus().post(drops);
      }
      if (drops.isCancelled()) {
        return true;
      }
      for (ItemEntityRef item : drops.items()) {
        if (item.isValid() && !item.item().isEmpty()) {
          block.world().dropItemNaturally(block.center().position(), item.item());
        }
      }
      return true;
    }
  }

  /** Answers from {@link PlayerRef#hasPermission}, so a fake player's op flag and grants decide. */
  public static final class Permissions implements HostPermissions {
    @Override
    public boolean has(PlayerRef player, String node) {
      return player.hasPermission(node);
    }

    @Override
    public boolean isSet(PlayerRef player, String node) {
      return player instanceof FakePlayer fake && fake.isPermissionSet(node);
    }

    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    @Override
    public boolean add(String node, String description, PermissionDefault fallback) {
      return registered.add(node);
    }

    @Override
    public void unregister(String node) {
      registered.remove(node);
    }

    /** Every node registered and not removed since the platform was built. */
    public Set<String> registered() {
      return Set.copyOf(registered);
    }
  }

  /** Platform log that keeps warnings, errors and verbose lines for assertions. */
  public static final class RecordingLog implements PlatformLog {
    private final List<String> warnings = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private final List<String> verbose = new ArrayList<>();
    private final List<String> infos = new ArrayList<>();

    public synchronized List<String> infos() {
      return List.copyOf(infos);
    }

    public synchronized List<String> warnings() {
      return List.copyOf(warnings);
    }

    public synchronized List<Throwable> errors() {
      return List.copyOf(errors);
    }

    public synchronized List<String> verbose() {
      return List.copyOf(verbose);
    }

    @Override
    public synchronized void info(String message) {
      infos.add(message);
    }

    @Override
    public synchronized void warn(String message) {
      warnings.add(message);
    }

    @Override
    public synchronized void error(String message, Throwable error) {
      warnings.add(message);
      if (error != null) {
        errors.add(error);
      }
    }

    @Override
    public synchronized void verbose(String message) {
      verbose.add(message);
    }
  }
}
