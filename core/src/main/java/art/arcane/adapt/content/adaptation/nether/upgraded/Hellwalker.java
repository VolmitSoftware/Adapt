package art.arcane.adapt.content.adaptation.nether.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.nether.NetherAshGuard;
import art.arcane.adapt.content.adaptation.nether.NetherAshwalker;
import art.arcane.adapt.content.adaptation.nether.NetherFireFizzle;
import art.arcane.adapt.content.adaptation.nether.NetherLavaStride;
import art.arcane.adapt.content.adaptation.nether.NetherLavaSwim;
import art.arcane.adapt.content.adaptation.nether.NetherSoulStride;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lava Walker, Fire Resistance, Ashwalker and Soul Strider fused: strides carry you over Nether lava, fire, magma and
 * campfire burns are ignored outright, and soul sand and soul soil no longer slow you. In any lava you see clearly and
 * swim at a share of water speed for hunger over time, and from level 2 items you drop near lava float for a while.
 */
public class Hellwalker extends SkillUpgrade<Hellwalker.Config> {
  private static final long CUE_GAP_MILLIS = 3000L;
  private static final long SWIM_PUSH_GAP_MILLIS = 100L;
  private static final int FLOAT_LAVA_RADIUS = 2;
  private final NetherFireFizzle fizzle = new NetherFireFizzle(this, cooldowns());
  private final Cooldowns strideCooldowns = cooldowns();
  private final Cooldowns cueCooldowns = cooldowns();
  private final Cooldowns swimPushes = cooldowns();
  private final Map<UUID, Long> lavaMillis = playerState();
  private final Map<UUID, Boolean> sighted = playerState();
  private final Set<UUID> floating = ConcurrentHashMap.newKeySet();
  private Boolean soulSupported;

