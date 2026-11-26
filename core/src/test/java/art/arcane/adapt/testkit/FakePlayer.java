package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Online survival player. Records chat, action bar and titles; permissions are granted explicitly or by op. */
public final class FakePlayer extends FakeLiving implements PlayerRef {
  private int savedData;
  private final String name;
  private final FakeInventory inventory = new FakeInventory();
  private final FakeInventory enderChest = new FakeInventory();
  private final Map<Id, Integer> cooldowns = new HashMap<>();
  private final Set<String> permissions = new HashSet<>();
  private final Set<String> denied = new HashSet<>();
  private final List<String> messages = new ArrayList<>();
  private final List<String> actionBars = new ArrayList<>();
  private final List<Double> verticalMotions = new ArrayList<>();
  private final List<Id> climbableTagOverrides = new ArrayList<>();
  private final List<Id> heardSounds = new ArrayList<>();
  private boolean online = true;
  private boolean fakePlayer;
  private boolean op = true;
  private GameMode gameMode = GameMode.SURVIVAL;
  private EntityRef spectatorTarget;
  private boolean sneaking;
  private boolean blocking;
  private final Set<String> teammates = new HashSet<>();
  private boolean sprinting;
  private boolean flying;
  private boolean allowFlight;
  private boolean flightRequests;
  private float walkSpeed = 0.2F;
  private int foodLevel = 20;
  private float saturation = 5F;
  private float exhaustion;
  private int level;
  private float expProgress;
  private int totalExperience;
  private Pose pose = Pose.STANDING;
  private boolean fixedPose;
  private BlockRef openContainer;
  private CraftingStation openStation;
  private int inventoryUpdates;
  private EntityRef openMerchant;
  private InventoryRef openMenuInventory;
  private Loc openMenuLocation;
  private int enchantmentSeed;
  private Loc compassTarget;
  private Vec3 movement;
  private final List<String> experienceChanges = new ArrayList<>();
  private final List<FakeProjectile> launched = new ArrayList<>();

  public FakePlayer(String name, Loc location) {
    super(Id.minecraft("player"), location);
    this.name = name;
  }

  /** Movement the client reported, kept apart from the server velocity as on servers before 1.21.2. */
  public void setMovement(Vec3 movement) {
    this.movement = movement;
  }

  @Override
  public Vec3 movement() {
    return movement == null ? velocity() : movement;
  }

  public FakePlayer holding(ItemRef item) {
    inventory.setMainHand(item);
    return this;
  }

  public void setSneaking(boolean sneaking) {
    this.sneaking = sneaking;
  }

  public void setBlocking(boolean blocking) {
    this.blocking = blocking;
  }

