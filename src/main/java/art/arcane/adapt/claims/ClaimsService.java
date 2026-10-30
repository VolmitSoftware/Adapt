package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.AdaptPermissionRegistrar;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimRepository;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimsService implements AutoCloseable {
  public static final String BYPASS_PERMISSION = "adapt.claims.bypass";

  private final Adapt plugin;
  private final ClaimManager manager;
  private final ClaimInteractionListener interactions;
  private final ClaimProtectionListener protection;
  private final ClaimMovementListener movement;
  private final ClaimTravelService travel;
  private final BrackenTravelBridge brackenTravel;
  private final ClaimsAnchorReconciler anchors;
  private volatile boolean closed;

  public ClaimsService(Adapt plugin) throws IOException {
    this.plugin = Objects.requireNonNull(plugin);
    manager = new ClaimManager(config(), new ClaimRepository(plugin.getDataFolder().toPath().resolve("claims.json")));
    refreshRankLimits();
    interactions = new ClaimInteractionListener(this);
    protection = new ClaimProtectionListener(this);
    travel = new ClaimTravelService(this, new ClaimSafeLocation(this));
    brackenTravel = new BrackenTravelBridge(plugin, travel);
    movement = new ClaimMovementListener(this);
    anchors = new ClaimsAnchorReconciler(this);
  }

  public static ClaimBlock block(Block block) {
    return block(block.getLocation());
  }

  public static ClaimBlock block(Location location) {
    return new ClaimBlock(Objects.requireNonNull(location.getWorld()).getUID(),
        location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static ClaimChunk chunk(Location location) {
    return new ClaimChunk(Objects.requireNonNull(location.getWorld()).getUID(),
        location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  public static ClaimWorldAccess world(World world) {
    return new ClaimWorldAccess(world.getName(), world.getEnvironment() == World.Environment.NORMAL);
  }

  public static boolean permitsEdit(Player player, Location location) {
    ClaimsService service = current();
    return service == null || service.canEdit(player, location);
  }

  public static boolean permitsTeleport(Player player, Location location) {
    ClaimsService service = current();
    return service == null || service.canTeleport(player, location);
  }

  public static boolean permitsEdit(UUID owner, Location location) {
    ClaimsService service = current();
    return service == null || !service.enabled() || service.canEdit(owner, location);
  }

  public static ClaimsService current() {
    return Adapt.instance == null ? null : Adapt.instance.getClaimsService();
  }

  public static boolean isClaimed(Location location) {
    ClaimsService service = current();
    return service != null && service.enabled() && location != null && location.getWorld() != null
        && service.manager.snapshot().cityAt(chunk(location)).isPresent();
  }

  public void start() {
    plugin.registerListener(interactions);
    plugin.registerListener(protection);
    plugin.registerListener(movement);
    plugin.registerListener(brackenTravel);
    plugin.registerListener(anchors);
    for (World world : Bukkit.getWorlds()) {
      anchors.validateWorld(world);
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      manager.setOnline(player.getUniqueId(), true, System.currentTimeMillis());
      J.runEntity(player, () -> {
        refreshRanks(player);
        movement.validatePlayer(player);
      });
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    brackenTravel.close();
    travel.close();
    anchors.close();
    HandlerList.unregisterAll(interactions);
    HandlerList.unregisterAll(protection);
    HandlerList.unregisterAll(movement);
    HandlerList.unregisterAll(brackenTravel);
    HandlerList.unregisterAll(anchors);
    interactions.close();
    manager.close();
  }

  public ClaimManager manager() {
    return manager;
  }

  public ClaimsConfig config() {
    SkillClaims skill = skill();
    return skill == null ? new ClaimsConfig() : skill.claimsConfig();
  }

  public ClaimInteractionListener interactions() {
    return interactions;
  }

  public ClaimMovementListener movement() {
    return movement;
  }

  public boolean relocating(Player player) {
    return movement.relocating(player);
  }

  public ClaimTravelService travel() {
    return travel;
  }

  public BrackenTravelBridge brackenTravel() {
    return brackenTravel;
  }

  public boolean enabled() {
    SkillClaims skill = skill();
    return !closed && skill != null && skill.isEnabled();
  }

  public boolean bypass(Player player) {
    return player != null && player.hasPermission(BYPASS_PERMISSION);
  }

  public boolean canEdit(Player player, Location location) {
    if (!enabled() || bypass(player)) {
      return true;
    }
    if (player == null || location == null || location.getWorld() == null || relocating(player)) {
      return false;
    }
    return canEdit(player.getUniqueId(), location);
  }

  public boolean canPrepareArrival(Player player, Location location) {
    if (!enabled() || bypass(player)) {
      return true;
    }
    return player != null && canEdit(player.getUniqueId(), location);
  }

  boolean canPrepareArrival(Player player, World world, BoundingBox volume) {
    if (!enabled() || bypass(player)) {
      return true;
    }
    if (player == null || world == null || volume == null || volume.getVolume() <= 0) {
      return false;
    }
    UUID owner = player.getUniqueId();
    UUID worldId = world.getUID();
    ClaimSnapshot snapshot = manager.snapshot();
    int maxX = Location.locToBlock(Math.nextDown(volume.getMaxX())) >> 4;
    int maxZ = Location.locToBlock(Math.nextDown(volume.getMaxZ())) >> 4;
    for (int x = Location.locToBlock(volume.getMinX()) >> 4; x <= maxX; x++) {
      for (int z = Location.locToBlock(volume.getMinZ()) >> 4; z <= maxZ; z++) {
        ClaimChunk chunk = new ClaimChunk(worldId, x, z);
        ClaimCity city = snapshot.cityAt(chunk).orElse(null);
        if (city == null) {
          continue;
        }
        ClaimPlot plot = snapshot.plotAt(chunk).orElse(null);
        if (!city.includes(owner) || contains(volume, city.anchor())
            || plot != null && (!plot.owner().equals(owner) || contains(volume, plot.anchor()))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean canEdit(UUID owner, Location location) {
    if (owner == null || location == null || location.getWorld() == null) {
      return false;
    }
    ClaimBlock position = block(location);
    ClaimSnapshot snapshot = manager.snapshot();
    ClaimCity city = snapshot.cityAt(position.chunk()).orElse(null);
    if (city == null) {
      return true;
    }
    ClaimPlot plot = snapshot.plotAt(position.chunk()).orElse(null);
    return !city.anchor().equals(position) && (plot == null || !plot.anchor().equals(position))
        && manager.canEdit(owner, position);
  }

  public boolean canReach(Player player, Location location) {
    if (!enabled() || bypass(player)) {
      return true;
    }
    if (player == null || location == null || location.getWorld() == null || relocating(player)) {
      return false;
    }
    if (manager.snapshot().cityAt(chunk(location)).isEmpty()) {
      return true;
    }
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
      return false;
    }
    Location eye = player.getEyeLocation();
    World world = location.getWorld();
    if (eye.getWorld() != world) {
      return false;
    }
    Location center = new Location(world, location.getBlockX() + 0.5, location.getBlockY() + 0.5,
        location.getBlockZ() + 0.5);
    Vector direction = center.toVector().subtract(eye.toVector());
    double distance = direction.length();
    if (distance > 6) {
      return false;
    }
    Location middle = eye.clone().add(direction.clone().multiply(0.5));
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(middle,
        Math.abs(direction.getX()) * 0.5 + 1, Math.abs(direction.getZ()) * 0.5 + 1)) {
      return false;
    }
    if (distance < 0.001) {
      return true;
    }
    RayTraceResult trace = world.rayTraceBlocks(eye, direction.multiply(1 / distance), distance,
        FluidCollisionMode.NEVER, true);
    Block hit = trace == null ? null : trace.getHitBlock();
    Block target = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    return hit == null || hit.equals(target) || sameChest(target, hit);
  }

  public boolean canTeleport(Player player, Location location) {
    if (!enabled() || bypass(player)) {
      return true;
    }
    if (player == null || location == null || location.getWorld() == null) {
      return false;
    }
    ClaimCity city = manager.snapshot().cityAt(chunk(location)).orElse(null);
    return city == null || city.includes(player.getUniqueId());
  }

  public ClaimRanks ranks(Player player) {
    AdaptPlayer profile = plugin.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
    if (profile == null || !profile.isRuntimeReady() || profile.getPlayer() != player) {
      throw new IllegalStateException("Claims progression is unavailable while the player profile loads");
    }
    PlayerSkillLine line = profile.getData().getSkillLines().get("claims");
    return line == null ? ClaimRanks.none() : new ClaimRanks(
        purchasedLevel(line, "claims-founding") > 0,
        purchasedLevel(line, "claims-fortification"),
        purchasedLevel(line, "claims-citizenship"),
        purchasedLevel(line, "claims-expansion"),
        purchasedLevel(line, "claims-homestead") > 0);
  }

  public Optional<String> deniedUsePermission(Player player, String adaptationId) {
    SkillClaims skill = skill();
    if (skill == null || !skill.hasUsePermission(player, skill)) {
      return Optional.of(AdaptPermissionRegistrar.useNode("claims"));
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptationId.equals(adaptation.getName()) && adaptation.hasUsePermission(player, adaptation)) {
        return Optional.empty();
      }
    }
    return Optional.of(AdaptPermissionRegistrar.useNode(adaptationId));
  }

  public ClaimRanks refreshRanks(Player player) {
    AdaptPlayer profile = plugin.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
    if (closed || profile == null || !profile.isRuntimeReady() || profile.getPlayer() != player) {
      return ClaimRanks.none();
    }
    ClaimRanks ranks = ranks(player);
    manager.updateRanks(player.getUniqueId(), ranks);
    return ranks;
  }

  public void resetRanks(UUID player) {
    if (!closed) {
      manager.updateRanks(player, ClaimRanks.none());
    }
  }

  public void reconfigure() {
    if (!closed) {
      resetTravel();
      manager.reconfigure(config());
      for (World world : Bukkit.getWorlds()) {
        anchors.validateWorld(world);
      }
      refreshRankLimits();
      for (Player player : Bukkit.getOnlinePlayers()) {
        J.runEntity(player, () -> {
          refreshRanks(player);
          movement.validatePlayer(player);
        });
      }
    }
  }

  public void resetTravel() {
    brackenTravel.reset();
    travel.reset();
  }

  public void refreshRankLimits() {
    if (closed) {
      return;
    }
    SkillClaims skill = skill();
    boolean founding = false;
    int fortification = 0;
    int citizenship = 0;
    int expansion = 0;
    boolean homestead = false;
    if (skill != null) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        int limit = adaptation.isEnabled() ? Math.clamp(adaptation.getMaxLevel(), 0, 4) : 0;
        switch (adaptation.getName()) {
          case "claims-founding" -> founding = limit > 0;
          case "claims-fortification" -> fortification = limit;
          case "claims-citizenship" -> citizenship = limit;
          case "claims-expansion" -> expansion = limit;
          case "claims-homestead" -> homestead = limit > 0;
          default -> { }
        }
      }
    }
    manager.setRankLimits(new ClaimRanks(founding, fortification, citizenship, expansion, homestead));
  }

  private static int purchasedLevel(PlayerSkillLine line, String id) {
    PlayerAdaptation adaptation = line.getAdaptation(id);
    return adaptation == null || adaptation.isRegionGranted() ? 0 : Math.clamp(adaptation.getLevel(), 0, 4);
  }

  private static boolean contains(BoundingBox volume, ClaimBlock block) {
    return block.x() < volume.getMaxX() && block.x() + 1.0 > volume.getMinX()
        && block.y() < volume.getMaxY() && block.y() + 1.0 > volume.getMinY()
        && block.z() < volume.getMaxZ() && block.z() + 1.0 > volume.getMinZ();
  }

  private boolean sameChest(Block target, Block hit) {
    if (target.getType() != hit.getType() || target.getY() != hit.getY()
        || !(target.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE
        || !(hit.getBlockData() instanceof Chest other) || other.getType() == Chest.Type.SINGLE
        || chest.getType() == other.getType() || chest.getFacing() != other.getFacing()) {
      return false;
    }
    BlockFace facing = chest.getFacing();
    int side = chest.getType() == Chest.Type.LEFT ? 1 : -1;
    return hit.getX() == target.getX() - side * facing.getModZ()
        && hit.getZ() == target.getZ() + side * facing.getModX();
  }

  private SkillClaims skill() {
    if (plugin.getAdaptServer() == null) {
      return null;
    }
    Skill<?> skill = plugin.getAdaptServer().getSkillRegistry().getAnySkill("claims");
    return skill instanceof SkillClaims claims ? claims : null;
  }
}
