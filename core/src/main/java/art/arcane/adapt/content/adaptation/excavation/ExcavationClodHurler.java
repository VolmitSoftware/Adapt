package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxeThrow;
import art.arcane.adapt.content.adaptation.axe.BlockFling;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Digging soft ground with a shovel while a hostile mob is in sight flings the dug block at the nearest one. The block's
 * drop rides the throw and comes back into the digger's inventory; with nobody in range the dig is an ordinary dig.
 */
public class ExcavationClodHurler extends SimpleAdaptation<ExcavationClodHurler.Config> {
  public static final PlayerPreference<ExcavationPreferences.Trigger> TRIGGER = ExcavationPreferences.trigger("trigger",
      ExcavationMessages.PREFERENCE_EXCAVATIONCLODHURLER_TRIGGER, ExcavationPreferences.Trigger.ALWAYS);
  private static final Id ENCHANT_SHARPNESS = Id.minecraft("sharpness");
  private static final Map<String, Double> HEFT = Map.ofEntries(
      Map.entry("snow", 0.4D), Map.entry("snow_block", 0.5D),
      Map.entry("sand", 0.7D), Map.entry("red_sand", 0.7D),
      Map.entry("dirt", 0.8D), Map.entry("farmland", 0.8D), Map.entry("dirt_path", 0.8D),
      Map.entry("coarse_dirt", 0.85D), Map.entry("rooted_dirt", 0.85D), Map.entry("grass_block", 0.85D),
      Map.entry("mycelium", 0.85D), Map.entry("podzol", 0.85D),
      Map.entry("soul_sand", 0.9D), Map.entry("soul_soil", 0.9D),
      Map.entry("mud", 1.0D), Map.entry("muddy_mangrove_roots", 1.0D),
      Map.entry("gravel", 1.15D), Map.entry("clay", 1.25D));

  private final Map<UUID, Aim> aims = playerState();
  private final Cooldowns cooldowns = cooldowns();
  private final AxeThrow throwing = new AxeThrow(this, cooldowns, new AxeThrow.Rules() {
    @Override
    public AxeThrow.Toss toss(int level, Id axe) {
      return new AxeThrow.Toss(0L, 0D, 0D, true, 0, 0);
    }

    @Override
    public boolean ignorePassiveMobs() {
      return true;
    }

    @Override
    public boolean strikes(UUID ownerId, LivingRef target) {
      return isQuarry(target);
    }

    @Override
    public void thrown(PlayerRef player) {
    }

    @Override
    public void hit(PlayerRef owner, LivingRef target) {
      addStat(owner, "excavation.clod-hurler.hits", 1);
      if (MobOrigin.paysKillReward(owner, target)) {
        xp(owner, target.location(), getConfig().xpPerHit, "excavation:clod-hurler");
      }
    }

    @Override
    public void launchFx(Loc spawn, Vec3 direction, ProjectileRef ball, int flightTicks) {
    }

    @Override
    public void flingFx(Loc spawn, ProjectileRef ball, BlockStateRef block, int flightTicks) {
      BlockFling.launchFx(ExcavationClodHurler.this, spawn, ball, block, flightTicks);
    }

    @Override
    public void impactFx(Loc impact, BlockStateRef block) {
      BlockFling.impactFx(ExcavationClodHurler.this, impact, block);
    }

    @Override
    public void breakFx(Loc impact) {
    }

    @Override
    public void hitFx(LivingRef target) {
      BlockFling.hitFx(ExcavationClodHurler.this, target);
    }

    @Override
    public int returnTicks() {
      return getConfig().returnTicks;
    }

    @Override
    public void returnFlight(PlayerRef owner, Loc impact, ItemRef item, int ticks) {
      BlockFling.returnFlight(ExcavationClodHurler.this, owner, impact, item, ticks);
    }

    @Override
    public void returnFx(PlayerRef owner) {
      BlockFling.returnFx(ExcavationClodHurler.this, owner);
    }
  });

  /** A dig that found a target, waiting for the block's drops. */
  private record Aim(BlockRef block, BlockStateRef state, LivingRef target, double damage, long tick) {
  }

