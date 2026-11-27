package art.arcane.adapt.engine.component;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.content.SimpleComponent;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.ComponentRuntime;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.engine.fx.Fx;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.tick.TickedObject;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigFileSupport;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The engine side of one skill or adaptation: its config file, its tick and listener registration and the runtime its
 * public base class calls. One host per component instance, made when the component attaches from its constructor.
 */
public abstract class ComponentHost extends TickedObject implements ComponentRuntime {
  private static final Map<ContentComponent, WeakReference<ComponentHost>> HOSTS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private final SimpleComponent<?> component;
  private final ComponentHooks hooks;
  private final String owner;
  private final AdaptCore core;
  private final String configFolder;
  private volatile Object config;
  private volatile boolean fixedConfig;

  protected ComponentHost(SimpleComponent<?> component, ComponentHooks hooks, String owner, AdaptCore core,
                          String group, String configFolder, long interval) {
    super(group, UUID.randomUUID() + "-" + component.getId(), interval, false, overridesTick(component.getClass()));
    this.component = component;
    this.hooks = hooks;
    this.owner = owner;
    this.core = core;
    this.configFolder = configFolder;
    HOSTS.put(component, new WeakReference<>(this));
  }

  /** The host of {@code component}, or null for one no running engine attached. */
  public static @Nullable ComponentHost find(@Nullable ContentComponent component) {
    WeakReference<ComponentHost> host = component == null ? null : HOSTS.get(component);
    return host == null ? null : host.get();
  }

  /** The host of {@code component}; throws for one no running engine attached. */
  public static ComponentHost of(ContentComponent component) {
    ComponentHost host = find(component);
    if (host == null) {
      throw new IllegalStateException(component + " is not attached to a running Adapt engine");
    }
    return host;
  }

  /** Retires the component the way the engine does: its own cleanup first, then this runtime. */
  public final void retireComponent() {
    component.unregister();
  }

  public abstract boolean openGui(PlayerRef player, boolean checkPermissions);

  public abstract void openGui(PlayerRef player);

  public abstract void openGui(PlayerRef player, int page);

  public abstract art.arcane.adapt.util.common.misc.CustomModel getModel();

  public abstract java.util.List<art.arcane.adapt.engine.world.AdaptStatTracker> getStatTrackers();

  public abstract art.arcane.adapt.engine.advancement.AdaptAdvancement buildAdvancements();

  public SimpleComponent<?> component() {
    return component;
  }

  public Id getId() {
    return component.getId();
  }

  public AdaptCore core() {
    return core;
  }

  protected ComponentHooks hooks() {
    return hooks;
  }

  /** The level the component acts at for {@code player} over the {@code activeLevel} the engine resolved. */
  public int effectiveLevel(PlayerRef player, int activeLevel) {
    return hooks.effectiveLevel(player, activeLevel);
  }

  @Override
  public String owner() {
    return owner;
  }

  /** Hands the public instance to the registrar as the listener of its {@code @Handle} methods. */
  @Override
  protected List<?> createCompanionListeners() {
    List<Object> listeners = new ArrayList<>();
    listeners.add(component);
    listeners.addAll(hooks.companionListeners());
    return listeners;
  }

  @Override
  protected void onRuntimeActivated() {
    hooks.activated();
  }

  @Override
  public void onTick() {
    component.onTick();
  }

  @Override
  public boolean hasTickDemand() {
    return component.hasTickDemand();
  }

  /**
   * Tears the runtime down: ticks and listeners. The public instance calls this from {@link SimpleComponent#unregister()}
   * after its own cleanup, so the engine retires a component by calling the public method, never this one directly.
   */
  @Override
  public void unregister() {
    super.unregister();
    synchronized (HOSTS) {
      WeakReference<ComponentHost> host = HOSTS.get(component);
      if (host != null && host.get() == this) {
        HOSTS.remove(component);
      }
    }
  }

  public @Nullable Class<?> getConfigurationClass() {
    return component.getConfigurationClass();
  }

  @Override
  public @Nullable Object config(@Nullable Class<?> type) {
    return type == null ? null : loadedConfig();
  }

