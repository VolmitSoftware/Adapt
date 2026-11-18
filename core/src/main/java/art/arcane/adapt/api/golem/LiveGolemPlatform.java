package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.gui.GolemGuiItems;
import art.arcane.adapt.api.golem.item.OwnerBoundDrop;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class LiveGolemPlatform implements GolemPlatform {
  private static final double FULL_SIGNAL_LIFT = 0.4D;

  private final ContainerLids lids;

  LiveGolemPlatform(ContainerLids lids) {
    this.lids = Objects.requireNonNull(lids);
  }

  @Override
  public boolean isOnline(UUID owner) {
    return host().player(owner) != null;
  }

  @Override
  public Collection<UUID> onlineOwners() {
    Collection<PlayerRef> players = host().onlinePlayers();
    List<UUID> owners = new ArrayList<>(players.size());
    for (PlayerRef player : players) {
      owners.add(player.uuid());
    }
    return owners;
  }

  @Override
  public PlayerRef player(UUID owner) {
    return host().player(owner);
  }

  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
  }

  @Override
  public boolean ownerReady(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    return player != null && player.isRuntimeReady();
  }

  @Override
  public boolean keepsBoneGolems(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    PlayerSkillLine tragoul = player == null ? null : player.getData().getSkillLineNullable("tragoul");
    return tragoul != null && tragoul.getAdaptationLevel("tragoul-skeletal-servant") > 0;
  }

  @Override
  public void rosterChanged(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    if (player != null) {
      player.requestSave();
    }
  }

  @Override
  public CompletableFuture<Void> checkpoint(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    return player == null ? CompletableFuture.failedFuture(new IllegalStateException("Golem owner is unavailable: " + owner)) : player.checkpoint();
  }

  @Override
  public boolean owns(EntityRef entity) {
    return Tasks.isOwnedByCurrentRegion(entity);
  }

  @Override
  public boolean owns(Loc location) {
    return Tasks.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? Tasks.runEntity(entity, task) : Tasks.runEntity(entity, task, delayTicks);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
    return Tasks.runEntity(entity, task, retired);
  }

  @Override
  public boolean runAt(Loc location, Runnable task) {
    return Tasks.runAt(location, task);
  }

  @Override
  public boolean isChunkLoaded(UUID world, long chunkKey) {
    WorldRef loaded = host().world(world);
    return loaded != null && loaded.isChunkLoaded(GolemRoster.chunkX(chunkKey), GolemRoster.chunkZ(chunkKey));
  }

  @Override
  public void sweepChunk(UUID worldId, long chunkKey, Consumer<MobRef> boundGolem) {
    WorldRef world = host().world(worldId);
    int chunkX = GolemRoster.chunkX(chunkKey);
    int chunkZ = GolemRoster.chunkZ(chunkKey);
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    Loc center = new Loc(world, (chunkX << 4) + 8D, world.minHeight(), (chunkZ << 4) + 8D);
    Box chunk = new Box(chunkX << 4, world.minHeight(), chunkZ << 4, (chunkX << 4) + 16D, world.maxHeight(), (chunkZ << 4) + 16D);
    Tasks.runAt(center, () -> {
      if (!world.isChunkLoaded(chunkX, chunkZ)) {
        return;
      }
      for (EntityRef entity : world.entitiesIn(chunk, (EntityRef candidate) -> candidate instanceof MobRef)) {
        if (entity instanceof MobRef mob && GolemRegistry.adoptable(mob)) {
          boundGolem.accept(mob);
        }
      }
    });
  }

  @Override
  public GolemAttribute scaleOf(LivingRef entity) {
    return GolemScale.scaleOf(entity);
  }

  @Override
  public GolemAttribute attribute(LivingRef entity, GolemStat stat) {
    return GolemAttribute.of(entity, stat.attribute());
  }

  @Override
  public int patinaStage(MobRef golem) {
    return golem.weatheringStage();
  }

  @Override
  public boolean waxed(MobRef golem) {
    return golem.isWaxed();
  }

  @Override
  public double heal(LivingRef entity, double amount) {
    return GolemVitals.heal(entity, amount, EntityRegainHealthEvent.Reason.REGEN);
  }

  @Override
  public void dropOwnerBound(Loc at, ItemRef item, UUID owner) {
    OwnerBoundDrop.drop(at, item, owner);
  }

  @Override
  public void returnToOwner(UUID owner, Loc at, ItemRef item) {
    PlayerRef online = host().player(owner);
    if (online == null) {
      at.world().dropItemNaturally(at.position(), item);
      return;
    }
    GolemGuiItems.toPlayer(online, item, at);
  }

  @Override
  public void upkeep(UUID owner, Set<UUID> golems) {
    MinionBurden.get().hold(owner, golems);
  }

  @Override
  public void crumble(MobRef body) {
    ServantFrame.crumble(body);
  }

  @Override
  public MobRef spawn(Loc at, Id type, Consumer<MobRef> prepare) {
    EntityRef spawned = at.world().spawn(type, at, (EntityRef entity) -> {
      if (entity instanceof MobRef mob) {
        prepare.accept(mob);
      }
    });
    if (!(spawned instanceof MobRef mob)) {
      if (spawned != null) {
        spawned.remove();
      }
      throw new IllegalStateException("Spawning " + type + " at " + at + " did not produce a mob");
    }
    return mob;
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }

  @Override
  public List<NodeIndex.Entry> nodesNear(UUID world, double x, double y, double z, double range) {
    return GolemancyRuntime.nodes().map(service -> service.index().near(world, x, y, z, range)).orElse(List.of());
  }

  @Override
  public Loc targetLocation(GolemTarget target, WorldRef world) {
    return targetEntity(target, world).map(EntityRef::location).orElseGet(() -> target.location(world));
  }

  @Override
  public Optional<EntityRef> targetEntity(GolemTarget target, WorldRef world) {
    return target.kind() == GolemTarget.Kind.BLOCK ? Optional.empty() : ownedEntity(target.entity(), world);
  }

  @Override
  public Optional<EntityRef> ownedEntity(UUID id, WorldRef world) {
    EntityRef entity = host().entity(id);
    return entity != null && entity.isValid() && Tasks.isOwnedByCurrentRegion(entity) && world.uid().equals(entity.world().uid())
        ? Optional.of(entity)
        : Optional.empty();
  }

  @Override
  public void signalFull(MobRef golem) {
    Loc above = golem.location().add(0D, golem.height() + FULL_SIGNAL_LIFT, 0D);
    AdaptCore.platform().fx().particle(golem.world(), Particles.ANGRY_VILLAGER, above.x(), above.y(), above.z(), 1, 0D, 0D, 0D, 0D);
  }

  @Override
  public void visitLid(BlockRef container, int holdTicks) {
    lids.visit(container, holdTicks);
  }

  @Override
  public Adaptation<?> bindingPolicy() {
    return GolemancyRuntime.protectionPolicy().orElse(null);
  }

  @Override
  public String encodeItem(ItemRef item) {
    return GolemState.encodeItem(item);
  }

  @Override
  public ItemRef decodeItem(String encoded) {
    return GolemState.decodeItem(encoded);
  }

  @Override
  public GolemNameMode nameMode() {
    return GolemancyRuntime.names().map(GolemNames::mode).orElse(GolemNameMode.VANILLA);
  }

  private static PlatformHost host() {
    return AdaptCore.platform().host();
  }

  private static AdaptPlayer onlinePlayer(UUID owner) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(owner);
  }
}