  public ExcavationClodHurler() {
    super("excavation-clod-hurler");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("coarse_dirt"));
    setInterval(4410);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("coarse_dirt"))
        .key("challenge_excavation_clod_hurler_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_clod_hurler_500", "excavation.clod-hurler.hits", 500, 600);
  }

  @Override
  protected void onRuntimeActivated() {
    throwing.activate();
  }

  @Override
  public void unregister() {
    throwing.close();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TRIGGER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "x ", Form.f(damageMultiplier(f), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(range(f), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(cooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    BlockRef block = e.block();
    if (BonusBlockBreaks.isActive(p) || !isShovel(p.inventory().mainHand()) || !ExcavationBlocks.isShovelable(block.type())
        || XpProvenance.isPlayerPlaced(block) || !preference(p, TRIGGER).allows(p.isSneaking())) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    double f = getLevelPercent(level);
    if (!cooldowns.isReady(p.uuid(), cooldownMillis(level))) {
      return;
    }
    Vec3 from = block.center().position();
    LivingRef target = nearestQuarry(p, from, range(f));
    if (target == null) {
      return;
    }
    ItemRef shovel = p.inventory().mainHand();
    double damage = damage(heldDamage(p, shovel), heft(block.type()),
        from.distance(BlockFling.aimPoint(target, 0)), f, getConfig());
    aims.put(p.uuid(), new Aim(block, block.state(), target, damage, p.ticksLived()));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    Aim aim = aims.remove(p.uuid());
    if (aim == null || aim.tick() != p.ticksLived() || !sameBlock(aim.block(), e.block()) || e.items().size() != 1) {
      return;
    }
    LivingRef target = aim.target();
    if (!target.isValid() || target.isDead() || !target.world().equals(p.world())) {
      return;
    }
    ItemEntityRef drop = e.items().get(0);
    ItemRef carried = drop.item().copy();
    if (carried.isEmpty()) {
      return;
    }
    Loc spawn = e.block().center().add(0D, 0.4D, 0D);
    double distance = spawn.position().distance(BlockFling.aimPoint(target, 0));
    int ticks = BlockFling.flightTicks(distance, getConfig().throwSpeed);
    Vec3 velocity = BlockFling.launch(spawn.position(), BlockFling.aimPoint(target, ticks), ticks);
    ItemRef look = lookOf(aim.state(), carried);
    AxeThrow.Fling fling = new AxeThrow.Fling(carried, aim.state(), look, velocity, aim.damage(), true, true,
        ticks + getConfig().flightGraceTicks);
    if (!throwing.fling(p, spawn, fling, getConfig().maxInFlight)) {
      return;
    }
    e.items().remove(drop);
    cooldowns.mark(p.uuid());
    addStat(p, "excavation.clod-hurler.thrown", 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    throwing.projectileHit(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    throwing.entityRemoved(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    throwing.entityDamaged(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    throwing.entityDied(e);
  }

  @Handle(ignoreCancelled = false)
  public void on(ChunkEntitiesEvent e) {
    if (e.phase() == ChunkEntitiesEvent.Phase.LOAD) {
      throwing.sweep(e.entities());
    }
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerJoinEvent event) {
    throwing.joined(event.player());
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    aims.remove(event.player().uuid());
    throwing.quit(event.player());
  }

  private LivingRef nearestQuarry(PlayerRef p, Vec3 from, double range) {
    Box box = new Box(from.x() - range, from.y() - range, from.z() - range, from.x() + range, from.y() + range,
        from.z() + range);
    LivingRef nearest = null;
    double best = range * range;
    for (EntityRef entity : p.world().entitiesIn(box, (EntityRef candidate) -> candidate instanceof LivingRef)) {
      LivingRef living = (LivingRef) entity;
      double distance = living.location().position().distanceSquared(from);
      if (distance <= best && isQuarry(living) && p.hasLineOfSight(living) && canDamageTarget(p, living)) {
        nearest = living;
        best = distance;
      }
    }
    return nearest;
  }

  /** A hostile mob nobody tamed: never a player, a pet, or a mob that only fights back. */
  static boolean isQuarry(LivingRef target) {
    return target.isValid() && !target.isDead() && !(target instanceof PlayerRef) && target.has(EntityTrait.ENEMY)
        && AdaptationDamageTargets.allows(target, true) && target.tameOwner() == null;
  }

  private static boolean sameBlock(BlockRef a, BlockRef b) {
    return a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.world().equals(b.world());
  }

  private static ItemRef lookOf(BlockStateRef state, ItemRef carried) {
    Id placed = state.placementItem();
    return placed == null || placed.path().equals("air") ? carried.copyWithAmount(1)
        : AdaptCore.platform().host().items().create(placed);
  }

  /**
   * Attack damage of the shovel in hand: the player's live attack damage, which carries the held item's own modifiers
   * and so covers shovels from other mods, plus Sharpness. Falls back to the vanilla table when the platform reports
   * no more than a bare hand.
   */
  static double heldDamage(PlayerRef p, ItemRef shovel) {
    int sharpness = shovel.enchantmentLevel(ENCHANT_SHARPNESS);
    double live = p.hasAttribute(Attributes.ATTACK_DAMAGE) ? p.attributeValue(Attributes.ATTACK_DAMAGE) : Double.NaN;
    if (Double.isNaN(live) || live <= 1D) {
      return shovelDamage(shovel.type(), sharpness);
    }
    return live + sharpnessBonus(sharpness);
  }

  /** Vanilla attack damage of a shovel type plus Java Sharpness ({@code 0.5 * level + 0.5}). */
  static double shovelDamage(Id shovel, int sharpness) {
    double base = switch (shovel.isMinecraft() ? shovel.path() : "") {
      case "netherite_shovel" -> 6.5D;
      case "diamond_shovel" -> 5.5D;
      case "iron_shovel" -> 4.5D;
      case "stone_shovel", "copper_shovel" -> 3.5D;
      default -> 2.5D;
    };
    return base + sharpnessBonus(sharpness);
  }

  private static double sharpnessBonus(int sharpness) {
    return sharpness > 0 ? (0.5D * sharpness) + 0.5D : 0D;
  }

  /** How hard a dug block hits: snow and sand soft, dirt middling, gravel and clay heavy. */
  static double heft(Id block) {
    return block.isMinecraft() ? HEFT.getOrDefault(block.path(), 0.8D) : 0.8D;
  }

  /** Damage rises from {@code closeFactor} at point blank to full at the sweet spot, then holds. */
  static double distanceFactor(double distance, Config config) {
    double reach = Math.max(0D, Math.min(1D, distance / Math.max(0.1D, config.sweetSpotBlocks)));
    return config.closeFactor + ((1D - config.closeFactor) * reach);
  }

  static double damage(double shovel, double heft, double distance, double levelPercent, Config config) {
    double raw = shovel * heft * distanceFactor(distance, config)
        * (config.damageMultiplierBase + (levelPercent * config.damageMultiplierFactor));
    return Math.min(config.maxDamage, raw);
  }

  double damageMultiplier(double levelPercent) {
    return getConfig().damageMultiplierBase + (levelPercent * getConfig().damageMultiplierFactor);
  }

  double range(double levelPercent) {
    return getConfig().rangeBase + (levelPercent * getConfig().rangeFactor);
  }

  long cooldownMillis(int level) {
    int top = Math.max(2, getConfig().maxLevel);
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (top - 1D)));
    double first = getConfig().cooldownMillisAtLevelOne;
    return (long) Math.max(0D, first + ((getConfig().cooldownMillisAtTopLevel - first) * progress));
  }

  @ConfigDescription("Digging soft ground with a shovel flings the dug block at the nearest hostile mob in sight; its drop flies back to you.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Throw range in blocks at level 1 before level scaling.", impact = "Higher values let digs reach farther mobs.")
    double rangeBase = 7;
    @ConfigDoc(value = "Range in blocks added at maximum level.", impact = "Higher values grow the range faster with level.")
    double rangeFactor = 25;
    @ConfigDoc(value = "Damage multiplier at level 0, applied to the held shovel's attack damage times block heft.", impact = "Higher values make every throw hit harder.")
    double damageMultiplierBase = 0.8;
    @ConfigDoc(value = "Damage multiplier added at maximum level.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @ConfigDoc(value = "Highest damage one throw can deal.", impact = "Lower values cap enchanted netherite shovels sooner.")
    double maxDamage = 14;
    @ConfigDoc(value = "Flight distance in blocks at which a throw reaches full damage.", impact = "Higher values ask for longer shots before damage peaks.")
    double sweetSpotBlocks = 8;
    @ConfigDoc(value = "Share of full damage a point-blank throw deals.", impact = "Higher values make close throws closer to full damage.")
    double closeFactor = 0.6;
    @ConfigDoc(value = "Milliseconds between throws at level 1.", impact = "Higher values slow how often digs can throw.")
    double cooldownMillisAtLevelOne = 2000;
    @ConfigDoc(value = "Milliseconds between throws at the top level; levels between are linear.", impact = "Lower values let high levels throw more often.")
    double cooldownMillisAtTopLevel = 500;
    @ConfigDoc(value = "Blocks per tick a flung block travels, before its arc.", impact = "Higher values give flatter, faster throws.")
    double throwSpeed = 1.1;
    @ConfigDoc(value = "Ticks a flung block may fly past its planned arc before it returns on its own.", impact = "Higher values let missed throws fall farther before they come back.")
    int flightGraceTicks = 20;
    @ConfigDoc(value = "Ticks a block's drop takes to fly back to you after landing.", impact = "Higher values show a slower return.")
    int returnTicks = 7;
    @ConfigDoc(value = "Most flung blocks in the air on the server at once.", impact = "Lower values cap the server cost of many diggers fighting at once.")
    int maxInFlight = 64;
    @ConfigDoc(value = "XP for each throw that hits a natural mob.", impact = "Higher values speed up Excavation from fighting with thrown blocks.")
    double xpPerHit = 4;

    public Config() {
      baseCost = 3;
      initialCost = 2;
      costFactor = 0.6;
      maxLevel = 5;
    }
  }
}
