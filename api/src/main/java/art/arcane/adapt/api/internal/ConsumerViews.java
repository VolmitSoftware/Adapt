package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.GameVersion;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.BossBarColor;
import art.arcane.adapt.platform.fx.BossBarRef;
import art.arcane.adapt.platform.fx.BossBarStyle;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.permission.PermissionRegistration;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeRegistration;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The forwarding views behind {@link HostPlatform#consumer()}. Each implements only the consumer interface, so a cast
 * never reaches the host object or its host-only operations.
 */
@ApiStatus.Internal
final class ConsumerViews {
  private ConsumerViews() {
  }

  static AdaptPlatform of(HostPlatform platform) {
    return new AdaptPlatform(new Host(platform.host()), new Scheduler(platform.scheduler()), new Fx(platform.fx()),
        new Viewers(platform.viewers()), new Menus(platform.menus()), new Permissions(platform.permissions()),
        new Protection(platform.protection()), new Recipes(platform.recipes()));
  }

  private static final class Host implements PlatformHost {
    private final PlatformHost host;

    private Host(PlatformHost host) {
      this.host = host;
    }

    @Override
    public PlatformFamily family() {
      return host.family();
    }

    @Override
    public String platformName() {
      return host.platformName();
    }

    @Override
    public String minecraftVersion() {
      return host.minecraftVersion();
    }

    @Override
    public GameVersion gameVersion() {
      return host.gameVersion();
    }

    @Override
    public boolean supports(EngineFeature feature) {
      return host.supports(feature);
    }

    @Override
    public String adaptVersion() {
      return host.adaptVersion();
    }

    @Override
    public boolean has(Capability capability) {
      return host.has(capability);
    }

    @Override
    public Path dataDirectory() {
      return host.dataDirectory();
    }

    @Override
    public PlatformLog log() {
      return host.log();
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
      return host.onlinePlayers();
    }

    @Override
    public PlayerRef player(UUID id) {
      return host.player(id);
    }

    @Override
    public PlayerRef player(String name) {
      return host.player(name);
    }

    @Override
    public UUID knownPlayerId(String name) {
      return host.knownPlayerId(name);
    }

    @Override
    public String knownPlayerName(UUID id) {
      return host.knownPlayerName(id);
    }

    @Override
    public EntityRef entity(UUID id) {
      return host.entity(id);
    }

    @Override
    public Collection<WorldRef> worlds() {
      return host.worlds();
    }

    @Override
    public WorldRef world(UUID id) {
      return host.world(id);
    }

    @Override
    public WorldRef world(String name) {
      return host.world(name);
    }

    @Override
    public WorldRef world(Id dimension) {
      return host.world(dimension);
    }

    @Override
    public boolean isPresent(String pluginOrModId) {
      return host.isPresent(pluginOrModId);
    }

    @Override
    public RegistryLookup registries() {
      return host.registries();
    }

    @Override
    public ItemOps items() {
      return host.items();
    }

    @Override
    public AnimalOps animals() {
      return host.animals();
    }

    @Override
    public long currentTick() {
      return host.currentTick();
    }

    @Override
    public int viewDistance() {
      return host.viewDistance();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
      host.dispatchConsoleCommand(command);
    }
  }

  private static final class Scheduler implements AdaptScheduler {
    private final AdaptScheduler scheduler;

    private Scheduler(AdaptScheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public boolean isRegionized() {
      return scheduler.isRegionized();
    }

    @Override
    public boolean isTickThread() {
      return scheduler.isTickThread();
    }

    @Override
    public boolean owns(EntityRef entity) {
      return scheduler.owns(entity);
    }

    @Override
    public boolean owns(Loc location) {
      return scheduler.owns(location);
    }

    @Override
    public boolean owns(WorldRef world, int chunkX, int chunkZ) {
      return scheduler.owns(world, chunkX, chunkZ);
    }

    @Override
    public boolean ownsArea(Loc center, double radiusX, double radiusZ) {
      return scheduler.ownsArea(center, radiusX, radiusZ);
    }

    @Override
    public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
      return scheduler.runEntity(entity, task, delayTicks, retired);
    }

    @Override
    public boolean runEntity(EntityRef entity, Runnable task) {
      return scheduler.runEntity(entity, task);
    }

    @Override
    public boolean runEntity(EntityRef entity, Runnable task, long delayTicks) {
      return scheduler.runEntity(entity, task, delayTicks);
    }

    @Override
    public boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
      return scheduler.runEntity(entity, task, retired);
    }

    @Override
    public boolean runAt(Loc location, Runnable task, long delayTicks) {
      return scheduler.runAt(location, task, delayTicks);
    }

    @Override
    public boolean runAt(Loc location, Runnable task) {
      return scheduler.runAt(location, task);
    }

    @Override
    public boolean runGlobal(Runnable task, long delayTicks) {
      return scheduler.runGlobal(task, delayTicks);
    }

    @Override
    public boolean runGlobal(Runnable task) {
      return scheduler.runGlobal(task);
    }

    @Override
    public TaskHandle runAsync(Runnable task, long delayTicks) {
      return scheduler.runAsync(task, delayTicks);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
      return scheduler.runAsync(task);
    }

    @Override
    public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks,
                                   Runnable retired) {
      return scheduler.repeatEntity(entity, task, delayTicks, periodTicks, retired);
    }

    @Override
    public TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks) {
      return scheduler.repeatAt(location, task, delayTicks, periodTicks);
    }

    @Override
    public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
      return scheduler.repeatGlobal(task, delayTicks, periodTicks);
    }

    @Override
    public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
      return scheduler.repeatAsync(task, delayTicks, periodTicks);
    }
  }

  private static final class Fx implements FxHost {
    private final FxHost fx;

    private Fx(FxHost fx) {
      this.fx = fx;
    }

    @Override
    public void particle(WorldRef world, ParticleSpec particle, double x, double y, double z, int count,
                         double spreadX, double spreadY, double spreadZ, double speed) {
      fx.particle(world, particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
    }

    @Override
    public void particle(PlayerRef viewer, ParticleSpec particle, double x, double y, double z, int count,
                         double spreadX, double spreadY, double spreadZ, double speed, boolean force) {
      fx.particle(viewer, particle, x, y, z, count, spreadX, spreadY, spreadZ, speed, force);
    }

    @Override
    public void sound(WorldRef world, double x, double y, double z, Id sound, SoundCategory category, float volume,
                      float pitch) {
      fx.sound(world, x, y, z, sound, category, volume, pitch);
    }

    @Override
    public BossBarRef createBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
      return fx.createBossBar(titleMarkup, color, style);
    }

    @Override
    public void boneMealParticles(WorldRef world, BlockPos block, int count) {
      fx.boneMealParticles(world, block, count);
    }

    @Override
    public void blockBreakEffect(WorldRef world, BlockPos block, BlockStateRef state) {
      fx.blockBreakEffect(world, block, state);
    }
  }

  private static final class Viewers implements ViewerHost {
    private final ViewerHost viewers;

    private Viewers(ViewerHost viewers) {
      this.viewers = viewers;
    }

    @Override
    public void showEntity(PlayerRef viewer, EntityRef entity) {
      viewers.showEntity(viewer, entity);
    }

    @Override
    public void hideEntity(PlayerRef viewer, EntityRef entity) {
      viewers.hideEntity(viewer, entity);
    }

    @Override
    public boolean canSee(PlayerRef viewer, EntityRef entity) {
      return viewers.canSee(viewer, entity);
    }

    @Override
    public void setGlow(PlayerRef viewer, EntityRef entity, GlowColor color) {
      viewers.setGlow(viewer, entity, color);
    }

    @Override
    public void clearGlow(PlayerRef viewer, int entityId) {
      viewers.clearGlow(viewer, entityId);
    }

    @Override
    public void sendEquipment(PlayerRef viewer, EntityRef entity, Map<EquipmentSlot, ItemRef> items) {
      viewers.sendEquipment(viewer, entity, items);
    }

    @Override
    public VirtualPlayer createVirtualPlayer(PlayerRef skinSource, Loc location, int skinLayerMask) {
      return viewers.createVirtualPlayer(skinSource, location, skinLayerMask);
    }

    @Override
    public void setVisibleByDefault(EntityRef entity, boolean visible) {
      viewers.setVisibleByDefault(entity, visible);
    }

    @Override
    public Collection<PlayerRef> trackingPlayers(EntityRef entity) {
      return viewers.trackingPlayers(entity);
    }

    @Override
    public Collection<PlayerRef> chunkViewers(WorldRef world, int chunkX, int chunkZ) {
      return viewers.chunkViewers(world, chunkX, chunkZ);
    }
  }

  private static final class Menus implements MenuHost {
    private final MenuHost menus;

    private Menus(MenuHost menus) {
      this.menus = menus;
    }

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu menu) {
      return menus.open(viewer, menu);
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return menus.current(viewer);
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return menus.openStorage(viewer, title, size, onClose);
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return menus.currentStorage(viewer);
    }
  }

  private static final class Permissions implements PermissionHost {
    private final PermissionHost permissions;

    private Permissions(PermissionHost permissions) {
      this.permissions = permissions;
    }

    @Override
    public boolean has(PlayerRef player, String node) {
      return permissions.has(player, node);
    }

    @Override
    public boolean isSet(PlayerRef player, String node) {
      return permissions.isSet(player, node);
    }

    @Override
    public PermissionRegistration register(String node, String description, PermissionDefault fallback) {
      return permissions.register(node, description, fallback);
    }
  }

  private static final class Protection implements ProtectionHost {
    private final ProtectionHost protection;

    private Protection(ProtectionHost protection) {
      this.protection = protection;
    }

    @Override
    public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, @Nullable Id adaptation) {
      return protection.allows(actor, check, at, adaptation);
    }

    @Override
    public boolean allowsDamage(PlayerRef actor, EntityRef target, @Nullable Id adaptation) {
      return protection.allowsDamage(actor, target, adaptation);
    }

    @Override
    public boolean attemptBreak(PlayerRef actor, BlockRef block) {
      return protection.attemptBreak(actor, block);
    }
  }

  private static final class Recipes implements RecipeHost {
    private final RecipeHost recipes;

    private Recipes(RecipeHost recipes) {
      this.recipes = recipes;
    }

    @Override
    public Optional<RecipeRegistration> register(RecipeSpec recipe) {
      return recipes.register(recipe);
    }

    @Override
    public Collection<Id> registered() {
      return recipes.registered();
    }

    @Override
    public void discover(PlayerRef player, Id key) {
      recipes.discover(player, key);
    }

    @Override
    public void undiscover(PlayerRef player, Id key) {
      recipes.undiscover(player, key);
    }

    @Override
    public Optional<RecipeView> recipe(Id key) {
      return recipes.recipe(key);
    }

    @Override
    public List<RecipeView> recipesFor(ItemRef stack) {
      return recipes.recipesFor(stack);
    }
  }
}
