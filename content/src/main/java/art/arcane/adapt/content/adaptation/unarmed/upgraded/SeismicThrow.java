package art.arcane.adapt.content.adaptation.unarmed.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGrab;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPreferences;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedShockwave;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Grapple and Shockwave Clap fused: a grabbed target is hurled as with Grapple, and where it lands it releases a
 * shockwave that damages and knocks back everything around it.
 */
public class SeismicThrow extends SkillUpgrade<SeismicThrow.Config> {
  public static final Id ID = Id.adapt("unarmed/upgrade/seismic_throw");

  private final UnarmedShockwave shockwave = new UnarmedShockwave(this, (PlayerRef player, List<LivingRef> hits) -> {
    xpOnMobs(player, hits, getConfig().xpPerTargetHit);
    addStat(player, "unarmed.seismic-throw.mobs-shaken", hits.size());
  });
  private final UnarmedGrab grab = new UnarmedGrab(this, playerState(), playerState(), cooldowns(),
      () -> getConfig().grabTimeoutMillis, this::getThrow, this::hurled);

  public SeismicThrow() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("piston"));
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    grab.forget(player.player());
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.TARGETS, UnarmedPreferences.RELEASE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getForce(level), 1), 1);
    statLore(v, Format.f(getWaveRange(level), 1), 2);
    statLore(v, Format.f(getWaveDamage(level), 1), 4);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (ItemKinds.isTool(p.inventory().mainHand()) || ItemKinds.isTool(p.inventory().offHand()) || finisher(p, e)) {
      return;
    }

    grab.strike(p, attack.target(), attack.level());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      grab.unsneak(e.player());
    }
  }

  private boolean finisher(PlayerRef p, EntityDamageByEntityEvent e) {
    for (Adaptation<?> upgrade : getSkill().getUpgrades()) {
      if (upgrade instanceof UnbrokenCombo combo && combo.finished(p, e)) {
        return true;
      }
    }
    return false;
  }

  private void hurled(PlayerRef player, LivingRef target) {
    addStat(player, "unarmed.seismic-throw.throws", 1);
    int level = getActiveLevel(player);
    if (level > 0) {
      AdaptApi.scheduler().runEntity(target, () -> new Flight(player, target, level).start());
    }
  }

  private void land(PlayerRef player, LivingRef target, int level) {
    Loc origin = target.location();
    int candidateLimit = UnarmedShockwave.candidateLimit(getConfig().maxCandidatesPerActivation);
    shockwave.release(new UnarmedShockwave.Wave(player, origin, Vec3.ZERO, getWaveRange(level), getWaveForce(level),
        getConfig().waveUpwardForce, getWaveDamage(level), UnarmedShockwave.FULL_CIRCLE, candidateLimit,
        UnarmedShockwave.affectedLimit(getConfig().maxAffectedPerActivation, candidateLimit),
        UnarmedShockwave.targetFxLimit(getConfig().maxTargetFxPerActivation), Set.of(player.uuid(), target.uuid())));
    AdaptApi.scheduler().runEntity(player, () -> cue(player, MutationCue.FIGHT));
  }

  private UnarmedGrab.Throw getThrow(int level) {
    return new UnarmedGrab.Throw(getForce(level), getConfig().upwardBoost, getConfig().maxHurlRange,
        getCooldownMillis(level), getConfig().exhaustionPerThrow);
  }

  double getForce(int level) {
    return getConfig().forceBase + (level * getConfig().forcePerLevel);
  }

  double getWaveRange(int level) {
    return getConfig().waveRangeBase + (level * getConfig().waveRangePerLevel);
  }

  double getWaveForce(int level) {
    return getConfig().waveForceBase + (level * getConfig().waveForcePerLevel);
  }

  double getWaveDamage(int level) {
    return getConfig().waveDamageBase + (level * getConfig().waveDamagePerLevel);
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(1000D, getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  /** Follows a hurled target on its own thread until it comes back down. */
  private final class Flight implements Runnable {
    private final PlayerRef player;
    private final LivingRef target;
    private final int level;
    private TaskHandle handle;
    private int ticks;
    private boolean airborne;

    private Flight(PlayerRef player, LivingRef target, int level) {
      this.player = player;
      this.target = target;
      this.level = level;
    }

    private void start() {
      handle = AdaptApi.scheduler().repeatEntity(target, this, 1, 1, null);
    }

    @Override
    public void run() {
      ticks++;
      if (!target.isValid() || target.isDead() || ticks > getConfig().maxFlightTicks) {
        stop();
        return;
      }
      if (!target.isOnGround()) {
        airborne = true;
        return;
      }
      if (airborne) {
        stop();
        land(player, target, level);
      }
    }

    private void stop() {
      if (handle != null) {
        handle.cancel();
      }
    }
  }

  @ConfigDescription("A thrown enemy lands with a shockwave.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Hurl force before level scaling.", impact = "Higher values throw grabbed targets further.")
    double forceBase = 1.3;
    @ConfigDoc(value = "Hurl force added per level.", impact = "Higher values throw further at higher levels.")
    double forcePerLevel = 0.3;
    @ConfigDoc(value = "Upward part of the hurl.", impact = "Higher values arc thrown targets higher, so they land harder and later.")
    double upwardBoost = 0.6;
    @ConfigDoc(value = "Farthest a grabbed target can be from you when hurled, in blocks.", impact = "Higher values let the grab hold over larger gaps.")
    double maxHurlRange = 6;
    @ConfigDoc(value = "Time before an unused grab expires, in milliseconds.", impact = "Higher values keep the grab ready longer.")
    long grabTimeoutMillis = 5000;
    @ConfigDoc(value = "Cooldown before level scaling, in milliseconds.", impact = "Higher values make throws rarer.")
    double cooldownMillisBase = 9000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels throw more often.")
    double cooldownMillisPerLevel = 1500;
    @ConfigDoc(value = "Exhaustion added per throw.", impact = "Higher values drain food faster; 0 removes the cost.")
    double exhaustionPerThrow = 2.0;
    @ConfigDoc(value = "Longest a thrown target is followed for its landing, in ticks.", impact = "Higher values catch landings after longer flights.")
    int maxFlightTicks = 60;
    @ConfigDoc(value = "Shockwave radius before level scaling, in blocks.", impact = "Higher values reach more targets around the landing.")
    double waveRangeBase = 3.0;
    @ConfigDoc(value = "Shockwave radius added per level, in blocks.", impact = "Higher values widen the shockwave at higher levels.")
    double waveRangePerLevel = 0.75;
    @ConfigDoc(value = "Shockwave knockback before level scaling.", impact = "Higher values push targets further.")
    double waveForceBase = 0.8;
    @ConfigDoc(value = "Shockwave knockback added per level.", impact = "Higher values push further at higher levels.")
    double waveForcePerLevel = 0.2;
    @ConfigDoc(value = "Upward part of the shockwave knockback.", impact = "Higher values pop targets into the air.")
    double waveUpwardForce = 0.35;
    @ConfigDoc(value = "Shockwave damage before level scaling.", impact = "Higher values hurt everything around the landing more.")
    double waveDamageBase = 2;
    @ConfigDoc(value = "Shockwave damage added per level.", impact = "Higher values reward higher levels more.")
    double waveDamagePerLevel = 1.5;
    @ConfigDoc(value = "Most nearby living targets one shockwave inspects.", impact = "Lower values reduce work in crowds; clamped to 32.")
    int maxCandidatesPerActivation = 16;
    @ConfigDoc(value = "Most targets one shockwave pushes.", impact = "Lower values cap the effect in crowds; clamped to 16.")
    int maxAffectedPerActivation = 12;
    @ConfigDoc(value = "Most pushed targets that get their own cloud effect.", impact = "Lower values reduce particles; clamped to 12.")
    int maxTargetFxPerActivation = 8;
    @ConfigDoc(value = "Skill XP for each target the shockwave pushes.", impact = "Higher values reward throwing into groups.")
    double xpPerTargetHit = 10;
  }
}
