package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.agility.AgilityDodge;
import art.arcane.adapt.content.adaptation.agility.AgilityKip;
import art.arcane.adapt.content.adaptation.agility.AgilityRoll;
import art.arcane.adapt.content.adaptation.rift.RiftPearlRebound;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cat Reflexes, Roll Landing and Kip-Up fused: a share of projectiles is sidestepped while sprinting, falls up to a
 * height that rises with level end in a roll that takes no damage, and every hit turns into a speed burst, with a jump
 * in the recovery window pushing the player on toward where they are heading. Prevented damage pays no skill XP.
 */
public class Untouchable extends SkillUpgrade<Untouchable.Config> {
  private static final double SAFE_FALL_BLOCKS = 3D;
  private final AgilityRoll roll = new AgilityRoll(this, playerState());
  private final Cooldowns struckAt = cooldowns();
  private final Map<UUID, Boolean> wasOnGround = playerState();

  public Untouchable() {
    super("upgraded-agility-untouchable");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_foot"));
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AgilityKip.clearSpeedBurst(this, player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getSpeedAmplifier(level) + 1, 1);
    statLore(v, Form.duration(getSpeedDurationTicks(level) * 50D, 1), 2);
    statLore(v, Form.duration(getRecoveryWindowMillis(level), 1), 3);
    statLore(v, Form.pc(getDodgeChance(level), 0), 4);
    statLore(v, Form.f(getMaxRolledFall(level), 0), 5);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !(e.damager() instanceof ProjectileRef projectile)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (!p.isSprinting() || level <= 0 || !M.r(getDodgeChance(level))) {
        return;
      }

      e.setCancelled(true);
      AgilityDodge.sidestep(this, p, projectile);
      cue(p, MutationCue.FIGHT);
      addStat(p, "agility.untouchable.dodges", 1);
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onStruck(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.damage() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int durationTicks = getSpeedDurationTicks(level);
    struckAt.mark(p.uuid());
    AgilityKip.speedBurst(this, p, getSpeedAmplifier(level), durationTicks);
    cue(p, MutationCue.BOON);
    LingeringSound lingering = linger(p);
    Tasks.runEntity(p, lingering::stop, durationTicks);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.cause() != DamageCause.FALL
        || RiftPearlRebound.ENDER_PEARL.equals(e.damageType())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      double prevented = e.damage();
      if (level <= 0 || prevented <= 0D || fallHeight(p, prevented) > getMaxRolledFall(level)) {
        return;
      }

      e.setCancelled(true);
      roll.roll(p, getConfig().rollProneTicks, prevented);
      cue(p, MutationCue.BOON);
      addStat(p, "agility.untouchable.fall-damage-prevented", prevented);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    boolean groundNow = p.isOnGround();
    Boolean previousGround = wasOnGround.put(id, groundNow);
    boolean groundBefore = previousGround == null ? groundNow : previousGround;
    if (!groundBefore || groundNow || p.velocity().y() <= getConfig().jumpVelocityThreshold) {
      return;
    }

    withPlayerThread(p, e, () -> recover(p, e));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    wasOnGround.remove(id);
    struckAt.clear(id);
  }

  private void recover(PlayerRef p, PlayerMoveEvent e) {
    int level = getActiveLevel(p);
    UUID id = p.uuid();
    if (level <= 0 || !AgilityKip.eligible(p) || struckAt.remaining(id, getRecoveryWindowMillis(level)) <= 0) {
      return;
    }

    Vec3 direction = AgilityKip.intendedDirection(p, e);
    if (direction == null) {
      return;
    }

    struckAt.clear(id);
    AgilityKip.push(this, p, direction, getConfig().recoverySpeed);
    addStat(p, "agility.untouchable.recoveries", 1);
  }

  /** The height of the fall that dealt {@code damage}: the tracked fall distance, else what the damage implies. */
  static double fallHeight(PlayerRef p, double damage) {
    float tracked = p.fallDistance();
    return tracked > 0F ? tracked : damage + SAFE_FALL_BLOCKS;
  }

  double getDodgeChance(int level) {
    return Math.min(1D, getConfig().dodgeChanceBase + (level * getConfig().dodgeChancePerLevel));
  }

  double getMaxRolledFall(int level) {
    List<Double> heights = getConfig().maxRolledFallByLevel;
    if (heights == null || heights.isEmpty() || level <= 0) {
      return 0D;
    }
    Double height = heights.get(Math.min(level, heights.size()) - 1);
    return height == null ? 0D : height;
  }

  int getSpeedAmplifier(int level) {
    return Math.max(0, getConfig().speedAmplifierBase + (level / 2));
  }

  int getSpeedDurationTicks(int level) {
    return Math.max(10, getConfig().speedDurationTicksBase + (level * getConfig().speedDurationTicksPerLevel));
  }

  long getRecoveryWindowMillis(int level) {
    return (long) (getConfig().recoveryWindowMillisBase + (level * getConfig().recoveryWindowMillisPerLevel));
  }

  @ConfigDescription("Sprinting dodges most projectiles, falls up to a height end in a roll, and every hit becomes a speed burst.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Speed burst amplifier at level 1; one more is added every second level.", impact = "Higher values make every hit faster to escape.")
    int speedAmplifierBase = 0;
    @ConfigDoc(value = "Speed burst duration before level scaling, in ticks.", impact = "Higher values keep the speed after a hit longer.")
    int speedDurationTicksBase = 30;
    @ConfigDoc(value = "Speed burst duration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int speedDurationTicksPerLevel = 10;
    @ConfigDoc(value = "Time after a hit in which a jump pushes you on, before level scaling, in milliseconds.", impact = "Higher values make the recovery jump easier to time.")
    double recoveryWindowMillisBase = 600;
    @ConfigDoc(value = "Recovery window added per level, in milliseconds.", impact = "Higher values reward higher levels more.")
    double recoveryWindowMillisPerLevel = 200;
    @ConfigDoc(value = "Horizontal velocity of the recovery push, in blocks per tick.", impact = "Higher values throw you further out of the hit.")
    double recoverySpeed = 0.6;
    @ConfigDoc(value = "Lowest upward velocity that counts as a jump for the recovery push.", impact = "Higher values need a stronger jump.")
    double jumpVelocityThreshold = 0.2;
    @ConfigDoc(value = "Ticks spent in the roll pose after a landing.", impact = "Higher values hold the roll longer.")
    int rollProneTicks = 8;
    @ConfigDoc(value = "Chance to sidestep a projectile while sprinting, before level scaling.", impact = "Higher values dodge more shots.")
    double dodgeChanceBase = 0.3;
    @ConfigDoc(value = "Dodge chance added per level.", impact = "Higher values reward higher levels more.")
    double dodgeChancePerLevel = 0.2;
    @ConfigDoc(value = "Highest fall in blocks that ends in a roll, one entry per level.", impact = "Higher values roll out of taller falls; longer falls hurt as usual.")
    List<Double> maxRolledFallByLevel = new ArrayList<>(List.of(12D, 20D, 30D));
  }
}