  /** The loaded config, read from disk on first use. */
  public Object loadedConfig() {
    Object local = config;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      local = config;
      if (local != null) {
        return local;
      }
      boolean loaded = reloadConfigFromDisk(false, true);
      local = config;
      if (!loaded || local == null) {
        local = createDefaultConfig();
        normalizeConfiguration(local);
        applyConfig(null, local);
        config = local;
        AdaptLog.warn("Falling back to in-memory defaults for " + kind() + " config " + getId() + ".");
      }
    }
    return local;
  }

  /** Uses {@code fixed} as the config for good: nothing is read from or written to disk for this component. */
  public final void useFixedConfig(Object fixed) {
    config = fixed;
    fixedConfig = true;
  }

  /** {@code skill} or {@code adaptation}, used in config section names and log lines. */
  protected abstract String kind();

  /** Applies the shared config fields after a load; called with the previous config, null on the first load. */
  protected void configApplied(@Nullable Object previous, Object current) {
  }

  /** Validates engine-owned parts of a freshly read config before it is accepted. */
  protected void validateConfig(Object loaded) {
  }

  public File getConfigFile() {
    return AdaptEnvironment.contentConfigFile(configFolder, getId());
  }

  protected Object createDefaultConfig() {
    Class<?> type = getConfigurationClass();
    if (type == null) {
      throw new IllegalStateException(getId() + " registered no configuration class");
    }
    try {
      return type.getConstructor().newInstance();
    } catch (Throwable error) {
      throw new IllegalStateException("Failed to create default config for " + kind() + " " + getId(), error);
    }
  }

  public synchronized boolean reloadConfigFromDisk(boolean announce) {
    return reloadConfigFromDisk(announce, config == null);
  }

  public synchronized boolean reloadConfigFromDiskPassive(boolean announce) {
    return reloadConfigFromDisk(announce, false);
  }

  private boolean reloadConfigFromDisk(boolean announce, boolean overwriteOnReadFailure) {
    if (fixedConfig || getConfigurationClass() == null) {
      return false;
    }
    Object previous = config;
    File file = getConfigFile();
    try {
      Object loaded = loadConfig(file, previous == null ? createDefaultConfig() : previous, overwriteOnReadFailure);
      config = loaded;
      applyConfig(previous, loaded);
      if (announce) {
        AdaptLog.info("Hotloaded " + file.getPath());
      }
      return true;
    } catch (Throwable error) {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + error.getMessage());
      AdaptLog.error(error);
      return false;
    }
  }

  public synchronized boolean reloadConfigSnapshot(String raw, File sourceFile, boolean announce) {
    Class<?> type = getConfigurationClass();
    if (fixedConfig || type == null) {
      return false;
    }
    Object previous = config;
    try {
      Object loaded = parseSnapshot(raw, sourceFile, type);
      config = loaded;
      applyConfig(previous, loaded);
      if (announce) {
        AdaptLog.info("Hotloaded " + sourceFile.getPath());
      }
      return true;
    } catch (Throwable error) {
      AdaptLog.warn("Skipped hotload for " + sourceFile.getPath() + " due to invalid config: " + error.getMessage());
      AdaptLog.error(error);
      return false;
    }
  }

  public synchronized boolean validateConfigFromDisk() {
    if (fixedConfig || getConfigurationClass() == null) {
      return false;
    }
    File file = getConfigFile();
    try {
      loadConfig(file, createDefaultConfig(), false);
      return true;
    } catch (Throwable error) {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + error.getMessage());
      return false;
    }
  }

  public synchronized boolean writeDefaultConfig() {
    if (fixedConfig || getConfigurationClass() == null) {
      return false;
    }
    File file = getConfigFile();
    try {
      ConfigFileSupport.writeDefaults(file, createDefaultConfig(), kind() + ":" + getId(), this::normalizeConfiguration);
      return true;
    } catch (Throwable error) {
      AdaptLog.warn("Failed to write default config " + file.getPath() + ": " + error.getMessage());
      return false;
    }
  }

  private void applyConfig(@Nullable Object previous, Object current) {
    configApplied(previous, current);
    hooks.configReloaded(previous, current);
    if (previous != null) {
      onConfigHotloaded();
    }
  }

  /** Runs after a hotload replaced an earlier config. */
  protected void onConfigHotloaded() {
  }

  @SuppressWarnings("unchecked")
  private <C> C parseSnapshot(String raw, File sourceFile, Class<C> type) throws IOException {
    return ConfigFileSupport.parseSnapshot(raw, sourceFile, type, kind() + ":" + getId(),
        loaded -> normalizeConfiguration(loaded));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object loadConfig(File file, Object fallback, boolean overwriteOnReadFailure) throws IOException {
    Class type = getConfigurationClass();
    return ConfigFileSupport.load(file, null, type, fallback, overwriteOnReadFailure, kind() + ":" + getId(),
        "Created missing " + kind() + " config [" + AdaptEnvironment.contentConfigPath(configFolder, getId())
            + "] from defaults.",
        loaded -> normalizeConfiguration(loaded), true);
  }

  private void normalizeConfiguration(Object loaded) {
    hooks.normalizeConfig(loaded);
    validateConfig(loaded);
  }

  @Override
  public long interval() {
    return getInterval();
  }

  @Override
  public boolean isRegistered() {
    return isRuntimeRegistered();
  }

  @Override
  public boolean particlesEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isParticlesEnabled();
  }

  @Override
  public boolean soundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }

  @Override
  public @Nullable PlayerProfile profile(@Nullable PlayerRef player) {
    return player == null ? null : PlayerApi.directory().find(player).orElse(null);
  }

  /** The engine player record of {@code player} once its data is loaded, or null. */
  public @Nullable AdaptPlayer readyPlayer(@Nullable PlayerRef player) {
    if (player == null) {
      return null;
    }
    AdaptServer server = core.getAdaptServer();
    AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(player.uuid());
    return adaptPlayer != null && adaptPlayer.isRuntimeReady() ? adaptPlayer : null;
  }

  @Override
  public void addStat(PlayerRef player, String stat, double amount) {
    AdaptPlayer adaptPlayer = readyPlayer(player);
    if (adaptPlayer != null) {
      adaptPlayer.getData().addStat(stat, amount);
    }
  }

  @Override
  public double stat(PlayerRef player, String stat) {
    AdaptPlayer adaptPlayer = readyPlayer(player);
    return adaptPlayer == null ? 0D : adaptPlayer.getData().getStat(stat);
  }

  @Override
  public boolean grantOnce(PlayerRef player, String advancementKey) {
    if (!AdaptConfig.get().isAdvancements()) {
      return false;
    }
    AdaptPlayer adaptPlayer = readyPlayer(player);
    String advancement = AdaptAdvancement.contentKey(getId(), advancementKey);
    if (adaptPlayer == null || adaptPlayer.getData().isGranted(advancement)) {
      return false;
    }
    return adaptPlayer.getAdvancementHandler().grant(advancement);
  }

  @Override
  public <V> Map<UUID, V> playerState() {
    return PlayerStateRegistry.newPlayerMap();
  }

  @Override
  public double materialValue(Id material) {
    return ComponentSupport.getValue(material);
  }

  @Override
  public double blockXpMultiplier(BlockRef block) {
    return ComponentSupport.getBlockMultiplier(block);
  }

  @Override
  public double armorValue(PlayerRef player) {
    return ComponentSupport.getArmorValue(player);
  }

  @Override
  public void damageItem(PlayerRef player, boolean offHand, int damage) {
    if (offHand) {
      ComponentSupport.damageOffHand(player, damage);
    } else {
      ComponentSupport.damageHand(player, damage);
    }
  }

  @Override
  public void addPotionStacks(PlayerRef player, Id effect, int amplifier, int durationTicks, boolean overlap) {
    ComponentSupport.addPotionStacks(player, effect, amplifier, durationTicks, overlap);
  }

  @Override
  public boolean addPotionStacksNow(PlayerRef player, Id effect, int amplifier, int durationTicks, boolean overlap) {
    return ComponentSupport.addPotionStacksNow(player, effect, amplifier, durationTicks, overlap);
  }

  @Override
  public boolean canSnatchItem(PlayerRef player, ItemEntityRef item) {
    return ComponentSupport.canSnatchItem(player, item);
  }

  @Override
  public boolean giveItem(PlayerRef player, EntityRef itemEntity, ItemRef item) {
    return ComponentSupport.safeGiveItem(player, itemEntity, item);
  }

  @Override
  public void giveItem(PlayerRef player, ItemRef item) {
    ComponentSupport.safeGiveItem(player, item);
  }

  @Override
  public void giveExp(PlayerRef player, int amount) {
    ComponentSupport.giveExp(player, amount);
  }

  @Override
  public @Nullable BlockFace blockFace(PlayerRef player, int maxRange) {
    return ComponentSupport.blockFace(player, maxRange);
  }

  @Override
  public FxEmitter fx(Loc location, FxPriority priority) {
    return Fx.now(component, location, priority);
  }

  @Override
  public FxEmitter fx(EntityRef entity, FxPriority priority) {
    return Fx.now(component, entity, priority);
  }

  @Override
  public FxTimeline timeline(Loc location) {
    return art.arcane.adapt.engine.fx.FxTimeline.at(component, location);
  }

  @Override
  public FxTimeline timeline(EntityRef entity) {
    return art.arcane.adapt.engine.fx.FxTimeline.follow(component, entity);
  }

  @Override
  public void sfx(Loc location, Id sound, float volume, float pitch) {
    fx(location, FxPriority.GAMEPLAY).sound(sound, volume, pitch);
  }

  static boolean overridesTick(Class<?> type) {
    try {
      return type.getMethod("onTick").getDeclaringClass() != SimpleComponent.class;
    } catch (ReflectiveOperationException | LinkageError | SecurityException error) {
      return true;
    }
  }
}
