package art.arcane.adapt.content.adaptation.blocking.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.architect.ArchitectTemporaryBlocks;
import art.arcane.adapt.content.adaptation.blocking.BlockingBrace;
import art.arcane.adapt.content.adaptation.blocking.BlockingPreferences;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shield Wall, Interpose and Bastion Stance fused into a planted barricade. Sneak still with the shield raised for a
 * moment, then let go of sneak: the shield becomes a short wall of tinted glass in front of you that stops projectiles
 * and slows hostile mobs against it, and everyone behind it takes less damage from blows, projectiles and blasts. While
 * the wall stands the shield lives in your player data, never in the world, so it comes back exactly once: when you
 * pick the wall up, when it expires, when you walk 24 blocks away, change worlds, log out, or respawn after a death.
 * While blocking you take the same reduction yourself and cannot be knocked back.
 */
public class LivingWall extends SkillUpgrade<LivingWall.Config> {
  public static final Id ID = Id.adapt("blocking/upgrade/living_wall");

  private static final Set<DamageCause> COVERED = EnumSet.of(DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK,
      DamageCause.PROJECTILE, DamageCause.BLOCK_EXPLOSION, DamageCause.ENTITY_EXPLOSION, DamageCause.FALLING_BLOCK);
  private static final DataKey<byte[]> PLANTED_SHIELD = DataKey.adapt("planted-bulwark-shield", DataCodecs.BYTE_ARRAY);
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");
  private static final int WALL_HEIGHT = 2;
  private static final int TICK_INTERVAL = 10;
  private static final int TIMER_MARGIN_TICKS = 40;
  private static final double STILL_TOLERANCE_SQUARED = 0.04D;
  private final Map<UUID, LingeringSound> lingering = playerState();
  private final Map<UUID, Stance> stances = new ConcurrentHashMap<>();
  private final Map<UUID, Planted> planted = new ConcurrentHashMap<>();
  private final Map<Spot, Long> takenDown = new ConcurrentHashMap<>();
  private final Cooldowns plantCooldowns = cooldowns();
  private final ArchitectTemporaryBlocks wallBlocks;
  private final BlockingBrace brace = new BlockingBrace(this, playerState(), Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, new BlockingBrace.Stance() {
    @Override
    public boolean holds(PlayerRef p, int level) {
      return level > 0;
    }

    @Override
    public double amount(PlayerRef p, int level) {
      return p.isBlocking() && BlockingShields.held(LivingWall.this, p) ? 1D : 0D;
    }

    @Override
    public void braced(PlayerRef p) {
      cue(p, MutationCue.BOON);
      lingering.put(p.uuid(), linger(p));
    }

    @Override
    public void released(PlayerRef p) {
      LingeringSound sound = lingering.remove(p.uuid());
      if (sound != null) {
        sound.stop();
      }
    }
  });

  public LivingWall() {
    super(ID);
    needs(PlatformNeed.partialAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "no explosion knockback resistance"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
    wallBlocks = new ArchitectTemporaryBlocks(this, "blocking-planted-bulwark", (BlockRef block) -> { });
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.ALLIES, BlockingPreferences.RESERVE);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    brace.end(player.player());
    if (getActiveLevel(player.player()) > 0) {
      brace.start(player.player());
    }
  }