  public Hellwalker() {
    super("upgraded-nether-hellwalker");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("magma_block"));
    setInterval(1000);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    NetherSoulStride.release(this, player.getPlayer());
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
    leaveLava(player.getPlayer());
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> soak(player, getInterval()));
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getStride(level), 0), 1);
    statLore(v, Form.pc(getSoulSpeedBonus(level), 0), 2);
    statLore(v, C.YELLOW, "* ", getConfig().hungerPerStride, 3);
    statLore(v, Form.pc(getSwimShare(level), 0), 4);
    statLore(v, C.YELLOW, "* ", Form.duration(getConfig().lavaMillisPerHunger, 0), 5);
    if (level >= getConfig().itemFloatLevel) {
      statLore(v, Form.duration(getConfig().itemFloatTicks * 50D, 0), 6);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    withActiveLevel(p, e, (int level) -> {
      Loc to = e.to();
      if (to != null && soulSupported()) {
        soulStride(p, e.from(), to, level);
      }
      if (swimming(p) && to != null) {
        swim(p, e.from(), to, level);
      } else if (p.world().environment() == Environment.NETHER && NetherLavaStride.canStride(p) && NetherLavaStride.onLava(p)) {
        lavaStride(p, level);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    withActiveLevel(p, (int level) -> {
      if (level >= getConfig().itemFloatLevel && NetherLavaSwim.lavaNear(p, FLOAT_LAVA_RADIUS)) {
        UUID item = e.itemDrop().uuid();
        floating.add(item);
        NetherLavaSwim.buoy(e.itemDrop(), getConfig().itemFloatTicks, () -> floating.remove(item));
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      if (e.entity() instanceof ItemEntityRef && floating.contains(e.entity().uuid())) {
        e.setCancelled(true);
      }
      return;
    }

    withActiveLevel(p, e, (int level) -> {
      if (NetherAshwalker.isMagmaDamage(e)) {
        NetherAshGuard.negate(this, p, e, true, 0F);
        heatCue(p);
      } else if (NetherAshwalker.isCampfireDamage(e.cause())) {
        NetherAshGuard.negate(this, p, e, false, 0F);
        heatCue(p);
      } else if (e.cause() == DamageCause.LAVA && p.foodLevel() > 0) {
        e.setCancelled(true);
        soak(p, 0L);
      } else if (NetherFireFizzle.isBurn(e.cause())) {
        e.setCancelled(true);
        fizzle.play(p);
        heatCue(p);
      }
    });
  }

  private void soulStride(PlayerRef p, Loc from, Loc to, int level) {
    if (p.isFlying() || p.isGliding() || p.vehicle() != null) {
      return;
    }
    if (!NetherSoulStride.onSoul(to)) {
      NetherSoulStride.release(this, p);
      return;
    }

    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    if (Math.sqrt((dx * dx) + (dz * dz)) < 0.02D
        || !NetherSoulStride.refresh(this, p, true, getSoulSpeedBonus(level), System.currentTimeMillis())) {
      return;
    }
    cue(p, MutationCue.BOON);
  }

  private void lavaStride(PlayerRef p, int level) {
    long cooldownMillis = getStrideCooldownMillis(level);
    if (!strideCooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }
    strideCooldowns.mark(p.uuid());
    NetherLavaStride.stride(this, p, getStride(level), getConfig().hungerPerStride, getConfig().fireResistTicks);
    heatCue(p);
  }

  /** Shields a player in lava, charging hunger for every {@code lavaMillisPerHunger} spent there; {@code millis} is the time to add. */
  private void soak(PlayerRef p, long millis) {
    if (!p.isOnline() || getActiveLevel(p) <= 0 || !NetherLavaSwim.inLava(p)) {
      leaveLava(p);
      return;
    }
    if (p.foodLevel() <= 0) {
      return;
    }
    boolean entering = !lavaMillis.containsKey(p.uuid());
    long spent = lavaMillis.getOrDefault(p.uuid(), 0L) + millis;
    if (spent >= getConfig().lavaMillisPerHunger) {
      spent -= getConfig().lavaMillisPerHunger;
      p.setFoodLevel(Math.max(0, p.foodLevel() - 1));
    }
    lavaMillis.put(p.uuid(), spent);
    p.setFireTicks(0);
    if (NetherLavaSwim.shield(p)) {
      sighted.put(p.uuid(), Boolean.TRUE);
    }
    if (entering) {
      heatCue(p);
    }
  }

  private void leaveLava(PlayerRef p) {
    if (lavaMillis.remove(p.uuid()) != null && sighted.remove(p.uuid()) != null) {
      NetherLavaSwim.clearSight(p);
    }
  }

  private boolean swimming(PlayerRef p) {
    if (p.isFlying() || p.vehicle() != null || p.foodLevel() <= 0 || !NetherLavaSwim.inLava(p)) {
      return false;
    }
    return p.world().environment() != Environment.NETHER || NetherLavaSwim.eyesInLava(p);
  }

  private void swim(PlayerRef p, Loc from, Loc to, int level) {
    if (!swimPushes.isReady(p.uuid(), SWIM_PUSH_GAP_MILLIS)) {
      return;
    }
    Vec3 push = NetherLavaSwim.swimVelocity(from, to, p.velocity(), getConfig().waterSwimSpeed * getSwimShare(level));
    if (push != null) {
      swimPushes.mark(p.uuid());
      p.setVelocity(push);
      p.setFallDistance(0);
    }
  }

  private void heatCue(PlayerRef p) {
    if (cueCooldowns.isReady(p.uuid(), CUE_GAP_MILLIS)) {
      cueCooldowns.mark(p.uuid());
      cue(p, MutationCue.BOON);
    }
  }

  private boolean soulSupported() {
    if (soulSupported == null) {
      soulSupported = PlatformSupport.check(List.of(NetherSoulStride.NEED)).usable();
    }
    return soulSupported;
  }

  double getStride(int level) {
    return getConfig().strideBase + (level * getConfig().stridePerLevel);
  }

  double getSoulSpeedBonus(int level) {
    return level * getConfig().soulSpeedBonusPerLevel;
  }

  double getSwimShare(int level) {
    return Math.min(1D, getConfig().swimShareBase + (level * getConfig().swimSharePerLevel));
  }

  long getStrideCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumStrideCooldownMillis,
        getConfig().strideCooldownMillisBase - (level * getConfig().strideCooldownMillisPerLevel));
  }

  @ConfigDescription("Stride over Nether lava, ignore fire, magma and campfires, and cross soul sand at full speed.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Lava stride push before level scaling.", impact = "Higher values carry you further across lava with each stride.")
    double strideBase = 0.45;
    @ConfigDoc(value = "Lava stride push added per level.", impact = "Higher values reward higher levels more.")
    double stridePerLevel = 0.15;
    @ConfigDoc(value = "Time between two lava strides before level scaling, in milliseconds.", impact = "Lower values keep you moving more smoothly.")
    double strideCooldownMillisBase = 500;
    @ConfigDoc(value = "Time between strides removed per level, in milliseconds.", impact = "Higher values make higher levels stride more often.")
    double strideCooldownMillisPerLevel = 100;
    @ConfigDoc(value = "Shortest time between two lava strides at any level, in milliseconds.", impact = "Lower values allow faster lava crossings.")
    double minimumStrideCooldownMillis = 150;
    @ConfigDoc(value = "Hunger points each lava stride costs.", impact = "Higher values make lava crossings drain food faster.")
    int hungerPerStride = 1;
    @ConfigDoc(value = "Fire resistance granted by each lava stride, in ticks.", impact = "Higher values keep you safe longer after leaving the lava.")
    int fireResistTicks = 100;
    @ConfigDoc(value = "Movement speed bonus on soul sand and soul soil per level, as a fraction.", impact = "Higher values make soul ground faster than normal ground.")
    double soulSpeedBonusPerLevel = 0.10;
    @ConfigDoc(value = "Water swim speed that lava swimming is measured against, in blocks per tick.", impact = "Higher values make lava swimming faster at every level.")
    double waterSwimSpeed = 0.2;
    @ConfigDoc(value = "Share of water swim speed in lava before level scaling.", impact = "Higher values swim faster through lava.")
    double swimShareBase = 0.4;
    @ConfigDoc(value = "Share of water swim speed added per level.", impact = "Higher values reward higher levels more.")
    double swimSharePerLevel = 0.2;
    @ConfigDoc(value = "Time in lava that costs one hunger point, in milliseconds.", impact = "Lower values make lava swims drain food faster.")
    long lavaMillisPerHunger = 10000;
    @ConfigDoc(value = "Level from which items you drop near lava float instead of burning.", impact = "Higher values unlock floating items later.")
    int itemFloatLevel = 2;
    @ConfigDoc(value = "How long a dropped item floats in lava before it can burn, in ticks.", impact = "Higher values give more time to pick items back up.")
    int itemFloatTicks = 100;
  }
}
