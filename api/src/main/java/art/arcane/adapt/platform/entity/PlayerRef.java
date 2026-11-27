package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
@ApiStatus.AvailableSince("1.0.0")
public interface PlayerRef extends LivingRef {
  /** Account name; safe on any thread. */
  String name();

  /** True while the player is connected; false after quit. */
  boolean isOnline();

  /** True when the handle is not the platform's own connected player class: mod FakePlayers, NPC plugin players. */
  boolean isFakePlayer();

  /** Language the client reports, in vanilla form such as {@code en_us}. */
  String locale();

  /** Current game mode. */
  GameMode gameMode();

  /** Switches the game mode. */
  void setGameMode(GameMode mode);

  /** True while the player holds sneak. */
  boolean isSneaking();

  /** True while the player sprints. */
  boolean isSprinting();

  /** Starts or stops sprinting. */
  void setSprinting(boolean sprinting);

  /** True while the player is flying (creative-style flight, not elytra gliding). */
  boolean isFlying();

  /** Starts or stops flight. */
  void setFlying(boolean flying);

  /** True when the player may fly. */
  boolean allowFlight();

  /** Grants or takes away the ability to fly. */
  void setAllowFlight(boolean allow);

  /**
   * Lets the client ask to start flying (its double tap of jump) without letting the player fly or changing fall
   * damage, for servers that do not receive the player input packet. While granted, {@link #allowFlight()} reports
   * false and {@link #setAllowFlight} replaces the grant. False when the player may already fly, is not in survival or
   * adventure mode, or the platform cannot.
   */
  default boolean grantFlightRequests() {
    return false;
  }

  /** Ends a grant from {@link #grantFlightRequests()}, leaving flight exactly as it was before; no-op without one. */
  default void revokeFlightRequests() {
  }

  /** True while a grant from {@link #grantFlightRequests()} holds. */
  default boolean flightRequestsGranted() {
    return false;
  }

  /** True while a raised shield is blocking. */
  boolean isBlocking();

  /** True when {@code other} is an entry of the team this player belongs to on this player's scoreboard. */
  boolean isOnTeamWith(PlayerRef other);

  /** Current pose. */
  Pose pose();

  /** True while a pose set with {@code fixed} holds against the game's own pose updates. */
  boolean hasFixedPose();

  /** Sets the pose; when {@code fixed}, the game keeps it until a later call releases it. */
  void setPose(Pose pose, boolean fixed);

  /**
   * Movement this player's client reported over the last tick. Servers before 1.21.2 receive no movement keys, so
   * input is inferred from this; the server-side {@link #velocity()} of a player never includes walking. Defaults to
   * {@link #velocity()} where the platform reports input directly.
   */
  default Vec3 movement() {
    return velocity();
  }

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

  /** True while the player lies in a bed. */
  boolean isSleeping();

  /** Walk speed on Bukkit's scale: 0.2 by default. */
  float walkSpeed();

  /** Sets the walk speed on Bukkit's scale, where 0.2 is the default. */
  void setWalkSpeed(float speed);

  /** The player's own inventory: hotbar, main slots, armor, off hand and cursor. */
  PlayerInventoryRef inventory();

  /** The player's ender chest contents, whether or not it is open. */
  InventoryRef enderChest();

  /** Closes whatever window the player has open. */
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

  /** Replaces the enchanting seed, which rerolls the offers the table shows. */
  void setEnchantmentSeed(int seed);

  /** Hunger points, 0 to 20. */
  int foodLevel();

  /** Sets the hunger points (vanilla range 0 to 20). */
  void setFoodLevel(int level);

  /** Saturation, which drains before hunger points do. */
  float saturation();

  /** Sets the saturation. */
  void setSaturation(float saturation);

  /** Exhaustion built up by actions; every 4.0 costs one saturation or hunger point. */
  float exhaustion();

  /** Sets the built-up exhaustion. */
  void setExhaustion(float exhaustion);

  /** Experience level. */
  int level();

  /** Sets the experience level, leaving the bar progress alone. */
  void setLevel(int level);

  /** Progress to the next level, 0 to 1. */
  float expProgress();

  /** Sets the progress to the next level, 0 to 1. */
  void setExpProgress(float progress);

  /** Experience points collected in total, as the vanilla score counts them. */
  int totalExperience();

  /** Adds experience points, applying vanilla level-up. */
  void giveExp(int amount);

  /** Adds experience levels; a negative amount removes them. */
  void giveExpLevels(int levels);

  /** Shows the given bar progress and level on the player's client only; the real experience is untouched. */
  void sendExperienceChange(float progress, int level);

  /** Points the player's compass at {@code target}. */
  void setCompassTarget(Loc target);

  /** Writes the player's data file now. */
  void saveData();

  /** True while the item type is on cooldown for this player. */
  boolean hasCooldown(Id item);

  /** Ticks left on the item type's cooldown; 0 when none. */
  int cooldownTicks(Id item);

  /** Puts the item type on cooldown for {@code ticks}; 0 clears it. */
  void setCooldown(Id item, int ticks);

  /**
   * True when the player holds the permission node, by the platform's permission system: Bukkit permissions, or on
   * modded the NeoForge or Fabric permission API, else the node's registered default with operators holding
   * operator-default nodes.
   */
  boolean hasPermission(String node);

  /** Server operator status, independent of permission grants. */
  boolean isOp();

  /** Sends a chat message. */
  void sendMessage(String markup);

  /** Shows text above the hotbar. */
  void sendActionBar(String markup);

  /** Shows a title and subtitle with the given fade and stay times in ticks; a null subtitle shows none. */
  void sendTitle(String titleMarkup, String subtitleMarkup, int fadeInTicks, int stayTicks, int fadeOutTicks);

  /** Sound only this player hears, at {@code at} or, when it is null, at the player. */
  void playSound(@Nullable Loc at, Id sound, SoundCategory category, float volume, float pitch);

  /** Where the player respawns: the bed or anchor spawn, else the main world's spawn; null when neither exists. */
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