  @Override
  protected void onRuntimeActivated() {
    wallBlocks.recoverLoadedChunks();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getDamageReduction(level), 0), 1);
    statLore(v, Format.f(getWidth(level)), 2);
    statLore(v, Format.duration(getDurationTicks(level) * 50D, 0), 3);
    statLore(v, Format.duration(getCooldownMillis(level), 0), 4);
  }

  @Handle
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    Stance stance = stances.get(p.uuid());
    if (stance != null && !stance.still(e.to())) {
      stances.remove(p.uuid(), stance);
    }
    if (!brace.active(p) && hasActiveAdaptation(p)) {
      brace.start(p);
    }
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      brace.end(e.player());
      pickUp(e.player());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (e.isSneaking()) {
      if (planted.containsKey(id) || getActiveLevel(p) <= 0) {
        return;
      }
      Stance stance = new Stance(p.location());
      stances.put(id, stance);
      AdaptApi.scheduler().runEntity(p, () -> arm(p, stance), getConfig().stillTicks);
      return;
    }

    Stance stance = stances.remove(id);
    if (stance != null && stance.armed && p.isBlocking()) {
      plant(p);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    BlockRef clicked = e.clickedBlock();
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || clicked == null) {
      return;
    }
    Planted wall = planted.get(e.player().uuid());
    if (wall != null && wall.contains(clicked)) {
      e.setCancelled(true);
      pickUp(e.player());
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent e) {
    BlockRef block = e.block();
    Long until = takenDown.get(Spot.of(block));
    if (until == null) {
      return;
    }
    if (System.currentTimeMillis() >= until) {
      takenDown.remove(Spot.of(block));
    } else if (TINTED_GLASS.equals(block.type())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    wallBlocks.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    wallBlocks.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    wallBlocks.spare(e.blocks());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    wallBlocks.spare(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    wallBlocks.on(e);
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    AdaptApi.scheduler().runEntity(p, () -> returnShield(p), 1);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    stances.remove(e.player().uuid());
    pickUp(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    Planted wall = planted.remove(e.player().uuid());
    if (wall != null) {
      takeDown(wall);
    }
  }

  @Handle
  public void on(PlayerRespawnEvent e) {
    PlayerRef p = e.player();
    AdaptApi.scheduler().runEntity(p, () -> returnShield(p), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef victim) || e.damage() <= 0 || !COVERED.contains(e.cause())) {
      return;
    }
    EntityRef damager = e instanceof EntityDamageByEntityEvent byEntity ? byEntity.damager() : null;
    EntityRef attacker = damager instanceof ProjectileRef projectile ? projectile.shooter() : damager;

    Planted cover = null;
    double bestReduction = 0;
    for (Planted wall : planted.values()) {
      double reduction = getDamageReduction(wall.level);
      if (reduction > bestReduction && wall.shelters(victim.location(), getRange(wall.level))
          && covers(wall.owner, victim, attacker)) {
        bestReduction = reduction;
        cover = wall;
      }
    }

    if (cover != null) {
      double shielded = e.damage() * bestReduction;
      e.setDamage(Math.max(0, e.damage() - shielded));
      shieldFx(victim);
      PlayerRef owner = cover.owner;
      if (!owner.uuid().equals(victim.uuid())) {
        AdaptApi.scheduler().runEntity(owner, () -> credit(owner, damager, shielded));
      }
      return;
    }

    int level = getActiveLevel(victim);
    if (level <= 0 || !victim.isBlocking() || !BlockingShields.held(this, victim)
        || (attacker != null && attacker.uuid().equals(victim.uuid()))) {
      return;
    }
    double shielded = e.damage() * getDamageReduction(level);
    int cost = wearCost(shielded);
    if (!preference(victim, BlockingPreferences.RESERVE).permits(BlockingShields.preferred(victim), cost)) {
      return;
    }
    e.setDamage(Math.max(0, e.damage() - shielded));
    shieldFx(victim);
    AdaptApi.scheduler().runEntity(victim, () -> {
      BlockingShields.wear(this, victim, cost);
      credit(victim, damager, shielded);
    });
  }

  @Override
  public void unregister() {
    super.unregister();
    stances.clear();
    for (Planted wall : new ArrayList<>(planted.values())) {
      pickUp(wall.owner);
    }
    planted.clear();
    takenDown.clear();
    wallBlocks.clear();
    lingering.values().forEach(LingeringSound::stop);
    lingering.clear();
    brace.clear();
  }

  private void arm(PlayerRef p, Stance stance) {
    if (stances.get(p.uuid()) != stance || !p.isOnline() || !p.isSneaking() || !p.isBlocking()
        || !stance.still(p.location())) {
      stances.remove(p.uuid(), stance);
      return;
    }
    stance.armed = true;
    fx(p.location().add(0, 1.0D, 0), FxPriority.GAMEPLAY)
        .ring(Particles.CRIT, 0.8D, 10, 0)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.4F, 1.6F);
  }

  private void plant(PlayerRef p) {
    UUID id = p.uuid();
    int level = getActiveLevel(p);
    if (level <= 0 || planted.containsKey(id) || p.gameMode() == GameMode.CREATIVE || p.gameMode() == GameMode.SPECTATOR
        || !plantCooldowns.isReady(id, getCooldownMillis(level)) || p.data().get(PLANTED_SHIELD) != null) {
      return;
    }

    long now = System.currentTimeMillis();
    takenDown.values().removeIf((Long until) -> until <= now);
    PlayerInventoryRef inventory = p.inventory();
    boolean offHand = ItemKinds.isShield(inventory.offHand());
    ItemRef shield = offHand ? inventory.offHand() : inventory.mainHand();
    int cost = getConfig().plantDurability;
    if (!ItemKinds.isShield(shield) || !affords(shield, cost)
        || !preference(p, BlockingPreferences.RESERVE).permits(shield, cost)) {
      return;
    }

    Loc origin = p.location();
    Vec3 facing = facing(origin.direction());
    Vec3 lateral = new Vec3(-facing.z(), 0, facing.x());
    BlockPos base = Loc.of(origin.world(), origin.position().add(facing.multiply(1.5D))).blockPos();
    int width = getWidth(level);
    int timerTicks = getDurationTicks(level) + TIMER_MARGIN_TICKS;
    List<BlockRef> raised = new ArrayList<>();
    for (int offset = -(width / 2); offset <= width / 2; offset++) {
      for (int up = 0; up < WALL_HEIGHT; up++) {
        BlockRef block = origin.world().blockAt(base.offset((int) lateral.x() * offset, up, (int) lateral.z() * offset));
        if (wallBlocks.raise(p, block, timerTicks)) {
          raised.add(block);
        }
      }
    }
    if (raised.isEmpty()) {
      return;
    }

    ItemRef stored = shield.copy();
    if (!stored.isUnbreakable()) {
      stored.setDamage(Math.min(stored.maxDamage() - 1, stored.damage() + cost));
    }
    byte[] encoded;
    try {
      encoded = items().serialize(stored);
    } catch (Throwable error) {
      AdaptApi.log().error("Failed to store a planted shield for " + p.name() + ".", null);
      AdaptApi.log().error("LivingWall failed", error);
      raised.forEach((BlockRef block) -> wallBlocks.remove(block, false));
      return;
    }
    p.data().set(PLANTED_SHIELD, encoded);
    if (offHand) {
      inventory.setOffHand(items().empty());
    } else {
      inventory.setMainHand(items().empty());
    }

    Vec3 center = new Vec3(base.x() + 0.5D, base.y(), base.z() + 0.5D);
    Planted wall = new Planted(p, Loc.of(origin.world(), center), facing, lateral, width, raised, level,
        getDurationTicks(level), System.currentTimeMillis() + (timerTicks * 50L));
    planted.put(id, wall);
    plantCooldowns.mark(id);
    addStat(p, "blocking.living-wall.walls-planted", 1);
    cue(p, MutationCue.WORK);
    lingering.put(id, linger(p));
    fx(wall.center.add(0, 1.0D, 0), FxPriority.GAMEPLAY)
        .burst(Particles.CRIT, 12, 0.8D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 0.7F, Sounds.BLOCK_ANVIL_PLACE, 0.4F, 1.4F);
    AdaptApi.scheduler().runEntity(p, () -> tick(p, wall), TICK_INTERVAL);
  }

  private void tick(PlayerRef p, Planted wall) {
    if (planted.get(p.uuid()) != wall) {
      return;
    }
    wall.ticksLeft -= TICK_INTERVAL;
    if (!p.isOnline() || wall.ticksLeft <= 0 || !p.location().sameWorld(wall.center)
        || p.location().distance(wall.center) > getConfig().returnDistance) {
      pickUp(p);
      return;
    }
    AdaptApi.scheduler().runAt(wall.center, () -> slowMobs(wall));
    if (!AdaptApi.scheduler().runEntity(p, () -> tick(p, wall), TICK_INTERVAL)) {
      pickUp(p);
    }
  }

  private void slowMobs(Planted wall) {
    double reach = (wall.width / 2D) + 1.5D;
    Vec3 at = wall.center.position().add(0, 1.0D, 0);
    for (EntityRef nearby : wall.center.world().nearbyEntities(at, reach, 1.5D, reach)) {
      if (nearby instanceof LivingRef mob && !(nearby instanceof PlayerRef)
          && (mob.has(EntityTrait.MONSTER) || mob.has(EntityTrait.ENEMY))) {
        mob.addEffect(EffectInstance.of(SLOWNESS, TICK_INTERVAL + 10, getConfig().slowAmplifier).withParticles(false));
      }
    }
  }

  /** Takes the player's wall down, if any, and gives the shield back. Safe to call any number of times. */
  private void pickUp(PlayerRef p) {
    Planted wall = planted.remove(p.uuid());
    if (wall != null) {
      takeDown(wall);
    }
    returnShield(p);
  }

  private void takeDown(Planted wall) {
    LingeringSound sound = lingering.remove(wall.owner.uuid());
    if (sound != null) {
      sound.stop();
    }
    for (BlockRef block : wall.blocks) {
      takenDown.put(Spot.of(block), wall.timerEndsAt);
      AdaptApi.scheduler().runAt(block.location(), () -> wallBlocks.remove(block, false));
    }
    fx(wall.center.add(0, 1.0D, 0), FxPriority.GAMEPLAY)
        .burst(Particles.CRIT, 8, 0.6D)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.7F, 1.2F);
  }

  private void returnShield(PlayerRef p) {
    if (!p.isOnline() || p.isDead() || planted.containsKey(p.uuid())) {
      return;
    }
    DataHolder data = p.data();
    byte[] encoded = data.get(PLANTED_SHIELD);
    if (encoded == null) {
      return;
    }
    ItemRef shield;
    try {
      shield = items().deserialize(encoded);
    } catch (Throwable error) {
      AdaptApi.log().error("Failed to read the planted shield of " + p.name() + ".", null);
      AdaptApi.log().error("LivingWall failed", error);
      return;
    }

    data.remove(PLANTED_SHIELD);
    PlayerInventoryRef inventory = p.inventory();
    if (inventory.offHand().isEmpty()) {
      inventory.setOffHand(shield);
    } else if (inventory.mainHand().isEmpty()) {
      inventory.setMainHand(shield);
    } else {
      for (ItemRef overflow : inventory.addItem(shield)) {
        p.world().dropItemNaturally(p.location().position(), overflow);
      }
    }
  }

  /** Whether {@code owner} has a wall planted now. */
  public boolean hasPlanted(UUID owner) {
    return planted.containsKey(owner);
  }

  /**
   * Whether {@code at} stands behind {@code owner}'s planted wall, at most {@code depth} blocks back; with a shot's
   * velocity, only when the shot comes at the wall's face.
   */
  public boolean shelters(UUID owner, Loc at, double depth, @Nullable Vec3 incoming) {
    Planted wall = planted.get(owner);
    return wall != null && (incoming == null || incoming.withY(0).dot(wall.facing) < 0D) && wall.shelters(at, depth);
  }

  private boolean covers(PlayerRef owner, PlayerRef victim, EntityRef attacker) {
    if (attacker != null && attacker.uuid().equals(owner.uuid())) {
      return false;
    }
    if (owner.uuid().equals(victim.uuid())) {
      return true;
    }
    return preference(owner, BlockingPreferences.ALLIES).accepts(owner, victim)
        && (owner.isOnTeamWith(victim) || !canPVP(owner, victim.location()));
  }

  private void credit(PlayerRef p, EntityRef damager, double shielded) {
    if (!p.isOnline()) {
      return;
    }
    xpFromAttacker(p, damager, shielded * getConfig().xpPerDamageShielded);
    addStat(p, "blocking.living-wall.damage-shielded", shielded);
    cue(p, MutationCue.FIGHT);
  }

  private void shieldFx(PlayerRef victim) {
    fx(victim.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT, 4, 0.2D)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.5F, 1.2F);
  }

  private static boolean affords(ItemRef shield, int cost) {
    return shield.isUnbreakable() || shield.maxDamage() - shield.damage() > cost;
  }

  private static Vec3 facing(Vec3 direction) {
    if (Math.abs(direction.x()) >= Math.abs(direction.z())) {
      return new Vec3(direction.x() >= 0 ? 1 : -1, 0, 0);
    }
    return new Vec3(0, 0, direction.z() >= 0 ? 1 : -1);
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }

  private int wearCost(double shielded) {
    return Math.max(1, (int) Math.ceil(shielded * getConfig().durabilityPerDamage));
  }

  double getDamageReduction(int level) {
    return Math.min(getConfig().maxDamageReduction, getConfig().damageReductionBase + (level * getConfig().damageReductionPerLevel));
  }

  double getRange(int level) {
    return getConfig().rangeBase + (level * getConfig().rangePerLevel);
  }

  int getWidth(int level) {
    return level >= 3 ? getConfig().widthAtTopLevel : getConfig().widthBelowTopLevel;
  }

  int getDurationTicks(int level) {
    return (int) Math.max(20L, (getConfig().durationMillisBase + (level * getConfig().durationMillisPerLevel)) / 50L);
  }

  long getCooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  private static final class Stance {
    private final Loc start;
    private volatile boolean armed;

    private Stance(Loc start) {
      this.start = start;
    }

    private boolean still(Loc at) {
      return at != null && at.sameWorld(start)
          && at.position().subtract(start.position()).withY(0).lengthSquared() <= STILL_TOLERANCE_SQUARED;
    }
  }

  private static final class Planted {
    private final PlayerRef owner;
    private final Loc center;
    private final Vec3 facing;
    private final Vec3 lateral;
    private final int width;
    private final List<BlockRef> blocks;
    private final Set<Spot> spots = ConcurrentHashMap.newKeySet();
    private final int level;
    private final long timerEndsAt;
    private volatile int ticksLeft;

    private Planted(PlayerRef owner, Loc center, Vec3 facing, Vec3 lateral, int width, List<BlockRef> blocks, int level,
                    int ticksLeft, long timerEndsAt) {
      this.owner = owner;
      this.center = center;
      this.facing = facing;
      this.lateral = lateral;
      this.width = width;
      this.blocks = List.copyOf(blocks);
      this.level = level;
      this.ticksLeft = ticksLeft;
      this.timerEndsAt = timerEndsAt;
      blocks.forEach((BlockRef block) -> spots.add(Spot.of(block)));
    }

    private boolean contains(BlockRef block) {
      return spots.contains(Spot.of(block));
    }

    /** Behind the wall, within {@code depth} blocks of it and no farther to the side than its ends plus one. */
    private boolean shelters(Loc at, double depth) {
      if (!at.sameWorld(center)) {
        return false;
      }
      Vec3 offset = at.position().subtract(center.position()).withY(0);
      double behind = -offset.dot(facing);
      return behind > 0.5D && behind <= depth && Math.abs(offset.dot(lateral)) <= (width / 2D) + 1D
          && Math.abs(at.y() - center.y()) <= 3D;
    }
  }

  private record Spot(WorldRef world, BlockPos pos) {
    private static Spot of(BlockRef block) {
      return new Spot(block.world(), new BlockPos(block.x(), block.y(), block.z()));
    }
  }

  @ConfigDescription("Sneak still with your shield raised, then let go, to plant it as a wall that shelters everyone behind it.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Damage reduction behind the wall and while blocking, before level scaling.", impact = "Higher values protect more.")
    double damageReductionBase = 0.2;
    @ConfigDoc(value = "Damage reduction added per level.", impact = "Higher values reward higher levels more.")
    double damageReductionPerLevel = 0.1;
    @ConfigDoc(value = "Highest damage reduction at any level.", impact = "Caps the reduction.")
    double maxDamageReduction = 0.6;
    @ConfigDoc(value = "Depth of the sheltered area behind the wall before level scaling, in blocks.", impact = "Higher values shelter players farther behind the wall.")
    double rangeBase = 3.0;
    @ConfigDoc(value = "Sheltered depth added per level, in blocks.", impact = "Higher values deepen the shelter at higher levels.")
    double rangePerLevel = 1.0;
    @ConfigDoc(value = "Wall width in blocks at levels 1 and 2.", impact = "Higher values cover more ground.")
    int widthBelowTopLevel = 3;
    @ConfigDoc(value = "Wall width in blocks at level 3.", impact = "Higher values cover more ground at the top level.")
    int widthAtTopLevel = 5;
    @ConfigDoc(value = "How long the wall stands before level scaling, in milliseconds.", impact = "Higher values keep the wall up longer.")
    long durationMillisBase = 5000;
    @ConfigDoc(value = "Milliseconds the wall stands added per level.", impact = "Higher values reward higher levels more.")
    long durationMillisPerLevel = 5000;
    @ConfigDoc(value = "Cooldown between plants before level scaling, in milliseconds.", impact = "Lower values let you plant more often.")
    long cooldownMillisBase = 50000;
    @ConfigDoc(value = "Milliseconds of cooldown removed per level.", impact = "Higher values shorten the cooldown at higher levels.")
    long cooldownMillisPerLevel = 10000;
    @ConfigDoc(value = "Ticks you must sneak still with the shield raised before letting go plants it.", impact = "Lower values plant faster.")
    int stillTicks = 20;
    @ConfigDoc(value = "Shield durability each plant costs.", impact = "Higher values wear your shield faster.")
    int plantDurability = 20;
    @ConfigDoc(value = "Distance from the wall in blocks at which it comes down and the shield returns.", impact = "Higher values let you stray farther from the wall.")
    double returnDistance = 24;
    @ConfigDoc(value = "Slowness amplifier given to hostile mobs against the wall.", impact = "Higher values slow them more.")
    int slowAmplifier = 1;
    @ConfigDoc(value = "Shield durability worn per point of damage the raised shield takes off you.", impact = "Higher values wear your shield down faster.")
    double durabilityPerDamage = 1.0;
    @ConfigDoc(value = "Skill XP per point of damage taken off you or an ally from a natural mob or a player.", impact = "Higher values reward holding the line.")
    double xpPerDamageShielded = 3.0;
  }
}
