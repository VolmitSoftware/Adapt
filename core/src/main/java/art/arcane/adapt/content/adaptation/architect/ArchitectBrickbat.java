package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AbilityDamage;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.axe.AxeThrow;
import art.arcane.adapt.content.adaptation.axe.BlockFling;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Punching a creature, or swinging at one in reach, while holding a placeable block throws one block from the stack at
 * it. The block hits by its weight and drops where it lands; glass and ice shatter as they would when broken by hand.
 */
public class ArchitectBrickbat extends SimpleAdaptation<ArchitectBrickbat.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> AIR_THROWS = CommonPreferences.toggle("air-throws",
      ArchitectMessages.ARCHITECTBRICKBAT_PREFERENCE_AIR_THROWS, CommonPreferences.Toggle.ON);
  private static final Set<String> HELD_SHAPES = Set.of("_door", "_bed", "_banner", "_sign", "_button", "_pressure_plate",
      "_carpet", "_sapling", "_torch", "_rail", "_head", "_skull");
  private static final Set<String> HELD_KINDS = Set.of("tnt", "scaffolding", "ladder", "cobweb", "lever", "redstone_wire",
      "tripwire", "snow", "sugar_cane", "bamboo", "cactus", "dragon_egg", "turtle_egg", "sniffer_egg", "frogspawn",
      "powder_snow", "pointed_dripstone", "big_dripleaf", "small_dripleaf");
  private static final Set<String> SHATTERING = Set.of("ice", "packed_ice", "blue_ice", "glass", "glass_pane");

  private final Cooldowns cooldowns = cooldowns();
  private final AxeThrow throwing = new AxeThrow(this, cooldowns, new AxeThrow.Rules() {
    @Override
    public AxeThrow.Toss toss(int level, Id axe) {
      return new AxeThrow.Toss(0L, 0D, 0D, false, 0, 0);
    }

    @Override
    public boolean ignorePassiveMobs() {
      return false;
    }

    @Override
    public boolean strikes(UUID ownerId, LivingRef target) {
      return target.tameOwner() == null;
    }

    @Override
    public void thrown(PlayerRef player) {
    }

    @Override
    public void hit(PlayerRef owner, LivingRef target) {
      addStat(owner, "architect.brickbat.hits", 1);
      if (!(target instanceof PlayerRef) && MobOrigin.paysKillReward(owner, target)) {
        xp(owner, target.location(), getConfig().xpPerHit, "architect:brickbat");
      }
    }

    @Override
    public void launchFx(Loc spawn, Vec3 direction, ProjectileRef ball, int flightTicks) {
    }

    @Override
    public void flingFx(Loc spawn, ProjectileRef ball, BlockStateRef block, int flightTicks) {
      BlockFling.launchFx(ArchitectBrickbat.this, spawn, ball, block, flightTicks);
    }

    @Override
    public void impactFx(Loc impact, BlockStateRef block) {
      BlockFling.impactFx(ArchitectBrickbat.this, impact, block);
    }

    @Override
    public void breakFx(Loc impact) {
    }

    @Override
    public void hitFx(LivingRef target) {
      BlockFling.hitFx(ArchitectBrickbat.this, target);
    }

    @Override
    public void struck(PlayerRef owner, LivingRef target, ItemRef thrown) {
      BlockFling.hitFx(ArchitectBrickbat.this, target);
      double push = knockback(heft(thrown.type()), getConfig());
      if (push <= 0D) {
        return;
      }
      Vec3 away = target.location().position().subtract(owner.location().position()).withY(0D);
      if (away.lengthSquared() < 1.0E-4D) {
        return;
      }
      target.setVelocity(target.velocity().add(away.normalize().multiply(push)).add(0D, push * 0.35D, 0D));
      Fx.now(ArchitectBrickbat.this, target.location(), FxPriority.COMBAT)
          .burst(Particles.blockCrack(items().blockState(thrown.type())), 6, 0.4D);
    }

    @Override
    public void returnFx(PlayerRef owner) {
    }
  });

  public ArchitectBrickbat() {
    super("architect-brickbat");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bricks"));
    setInterval(4470);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bricks"))
        .key("challenge_architect_brickbat_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_architect_brickbat_500", "architect.brickbat.hits", 500, 600);
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
    return List.of(CommonPreferences.ENABLED, AIR_THROWS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "x ", Form.f(damageMultiplier(f, getConfig()), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(range(f), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(cooldownMillis(heft(Id.minecraft("stone")), f, getConfig()), 1), 3);
  }

  @Handle(priority = EventPriority.LOW)
  public void on(EntityDamageByEntityEvent e) {
    if (e.cause() != DamageCause.ENTITY_ATTACK || AbilityDamage.dealing() || !(e.damager() instanceof PlayerRef p)
        || !(e.entity() instanceof LivingRef target)) {
      return;
    }
    if (hurl(p, target)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamageSeen(EntityDamageByEntityEvent e) {
    throwing.entityDamaged(e);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.LEFT_CLICK_AIR || (e.hand() != null && e.hand() != Hand.MAIN_HAND)) {
      return;
    }
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, AIR_THROWS) || throwing.consumeBreakSwing(p)) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    if (!throwable(hand)) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    Loc eye = p.eyeLocation();
    RayHit hit = p.world().rayTrace(eye.position(), eye.direction().normalize(), range(getLevelPercent(level)), false,
        0.3D, (EntityRef candidate) -> candidate instanceof LivingRef && !candidate.uuid().equals(p.uuid()));
    if (hit != null && hit.entity() instanceof LivingRef target) {
      hurl(p, target);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    throwing.blockBroken(e.player());
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
    throwing.quit(event.player());
  }

  /** Throws one block from the main hand at {@code target}; true when it flew. */
  private boolean hurl(PlayerRef p, LivingRef target) {
    ItemRef hand = p.inventory().mainHand();
    if (!throwable(hand) || target.uuid().equals(p.uuid()) || target.tameOwner() != null
        || !target.world().equals(p.world())) {
      return false;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return false;
    }
    double f = getLevelPercent(level);
    Id type = hand.type();
    double heft = heft(type);
    long cooldown = cooldownMillis(heft, f, getConfig());
    Loc eye = p.eyeLocation();
    double distance = eye.position().distance(BlockFling.aimPoint(target, 0));
    if (distance > range(f) + 1D || !cooldowns.isReady(p.uuid(), cooldown) || !p.hasLineOfSight(target)
        || !canDamageTarget(p, target)) {
      return false;
    }
    ItemOps items = items();
    BlockStateRef block = items.blockState(type);
    ItemRef carried = hand.copyWithAmount(1);
    boolean[] taken = new boolean[1];
    if (!payItemCost(p, "throw", items.create(type), 1, () -> {
      ItemRef held = p.inventory().mainHand();
      if (held.amount() > 1) {
        held.setAmount(held.amount() - 1);
        p.inventory().setMainHand(held);
      } else {
        p.inventory().setMainHand(items.empty());
      }
      taken[0] = true;
      return true;
    })) {
      return false;
    }
    Loc spawn = eye.add(eye.direction().normalize().multiply(0.6D));
    int ticks = BlockFling.flightTicks(distance, getConfig().throwSpeed);
    Vec3 velocity = BlockFling.launch(spawn.position(), BlockFling.aimPoint(target, ticks), ticks);
    boolean keeps = taken[0] && !shatters(type);
    AxeThrow.Fling fling = new AxeThrow.Fling(carried, block, carried, velocity, damage(heft, f, getConfig()), false,
        keeps, ticks + getConfig().flightGraceTicks);
    if (!throwing.fling(p, spawn, fling, getConfig().maxInFlight)) {
      if (taken[0]) {
        giveBack(p, carried);
      }
      return false;
    }
    cooldowns.mark(p.uuid(), cooldown);
    addStat(p, "architect.brickbat.thrown", 1);
    return true;
  }

  private static void giveBack(PlayerRef p, ItemRef item) {
    for (ItemRef leftover : p.inventory().addItem(item)) {
      p.world().dropItem(p.location().position(), leftover);
    }
  }

  /** A plain placeable block: no contents, no special state, breakable, and not a thin or living thing. */
  static boolean throwable(ItemRef hand) {
    if (hand == null || hand.isEmpty() || !hand.type().isMinecraft() || hand.maxDamage() > 0) {
      return false;
    }
    Id type = hand.type();
    ItemOps items = items();
    if (!items.isBlock(type) || items.hasBlockEntity(type) || items.blockHardness(type) < 0F
        || HELD_KINDS.contains(type.path())) {
      return false;
    }
    for (String shape : HELD_SHAPES) {
      if (type.path().endsWith(shape)) {
        return false;
      }
    }
    BlockStateRef state = items.blockState(type);
    return state != null && !state.isAir();
  }

  /** Glass and ice break into nothing, as they do when broken without Silk Touch. */
  static boolean shatters(Id type) {
    String path = type.path();
    return SHATTERING.contains(path) || ((path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane"))
        && !path.startsWith("tinted"));
  }

  /**
   * How hard a block hits: the square root of its hardness (capped at 50) plus the base-ten log of its blast
   * resistance, so obsidian, ancient debris and anvils hit hard while wool, leaves and glass barely sting.
   */
  static double heft(Id type) {
    ItemOps items = items();
    return heft(items.blockHardness(type), items.blastResistance(type));
  }

  static double heft(double hardness, double blastResistance) {
    return Math.sqrt(Math.max(0D, Math.min(50D, hardness))) + Math.log10(1D + Math.max(0D, blastResistance));
  }

  static double damageMultiplier(double levelPercent, Config config) {
    return config.damageMultiplierBase + (levelPercent * config.damageMultiplierFactor);
  }

  static double damage(double heft, double levelPercent, Config config) {
    return Math.max(config.minDamage, Math.min(config.maxDamage, heft * damageMultiplier(levelPercent, config)));
  }

  /** Heavier blocks take longer to wind up; levels shorten every wind-up by the same share. */
  static long cooldownMillis(double heft, double levelPercent, Config config) {
    double wound = config.cooldownMillisBase + (heft * config.cooldownMillisPerHeft);
    return (long) (wound * (1D - (levelPercent * config.cooldownLevelReduction)));
  }

  static double knockback(double heft, Config config) {
    return heft < config.knockbackHeft ? 0D : Math.min(config.maxKnockback, heft * config.knockbackPerHeft);
  }

  double range(double levelPercent) {
    return getConfig().rangeBase + (levelPercent * getConfig().rangeFactor);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  @ConfigDescription("Punch or swing at a creature with a block in hand to throw one block at it; heavier blocks hit harder.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Throw range in blocks at level 0.", impact = "Higher values let throws reach farther targets.")
    double rangeBase = 4;
    @ConfigDoc(value = "Range in blocks added at maximum level.", impact = "Higher values grow the range faster with level.")
    double rangeFactor = 10;
    @ConfigDoc(value = "Damage per point of block heft at level 0.", impact = "Higher values make every block hit harder.")
    double damageMultiplierBase = 0.9;
    @ConfigDoc(value = "Damage per point of heft added at maximum level.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @ConfigDoc(value = "Least damage any thrown block deals.", impact = "Higher values make soft blocks sting more.")
    double minDamage = 0.5;
    @ConfigDoc(value = "Most damage one thrown block deals.", impact = "Lower values cap obsidian and netherite throws sooner.")
    double maxDamage = 12;
    @ConfigDoc(value = "Wind-up in milliseconds for a weightless block at level 0.", impact = "Higher values slow every throw.")
    double cooldownMillisBase = 700;
    @ConfigDoc(value = "Wind-up in milliseconds added per point of block heft.", impact = "Higher values slow heavy blocks more than light ones.")
    double cooldownMillisPerHeft = 260;
    @ConfigDoc(value = "Share of the wind-up removed at maximum level.", impact = "Higher values let high levels throw more often.")
    double cooldownLevelReduction = 0.4;
    @ConfigDoc(value = "Heft at which a thrown block starts to knock its target back.", impact = "Lower values let lighter blocks shove.")
    double knockbackHeft = 3;
    @ConfigDoc(value = "Knockback speed per point of heft.", impact = "Higher values shove targets farther.")
    double knockbackPerHeft = 0.06;
    @ConfigDoc(value = "Most knockback speed a thrown block adds.", impact = "Lower values keep heavy throws from flinging targets.")
    double maxKnockback = 0.6;
    @ConfigDoc(value = "Blocks per tick a thrown block travels, before its arc.", impact = "Higher values give flatter, faster throws.")
    double throwSpeed = 1.2;
    @ConfigDoc(value = "Ticks a thrown block may fly past its planned arc before it drops on its own.", impact = "Higher values let missed throws fall farther.")
    int flightGraceTicks = 20;
    @ConfigDoc(value = "Most thrown blocks in the air on the server at once.", impact = "Lower values cap the server cost of many builders fighting at once.")
    int maxInFlight = 64;
    @ConfigDoc(value = "XP for each throw that hits a natural mob.", impact = "Higher values speed up Architect from fighting with thrown blocks.")
    double xpPerHit = 3;

    public Config() {
      baseCost = 3;
      initialCost = 2;
      costFactor = 0.6;
      maxLevel = 5;
    }
  }
}
