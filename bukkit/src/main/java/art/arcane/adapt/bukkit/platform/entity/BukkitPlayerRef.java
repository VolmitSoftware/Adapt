package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitNatives;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.scoreboard.Team;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Online player handle. One instance per UUID for the whole session; {@link #rebind} follows the
 * native player object if the server replaces it.
 */
public final class BukkitPlayerRef extends BukkitLivingRef implements PlayerRef {
  private static volatile Optional<PlayerClientAccess> clientAccess;
  private final String name;

  public BukkitPlayerRef(Player player) {
    super(player);
    this.name = player.getName();
  }

  public Player player() {
    return (Player) handle();
  }

  public void rebind(Player player) {
    super.rebind(player);
  }

  @Override
  public boolean isValid() {
    Player player = player();
    return player.isOnline() && player.isValid();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean isOnline() {
    return player().isOnline();
  }

  @Override
  public boolean isFakePlayer() {
    return !BukkitNatives.isServerPlayer(player());
  }

  @Override
  public Vec3 movement() {
    return BukkitPlayerVersion.movement(player());
  }

  @Override
  public String locale() {
    return player().getLocale();
  }

  @Override
  public GameMode gameMode() {
    return BukkitConversions.gameMode(player().getGameMode());
  }

  @Override
  public void setGameMode(GameMode mode) {
    player().setGameMode(BukkitConversions.gameMode(mode));
  }

  @Override
  public boolean isSneaking() {
    return player().isSneaking();
  }

  @Override
  public boolean isSprinting() {
    return player().isSprinting();
  }

  @Override
  public void setSprinting(boolean sprinting) {
    player().setSprinting(sprinting);
  }

  @Override
  public boolean isFlying() {
    return player().isFlying();
  }

  @Override
  public void setFlying(boolean flying) {
    player().setFlying(flying);
  }

  @Override
  public boolean allowFlight() {
    Player player = player();
    return player.getAllowFlight() && !BukkitFlightRequests.granted(player);
  }

  @Override
  public void setAllowFlight(boolean allow) {
    Player player = player();
    BukkitFlightRequests.release(player);
    player.setAllowFlight(allow);
  }

  @Override
  public boolean grantFlightRequests() {
    return BukkitFlightRequests.grant(player());
  }

  @Override
  public void revokeFlightRequests() {
    BukkitFlightRequests.revoke(player());
  }

  @Override
  public boolean flightRequestsGranted() {
    return BukkitFlightRequests.granted(player());
  }

  @Override
  public boolean isBlocking() {
    return player().isBlocking();
  }

  @Override
  public boolean isOnTeamWith(PlayerRef other) {
    Player owner = player();
    Team team = owner.getScoreboard().getEntryTeam(owner.getName());
    return team != null && team.hasEntry(other.name());
  }

  @Override
  public Pose pose() {
    return corePose(player().getPose());
  }

  @Override
  public boolean hasFixedPose() {
    return PaperCompat.hasFixedPose(player());
  }

  @Override
  public void setPose(Pose pose, boolean fixed) {
    PaperCompat.setPose(player(), bukkitPose(pose), fixed);
  }

  @Override
  public boolean sendVerticalMotion(double verticalVelocity) {
    return clientAccess().map((PlayerClientAccess access) -> access.sendVerticalMotion(player(), verticalVelocity)).orElse(false);
  }

  @Override
  public boolean sendClimbableTagOverride(Id block) {
    Material suppressed = block == null ? null : BukkitResolve.material(block);
    if (block != null && suppressed == null) {
      return false;
    }
    return clientAccess().map((PlayerClientAccess access) -> access.createBlockTags().sendClimbingState(player(), suppressed))
        .orElse(false);
  }

  private static Pose corePose(org.bukkit.entity.Pose pose) {
    for (Pose candidate : Pose.values()) {
      if (candidate.name().equals(pose.name())) {
        return candidate;
      }
    }
    return Pose.STANDING;
  }

  private static org.bukkit.entity.Pose bukkitPose(Pose pose) {
    for (org.bukkit.entity.Pose candidate : org.bukkit.entity.Pose.values()) {
      if (candidate.name().equals(pose.name())) {
        return candidate;
      }
    }
    return org.bukkit.entity.Pose.STANDING;
  }

  private static Optional<PlayerClientAccess> clientAccess() {
    Optional<PlayerClientAccess> access = clientAccess;
    if (access == null) {
      access = BukkitNatives.find(PlayerClientAccess.class);
      clientAccess = access;
    }
    return access;
  }

  @Override
  public boolean isSleeping() {
    return player().isSleeping();
  }

  @Override
  public float walkSpeed() {
    return player().getWalkSpeed();
  }

  @Override
  public void setWalkSpeed(float speed) {
    player().setWalkSpeed(speed);
  }

  @Override
  public PlayerInventoryRef inventory() {
    return new BukkitPlayerInventoryRef(player().getInventory());
  }

  @Override
  public InventoryRef enderChest() {
    return new BukkitInventoryRef(player().getEnderChest());
  }

  @Override
  public void closeInventory() {
    player().closeInventory();
  }

  @Override
  public void updateInventory() {
    player().updateInventory();
  }

  @Override
  public boolean hasOpenContainer() {
    return player().getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING;
  }

  @Override
  public void openStation(CraftingStation station) {
    Player player = player();
    switch (station) {
      case WORKBENCH -> player.openWorkbench(null, true);
      case GRINDSTONE -> PaperCompat.openGrindstone(player);
      case ANVIL -> PaperCompat.openAnvil(player);
      case STONECUTTER -> PaperCompat.openStonecutter(player);
      case CARTOGRAPHY -> PaperCompat.openCartographyTable(player);
      case LOOM -> PaperCompat.openLoom(player);
    }
  }

  @Override
  public EntityRef openMerchant() {
    if (!(player().getOpenInventory().getTopInventory() instanceof MerchantInventory merchant)) {
      return null;
    }
    if (merchant.getMerchant() instanceof Entity trader) {
      return BukkitRefs.entity(trader);
    }
    return PaperApi.holder(merchant) instanceof Entity holder ? BukkitRefs.entity(holder) : null;
  }

  @Override
  public InventoryRef openMenuInventory() {
    Inventory top = player().getOpenInventory().getTopInventory();
    return top.getType() == InventoryType.CRAFTING ? null : new BukkitInventoryRef(top);
  }

  @Override
  public Loc openMenuLocation() {
    Inventory top = player().getOpenInventory().getTopInventory();
    if (top.getType() == InventoryType.CRAFTING) {
      return null;
    }
    Location location = top.getLocation();
    return location == null ? null : BukkitRefs.loc(location);
  }

  @Override
  public int enchantmentSeed() {
    return player().getEnchantmentSeed();
  }

  @Override
  public void setEnchantmentSeed(int seed) {
    player().setEnchantmentSeed(seed);
  }

  @Override
  public Optional<BlockRef> openContainerBlock() {
    InventoryHolder holder = PaperApi.holder(player().getOpenInventory().getTopInventory());
    if (holder instanceof BlockInventoryHolder blockHolder) {
      return Optional.of(BukkitRefs.block(blockHolder.getBlock()));
    }
    if (holder instanceof DoubleChest chest && PaperApi.leftSide(chest) instanceof BlockInventoryHolder left) {
      return Optional.of(BukkitRefs.block(left.getBlock()));
    }
    return Optional.empty();
  }

  @Override
  public int foodLevel() {
    return player().getFoodLevel();
  }

  @Override
  public void setFoodLevel(int level) {
    player().setFoodLevel(level);
  }

  @Override
  public float saturation() {
    return player().getSaturation();
  }

  @Override
  public void setSaturation(float saturation) {
    player().setSaturation(saturation);
  }

  @Override
  public float exhaustion() {
    return player().getExhaustion();
  }

  @Override
  public void setExhaustion(float exhaustion) {
    player().setExhaustion(exhaustion);
  }

  @Override
  public int level() {
    return player().getLevel();
  }

  @Override
  public void setLevel(int level) {
    player().setLevel(level);
  }

  @Override
  public float expProgress() {
    return player().getExp();
  }

  @Override
  public void setExpProgress(float progress) {
    player().setExp(Math.max(0.0F, Math.min(1.0F, progress)));
  }

  @Override
  public int totalExperience() {
    return player().getTotalExperience();
  }

  @Override
  public void giveExp(int amount) {
    player().giveExp(amount);
  }

  @Override
  public void giveExpLevels(int levels) {
    player().giveExpLevels(levels);
  }

  @Override
  public void sendExperienceChange(float progress, int level) {
    player().sendExperienceChange(progress, level);
  }

  @Override
  public void setCompassTarget(Loc target) {
    player().setCompassTarget(BukkitRefs.location(target));
  }

  @Override
  public void saveData() {
    player().saveData();
  }

  @Override
  public boolean hasCooldown(Id item) {
    return cooldownTicks(item) > 0;
  }

  @Override
  public int cooldownTicks(Id item) {
    return item == null ? 0 : BukkitPlayerVersion.cooldownTicks(player(), item);
  }

  @Override
  public void setCooldown(Id item, int ticks) {
    if (item != null) {
      BukkitPlayerVersion.setCooldown(player(), item, Math.max(0, ticks));
    }
  }

  @Override
  public boolean hasPermission(String node) {
    return player().hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return player().isOp();
  }

  @Override
  public void sendMessage(String markup) {
    ComponentMessenger.sendMarkup(player(), markup);
  }

  @Override
  public void sendActionBar(String markup) {
    ComponentMessenger.sendActionBarMarkup(player(), markup);
  }

  @Override
  public void sendTitle(String titleMarkup, String subtitleMarkup, int fadeInTicks, int stayTicks, int fadeOutTicks) {
    ComponentMessenger.showTitleMarkup(player(), titleMarkup == null ? "" : titleMarkup,
        subtitleMarkup == null ? "" : subtitleMarkup, ticks(fadeInTicks), ticks(stayTicks), ticks(fadeOutTicks));
  }

  @Override
  public void playSound(Loc at, Id sound, SoundCategory category, float volume, float pitch) {
    Location location = at == null ? player().getLocation() : BukkitRefs.location(at);
    if (location == null || sound == null) {
      return;
    }
    org.bukkit.Sound resolved = BukkitResolve.sound(sound);
    if (resolved != null) {
      player().playSound(location, resolved, BukkitConversions.category(category), volume, pitch);
    } else {
      player().playSound(location, sound.toString(), BukkitConversions.category(category), volume, pitch);
    }
  }

  @Override
  public Loc respawnLocation() {
    Location respawn = BukkitPlayerVersion.respawnLocation(player());
    if (respawn != null) {
      return BukkitRefs.loc(respawn);
    }
    List<World> worlds = Bukkit.getWorlds();
    return worlds.isEmpty() ? null : BukkitRefs.loc(worlds.getFirst().getSpawnLocation());
  }

  @Override
  public Loc bedSpawnLocation() {
    Location respawn = BukkitPlayerVersion.respawnLocation(player());
    return respawn == null ? null : BukkitRefs.loc(respawn);
  }

  @Override
  public EntityRef spectatorTarget() {
    Entity target = player().getSpectatorTarget();
    return target == null ? null : BukkitRefs.entity(target);
  }

  @Override
  public void setSpectatorTarget(EntityRef target) {
    Player player = player();
    if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
      return;
    }
    player.setSpectatorTarget(target == null ? null : BukkitRefs.entity(target));
  }

  private static Duration ticks(int ticks) {
    return Duration.ofMillis(Math.max(0L, ticks) * 50L);
  }

  @Override
  public InventoryRef openBlockInventory(BlockRef block) {
    if (!(BukkitRefs.block(block).getState() instanceof InventoryHolder holder)) {
      return null;
    }
    return topInventory(player().openInventory(holder.getInventory()));
  }

  @Override
  public InventoryRef openEnderChest() {
    Player player = player();
    return topInventory(player.openInventory(player.getEnderChest()));
  }

  @Override
  public ProjectileRef launchProjectile(Id type, Consumer<ProjectileRef> beforeSpawn) {
    EntityType entityType = BukkitRefs.entityType(type);
    Class<? extends Entity> entityClass = entityType == null ? null : entityType.getEntityClass();
    if (entityClass == null || !Projectile.class.isAssignableFrom(entityClass)) {
      return null;
    }
    Projectile projectile = PaperApi.launchProjectile(player(), entityClass.asSubclass(Projectile.class),
        (Projectile launched) -> beforeSpawn.accept(BukkitRefs.projectile(launched)));
    return BukkitRefs.projectile(projectile);
  }

  private static InventoryRef topInventory(InventoryView view) {
    return view == null ? null : new BukkitInventoryRef(view.getTopInventory());
  }
}
