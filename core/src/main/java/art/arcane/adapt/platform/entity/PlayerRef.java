package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Online player.
 *
 * <p>Staleness: unlike other entities, a player handle stays valid across death, respawn and
 * dimension changes for as long as the player is online (vanilla replaces the player object on
 * respawn; adapters rebind by UUID). After quit {@link #isOnline()} is false and every other call
 * except {@link #uuid()} and {@link #name()} is undefined. Code that keeps per-player state should
 * key it by {@link #uuid()} and clear it on quit.
 *
 * <p>{@link #name()} and {@link #isOnline()} are safe on any thread. Messages, action bars and titles
 * may be sent from any thread; adapters hop to the right thread where the platform requires it.
 * Text uses Adapt markup.
 */
public interface PlayerRef extends LivingRef {
  String name();

  boolean isOnline();

  /** True when the handle is not the platform's own connected player class: mod FakePlayers, NPC plugin players. */
  boolean isFakePlayer();

  String locale();

  GameMode gameMode();

  void setGameMode(GameMode mode);

  boolean isSneaking();

  boolean isSprinting();

  void setSprinting(boolean sprinting);

  boolean isFlying();

  void setFlying(boolean flying);

  boolean allowFlight();

  void setAllowFlight(boolean allow);

  boolean isBlocking();

  /** True when {@code other} is an entry of the team this player belongs to on this player's scoreboard. */
  boolean isOnTeamWith(PlayerRef other);

  Pose pose();

  /** True while a pose set with {@code fixed} holds against the game's own pose updates. */
  boolean hasFixedPose();

  /** Sets the pose; when {@code fixed}, the game keeps it until a later call releases it. */
  void setPose(Pose pose, boolean fixed);

  /**
   * Replaces the vertical motion, keeps the horizontal movement the client last reported, and sends the result to
   * the client at once. False when the platform cannot.
   */
  boolean sendVerticalMotion(double verticalVelocity);

  /**
   * Resends this client's block tags with {@code block} removed from {@code minecraft:climbable}, or the server's
   * real tags when {@code block} is null. The server's own tags are untouched. False when the platform cannot.
   */
  boolean sendClimbableTagOverride(Id block);

  boolean isSleeping();

  float walkSpeed();

  void setWalkSpeed(float speed);

  PlayerInventoryRef inventory();

  InventoryRef enderChest();

  void closeInventory();

  /** Resends the open window and inventory contents to the client. */
  void updateInventory();

  /** True when a container window is open instead of the player's own inventory. */
  boolean hasOpenContainer();

  /** Opens a utility block window without a block, like the portable crafting table. */
  void openStation(CraftingStation station);

  /** Block of the container whose inventory the player has open (left half for double chests), if any. */
  Optional<BlockRef> openContainerBlock();

  /** Entity the player is trading with in an open merchant screen, null when no merchant screen is open. */
  EntityRef openMerchant();

  /**
   * Top inventory of the open window in raw-slot order (enchanting table, anvil and grindstone slots including the
   * result), or null when only the player's own inventory is open. Refs of the same open window are equal.
   */
  InventoryRef openMenuInventory();

  /** Block position behind the open window, or null when there is none or the platform cannot tell. */
  Loc openMenuLocation();

  /** Seed the enchanting table uses to roll this player's offers. */
  int enchantmentSeed();

  void setEnchantmentSeed(int seed);

  int foodLevel();

  void setFoodLevel(int level);

  float saturation();

  void setSaturation(float saturation);

  float exhaustion();

  void setExhaustion(float exhaustion);

  int level();

  void setLevel(int level);

  /** Progress to the next level, 0 to 1. */
  float expProgress();

  void setExpProgress(float progress);

  int totalExperience();

  /** Adds experience points, applying vanilla level-up. */
  void giveExp(int amount);

  void giveExpLevels(int levels);

  /** Shows the given bar progress and level on the player's client only; the real experience is untouched. */
  void sendExperienceChange(float progress, int level);

  /** Points the player's compass at {@code target}. */
  void setCompassTarget(Loc target);

  /** Writes the player's data file now. */
  void saveData();

  boolean hasCooldown(Id item);

  int cooldownTicks(Id item);

  void setCooldown(Id item, int ticks);

  boolean hasPermission(String node);

  /** Server operator status, independent of permission grants. */
  boolean isOp();

  void sendMessage(String markup);

  void sendActionBar(String markup);

  void sendTitle(String titleMarkup, String subtitleMarkup, int fadeInTicks, int stayTicks, int fadeOutTicks);

  /** Sound only this player hears. */
  void playSound(Loc at, Id sound, SoundCategory category, float volume, float pitch);

  Loc respawnLocation();

  /** Bed or anchor spawn, or null. */
  Loc bedSpawnLocation();

  /** Entity a spectator is viewing through, or null. */
  EntityRef spectatorTarget();

  /** Sets the entity a spectator views through; null returns the camera to the player. Ignored outside spectator mode. */
  void setSpectatorTarget(EntityRef target);

  /**
   * Opens the container of the block for this player from any distance, as Bukkit's openInventory with the block's
   * inventory does (both halves for a double chest). Returns the top inventory of the opened view, or null when
   * the block has no container or nothing opened.
   */
  InventoryRef openBlockInventory(BlockRef block);

  /** Opens this player's ender chest. Returns the top inventory of the opened view, or null when nothing opened. */
  InventoryRef openEnderChest();

  /**
   * Launches a projectile of the entity type from this player the way Bukkit's launchProjectile does (shooter set,
   * thrown along the look direction). {@code beforeSpawn} runs before the projectile enters the world, so launch
   * listeners already see what it sets. Returns the projectile, or null when the type cannot be launched.
   */
  ProjectileRef launchProjectile(Id type, Consumer<ProjectileRef> beforeSpawn);
}