  /** Lists {@code other} on this player's scoreboard team. */
  public void addTeammate(PlayerRef other) {
    teammates.add(other.name());
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public void setFakePlayer(boolean fakePlayer) {
    this.fakePlayer = fakePlayer;
  }

  public void setOp(boolean op) {
    this.op = op;
  }

  public void grant(String node) {
    denied.remove(node);
    permissions.add(node);
  }

  /** Negates a node explicitly, which also overrides op, as a Bukkit permission attachment does. */
  public void deny(String node) {
    permissions.remove(node);
    denied.add(node);
  }

  public boolean isPermissionSet(String node) {
    return permissions.contains(node) || denied.contains(node);
  }

  public List<String> messages() {
    return List.copyOf(messages);
  }

  public List<String> actionBars() {
    return List.copyOf(actionBars);
  }

  @Override
  public ItemRef equipment(EquipmentSlot slot) {
    return switch (slot) {
      case MAIN_HAND -> inventory.mainHand();
      case OFF_HAND -> inventory.offHand();
      case HEAD, CHEST, LEGS, FEET -> inventory.armor(slot);
      default -> super.equipment(slot);
    };
  }

  @Override
  public void setEquipment(EquipmentSlot slot, ItemRef item) {
    switch (slot) {
      case MAIN_HAND -> inventory.setMainHand(item);
      case OFF_HAND -> inventory.setOffHand(item);
      case HEAD, CHEST, LEGS, FEET -> inventory.setArmor(slot, item);
      default -> super.setEquipment(slot, item);
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean isOnline() {
    return online;
  }

  @Override
  public boolean isFakePlayer() {
    return fakePlayer;
  }

  @Override
  public String locale() {
    return "en_us";
  }

  @Override
  public GameMode gameMode() {
    return gameMode;
  }

  @Override
  public void setGameMode(GameMode mode) {
    gameMode = mode;
  }

  @Override
  public boolean isSneaking() {
    return sneaking;
  }

  @Override
  public boolean isSprinting() {
    return sprinting;
  }

  @Override
  public void setSprinting(boolean sprinting) {
    this.sprinting = sprinting;
  }

  @Override
  public boolean isFlying() {
    return flying;
  }

  @Override
  public void setFlying(boolean flying) {
    this.flying = flying;
  }

  @Override
  public boolean allowFlight() {
    return allowFlight && !flightRequests;
  }

  @Override
  public void setAllowFlight(boolean allow) {
    flightRequests = false;
    allowFlight = allow;
    if (!allow) {
      flying = false;
    }
  }

  @Override
  public boolean grantFlightRequests() {
    if (flightRequests || allowFlight || (gameMode != GameMode.SURVIVAL && gameMode != GameMode.ADVENTURE)) {
      return false;
    }
    flightRequests = true;
    allowFlight = true;
    return true;
  }

  @Override
  public void revokeFlightRequests() {
    if (!flightRequests) {
      return;
    }
    flightRequests = false;
    allowFlight = false;
    flying = false;
  }

  @Override
  public boolean flightRequestsGranted() {
    return flightRequests;
  }

  /** The raw may-fly ability as the client sees it, grants included. */
  public boolean mayFly() {
    return allowFlight;
  }

  @Override
  public boolean isBlocking() {
    return blocking;
  }

  @Override
  public boolean isOnTeamWith(PlayerRef other) {
    return teammates.contains(other.name());
  }

  @Override
  public Pose pose() {
    return pose;
  }

  @Override
  public boolean hasFixedPose() {
    return fixedPose;
  }

  @Override
  public void setPose(Pose pose, boolean fixed) {
    this.pose = pose;
    this.fixedPose = fixed;
  }

  @Override
  public boolean sendVerticalMotion(double verticalVelocity) {
    verticalMotions.add(verticalVelocity);
    return true;
  }

  @Override
  public boolean sendClimbableTagOverride(Id block) {
    climbableTagOverrides.add(block);
    return true;
  }

  /** Vertical motions sent to this client, oldest first. */
  public List<Double> verticalMotions() {
    return verticalMotions;
  }

  /** Climbable tag overrides sent to this client, oldest first; null entries restored the real tags. */
  public List<Id> climbableTagOverrides() {
    return climbableTagOverrides;
  }

  @Override
  public boolean isSleeping() {
    return false;
  }

  @Override
  public float walkSpeed() {
    return walkSpeed;
  }

  @Override
  public void setWalkSpeed(float speed) {
    walkSpeed = speed;
  }

  @Override
  public PlayerInventoryRef inventory() {
    return inventory;
  }

  @Override
  public InventoryRef enderChest() {
    return enderChest;
  }

  @Override
  public void closeInventory() {
    openContainer = null;
  }

  @Override
  public void updateInventory() {
    inventoryUpdates++;
  }

  /** How often {@link #updateInventory()} was called. */
  public int inventoryUpdates() {
    return inventoryUpdates;
  }

  @Override
  public boolean hasOpenContainer() {
    return openContainer != null || openStation != null;
  }

  @Override
  public void openStation(CraftingStation station) {
    openStation = station;
  }

  /** The station window last opened through {@link #openStation}, or null. */
  public CraftingStation openStation() {
    return openStation;
  }

  /** Opens the container at {@code block} (null closes it). */
  public FakePlayer openContainer(BlockRef block) {
    openContainer = block;
    return this;
  }

  /** Opens (or with null closes) a trading screen with {@code merchant}. */
  public FakePlayer openMerchant(EntityRef merchant) {
    openMerchant = merchant;
    return this;
  }

  @Override
  public EntityRef openMerchant() {
    return openMerchant;
  }

  @Override
  public Optional<BlockRef> openContainerBlock() {
    return Optional.ofNullable(openContainer);
  }

  public FakePlayer openMenu(InventoryRef inventory, Loc location) {
    openMenuInventory = inventory;
    openMenuLocation = location;
    return this;
  }

  @Override
  public InventoryRef openMenuInventory() {
    return openMenuInventory;
  }

  @Override
  public Loc openMenuLocation() {
    return openMenuLocation;
  }

  @Override
  public int enchantmentSeed() {
    return enchantmentSeed;
  }

  @Override
  public void setEnchantmentSeed(int seed) {
    enchantmentSeed = seed;
  }

  @Override
  public int foodLevel() {
    return foodLevel;
  }

  @Override
  public void setFoodLevel(int level) {
    foodLevel = level;
  }

  @Override
  public float saturation() {
    return saturation;
  }

  @Override
  public void setSaturation(float saturation) {
    this.saturation = saturation;
  }

  @Override
  public float exhaustion() {
    return exhaustion;
  }

  @Override
  public void setExhaustion(float exhaustion) {
    this.exhaustion = exhaustion;
  }

  @Override
  public int level() {
    return level;
  }

  @Override
  public void setLevel(int level) {
    this.level = level;
  }

  @Override
  public float expProgress() {
    return expProgress;
  }

  @Override
  public void setExpProgress(float progress) {
    expProgress = progress;
  }

  @Override
  public int totalExperience() {
    return totalExperience;
  }

  @Override
  public void giveExp(int amount) {
    totalExperience += amount;
  }

  @Override
  public void giveExpLevels(int levels) {
    level += levels;
  }

  @Override
  public void sendExperienceChange(float progress, int level) {
    experienceChanges.add(progress + "/" + level);
  }

  /** Client-only bar updates sent with {@link #sendExperienceChange}, as {@code progress/level}. */
  public List<String> experienceChanges() {
    return experienceChanges;
  }

  @Override
  public void setCompassTarget(Loc target) {
    compassTarget = target;
  }

  public Loc compassTarget() {
    return compassTarget;
  }

  @Override
  public void saveData() {
    savedData++;
  }

  /** Number of saveData calls. */
  public int savedData() {
    return savedData;
  }

  @Override
  public boolean hasCooldown(Id item) {
    return cooldownTicks(item) > 0;
  }

  @Override
  public int cooldownTicks(Id item) {
    return cooldowns.getOrDefault(item, 0);
  }

  @Override
  public void setCooldown(Id item, int ticks) {
    cooldowns.put(item, ticks);
  }

  @Override
  public boolean hasPermission(String node) {
    return !denied.contains(node) && (op || permissions.contains(node));
  }

  @Override
  public boolean isOp() {
    return op;
  }

  @Override
  public void sendMessage(String markup) {
    messages.add(markup);
  }

  @Override
  public void sendActionBar(String markup) {
    actionBars.add(markup);
  }

  @Override
  public void sendTitle(String titleMarkup, String subtitleMarkup, int fadeInTicks, int stayTicks, int fadeOutTicks) {
    messages.add(titleMarkup + " / " + subtitleMarkup);
  }

  @Override
  public synchronized void playSound(Loc at, Id sound, SoundCategory category, float volume, float pitch) {
    heardSounds.add(sound);
  }

  /** Every sound played to this player, in order. */
  public synchronized List<Id> heardSounds() {
    return List.copyOf(heardSounds);
  }

  @Override
  public Loc respawnLocation() {
    return null;
  }

  @Override
  public Loc bedSpawnLocation() {
    return null;
  }

  @Override
  public EntityRef spectatorTarget() {
    return spectatorTarget;
  }

  @Override
  public void setSpectatorTarget(EntityRef target) {
    if (gameMode() == GameMode.SPECTATOR) {
      spectatorTarget = target;
    }
  }

  @Override
  public InventoryRef openBlockInventory(BlockRef block) {
    InventoryRef container = block.container();
    if (container == null) {
      return null;
    }
    openContainer = block;
    openMenuInventory = container;
    openMenuLocation = block.location();
    return container;
  }

  @Override
  public InventoryRef openEnderChest() {
    openMenuInventory = enderChest;
    openMenuLocation = null;
    return enderChest;
  }

  @Override
  public ProjectileRef launchProjectile(Id type, Consumer<ProjectileRef> beforeSpawn) {
    FakeProjectile projectile = new FakeProjectile(type, eyeLocation());
    projectile.setShooter(this);
    projectile.setVelocity(eyeLocation().direction().multiply(1.5D));
    beforeSpawn.accept(projectile);
    launched.add(projectile);
    if (world() instanceof FakeWorld fake) {
      fake.add(projectile);
    }
    return projectile;
  }

  /** Projectiles launched through {@link #launchProjectile}, in order. */
  public List<FakeProjectile> launched() {
    return List.copyOf(launched);
  }
}
