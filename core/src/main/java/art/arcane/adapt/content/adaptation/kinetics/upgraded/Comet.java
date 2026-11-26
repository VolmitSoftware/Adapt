package art.arcane.adapt.content.adaptation.kinetics.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.kinetics.KineticsBreachwright;
import art.arcane.adapt.content.adaptation.kinetics.KineticsCushion;
import art.arcane.adapt.content.adaptation.kinetics.KineticsDive;
import art.arcane.adapt.content.adaptation.kinetics.KineticsFormShape;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMassShift;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMeteorCadence;
import art.arcane.adapt.content.adaptation.kinetics.KineticsShockwave;
import art.arcane.adapt.content.adaptation.kinetics.KineticsShred;
import art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meteor Cadence, Windburst and Breachwright fused: a mace dive pulls you down harder, and the smash it ends in
 * shreds the target's armor and releases a wide shockwave that damages, hurls and shreds everything around it. Its
 * mastery is Mass Shift: sneak and swap hands on the ground for Titan form (a wider, heavier shockwave) or Pocket form
 * (a faster dive and a lighter landing), kept until switched.
 */
public class Comet extends SkillUpgrade<Comet.Config> {
  private static final String FORM_KEY = "form";
  private static final long FORM_RECONCILE_MILLIS = 1000L;
  private static final Map<UUID, Strength> STRENGTHENED = new ConcurrentHashMap<>();
  private final KineticsDive dive = new KineticsDive(this, playerState(), playerState());
  private final KineticsShockwave shockwave = new KineticsShockwave(this);
  private final KineticsShred shred = new KineticsShred(this);
  private final Cooldowns waveCooldowns = cooldowns();
  private final Cooldowns targetCooldowns = cooldowns();
  private final Cooldowns formCooldowns = cooldowns();
  private final KineticsFormShape shape = new KineticsFormShape(this);
  private final KineticsCushion landing = new KineticsCushion(this, "comet-pocket-landing");
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public Comet() {
    super("upgraded-kinetics-comet");
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(PlatformSupport.itemOr(Id.minecraft("mace"), Id.minecraft("anvil")));
    ownerMaintenance = AdaptationOwnerPulse.register(this, () -> FORM_RECONCILE_MILLIS, this::reconcileForm);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    super.unregister();
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, KineticsMeteorCadence.CONTROL, KineticsWindburst.HOSTILES);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    dive.end(player.getPlayer());
    reconcileForm(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.f(getArmorShred(level), 1), 2);
    statLore(v, Form.duration(getShredTicks(level) * 50D, 1), 3);
    statLore(v, Form.f(getWaveDamage(level), 1), 4);
    statLore(v, Form.pc(getFormSize(level), 0), 5);
    statLore(v, "+" + Form.f(getTitanRadius(level), 1), 6);
    statLore(v, Form.pc(getPocketLandingCut(level), 0), 7);
  }

  @Override
  public void onTick() {
    targetCooldowns.clearExpired((long) getConfig().targetCooldownMillis);
    formCooldowns.clearExpired((long) getConfig().formSwitchCooldownMillis);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || !p.isOnGround()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    e.setCancelled(true);
    int next = KineticsMassShift.formForPitch(p.location().pitch(), KineticsMassShift.LOOK_PITCH_THRESHOLD);
    if (next == form(p)) {
      return;
    }

    long cooldownMillis = (long) getConfig().formSwitchCooldownMillis;
    if (!formCooldowns.isReady(p.uuid(), cooldownMillis)) {
      fx(p, FxPriority.GAMEPLAY).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    int hunger = getConfig().formSwitchHunger;
    if (!payHungerCost(p, "hunger", hunger, () -> getPlayer(p).consumeFood(hunger, 0))) {
      return;
    }

    formCooldowns.mark(p.uuid(), cooldownMillis);
    setStorage(p, FORM_KEY, next);
    shapeForm(p, next, level);
    addStat(p, "kinetics.comet.form-shifts", 1);
    playFormFeedback(p, next);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || e.damage() <= 0D || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || form(p) != KineticsMassShift.FORM_POCKET) {
      return;
    }

    landing.soften(p, e, new KineticsCushion.Softening(getPocketLandingCut(level), 0D, 0D));
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() && preference(p, KineticsMeteorCadence.CONTROL) == KineticsMeteorCadence.Control.SNEAK) {
      dive.removeModifiers(p);
      return;
    }

    refreshDive(p, p.velocity().y());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc to = e.to();
    if (to == null) {
      return;
    }

    PlayerRef p = e.player();
    if (p.isOnGround()) {
      dive.end(p);
      return;
    }

    refreshDive(p, to.y() - e.from().y());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!KineticsWindburst.smashLands(e.originalResult(), e.result()) || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    KineticsShred.Cut cut = new KineticsShred.Cut(getArmorShred(level), getToughnessShred(level), getShredTicks(level));
    boolean struck = shredTarget(p, e.target(), cut);
    boolean burst = release(p, level, cut, strength(e, p));
    if (struck || burst) {
      cue(p, MutationCue.FIGHT);
    }
  }

  private void refreshDive(PlayerRef p, double deltaY) {
    boolean wantsDive = p.isSneaking()
        || preference(p, KineticsMeteorCadence.CONTROL) == KineticsMeteorCadence.Control.AUTOMATIC;
    if (!KineticsMeteorCadence.isDiving(p.isOnGround(), deltaY, wantsDive, isMace(p.inventory().mainHand()))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !PlatformSupport.check(KineticsDive.NEEDS).usable()) {
      return;
    }

    double hurry = form(p) == KineticsMassShift.FORM_POCKET ? 1D + getFormSize(level) : 1D;
    if (dive.refresh(p, new KineticsDive.Pull(getGravityBoost(level) * hurry, getConfig().dragCut,
        getDownwardAcceleration(level) * hurry, getConfig().terminalFallSpeed))) {
      addStat(p, "kinetics.comet.dives", 1);
    }
  }

  private boolean shredTarget(PlayerRef p, LivingRef target, KineticsShred.Cut cut) {
    if (target == null || !target.isValid() || target.isDead()) {
      return false;
    }

    boolean allowed = target instanceof PlayerRef ? canPVP(p, target.location()) : canPVE(p, target.location());
    long cooldownMillis = (long) getConfig().targetCooldownMillis;
    if (!allowed || !targetCooldowns.isReady(target.uuid(), cooldownMillis)) {
      return false;
    }

    targetCooldowns.mark(target.uuid());
    Tasks.runEntity(target, () -> shred.apply(target, cut));
    return true;
  }

  /** Whether a smash landing now releases the player's shockwave: Comet learned, a long enough dive, the wave ready. */
  public boolean releases(PlayerRef p) {
    return getActiveLevel(p) > 0 && KineticsWindburst.burstReady(p.fallDistance(), getConfig().minFallDistance)
        && waveCooldowns.isReady(p.uuid(), (long) getConfig().waveCooldownMillis);
  }

  /** The shockwave the smash {@code e} releases grows by {@code share}; call before MONITOR. */
  public static void strengthen(EntityAttemptSmashAttackEvent e, PlayerRef p, double share) {
    STRENGTHENED.put(p.uuid(), new Strength(e.dispatchId(), Math.max(0D, share)));
  }

  private static double strength(EntityAttemptSmashAttackEvent e, PlayerRef p) {
    Strength strength = STRENGTHENED.remove(p.uuid());
    return strength != null && strength.dispatchId() == e.dispatchId() ? 1D + strength.share() : 1D;
  }

  private boolean release(PlayerRef p, int level, KineticsShred.Cut cut, double strength) {
    UUID id = p.uuid();
    long cooldownMillis = (long) getConfig().waveCooldownMillis;
    if (!KineticsWindburst.burstReady(p.fallDistance(), getConfig().minFallDistance)
        || !waveCooldowns.isReady(id, cooldownMillis)) {
      return false;
    }

    waveCooldowns.mark(id, cooldownMillis);
    Loc origin = p.location();
    boolean titan = form(p) == KineticsMassShift.FORM_TITAN;
    double weight = (titan ? 1D + getFormSize(level) : 1D) * strength;
    double radius = getRadius(level) + (titan ? getTitanRadius(level) : 0D);
    shockwave.release(new KineticsShockwave.Wave(p, origin, radius, getForce(level) * weight,
        getWaveDamage(level) * weight, preferenceEnabled(p, KineticsWindburst.HOSTILES), (LivingRef target) -> {
          shred.apply(target, cut);
          Tasks.runEntity(p, () -> xpSilentOn(p, target, getConfig().xpPerTargetHit));
        }));
    addStat(p, "kinetics.comet.waves", 1);
    return true;
  }

  private record Strength(long dispatchId, double share) {
  }

  int form(PlayerRef p) {
    return getStorageInt(p, FORM_KEY, KineticsMassShift.FORM_NORMAL);
  }

  private void reconcileForm(PlayerRef p) {
    int level = getActiveLevel(p);
    int form = level > 0 ? form(p) : KineticsMassShift.FORM_NORMAL;
    if (!shape.holds(p, form, getFormSize(level))) {
      shapeForm(p, form, level);
    }
  }

  /**
   * Whether the player holds Comet in {@code original}'s skill, which then owns the forms in place of the kept Mass
   * Shift.
   */
  public static boolean covers(Adaptation<?> original, PlayerRef player) {
    if (original == null || original.getSkill() == null || player == null) {
      return false;
    }
    for (Adaptation<?> upgrade : original.getSkill().getUpgrades()) {
      if (upgrade instanceof Comet comet && comet.getActiveLevel(player) > 0) {
        return true;
      }
    }
    return false;
  }

  private void shapeForm(PlayerRef p, int form, int level) {
    if (form != KineticsMassShift.FORM_NORMAL) {
      KineticsMassShift original = KineticsMassShift.of(getSkill());
      if (original != null) {
        original.release(p);
      }
    }
    shape.shape(p, form, getFormSize(level));
  }

  private void playFormFeedback(PlayerRef p, int form) {
    if (form == KineticsMassShift.FORM_TITAN) {
      fx(p, FxPriority.GAMEPLAY).ring(Particles.CLOUD, 1.1D, 14, 0.03D).sound(Sounds.ENTITY_IRON_GOLEM_REPAIR, 0.7F, 0.5F);
    } else if (form == KineticsMassShift.FORM_POCKET) {
      fx(p, FxPriority.GAMEPLAY).ring(Particles.CLOUD, 0.45D, 8, 0.03D).sound(Sounds.ENTITY_RABBIT_JUMP, 0.6F, 1.7F);
    } else {
      fx(p, FxPriority.GAMEPLAY).ring(Particles.CLOUD, 0.6D, 8, 0.02D).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.2F);
    }
  }

  double getFormSize(int level) {
    return getConfig().formSizeBase + (level * getConfig().formSizePerLevel);
  }

  double getTitanRadius(int level) {
    return getConfig().titanRadiusBase + (level * getConfig().titanRadiusPerLevel);
  }

  double getPocketLandingCut(int level) {
    return Math.min(1D, getFormSize(level) * getConfig().pocketLandingCutPerSize);
  }

  double getRadius(int level) {
    return getConfig().radiusBase + (level * getConfig().radiusPerLevel);
  }

  double getForce(int level) {
    return getConfig().forceBase + (level * getConfig().forcePerLevel);
  }

  double getWaveDamage(int level) {
    return getConfig().waveDamageBase + (level * getConfig().waveDamagePerLevel);
  }

  double getArmorShred(int level) {
    return getConfig().armorShredBase + (level * getConfig().armorShredPerLevel);
  }

  double getToughnessShred(int level) {
    return getConfig().toughnessShredBase + (level * getConfig().toughnessShredPerLevel);
  }

  int getShredTicks(int level) {
    return KineticsBreachwright.shredDurationTicks(getConfig().shredTicksBase, getConfig().shredTicksPerLevel, level);
  }

  double getGravityBoost(int level) {
    return getConfig().gravityBoostBase + (level * getConfig().gravityBoostPerLevel);
  }

  double getDownwardAcceleration(int level) {
    return KineticsMeteorCadence.scaledDownwardAcceleration(getConfig().downwardAccelerationBase,
        getConfig().downwardAccelerationPerLevel, level);
  }

  @ConfigDescription("Mace dives pull harder; the smash shreds armor and releases a wide shockwave that damages and shreds what it hits.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Shockwave radius before level scaling, in blocks.", impact = "Higher values reach more distant targets.")
    double radiusBase = 4.0;
    @ConfigDoc(value = "Shockwave radius added per level, in blocks.", impact = "Higher values widen the wave at higher levels.")
    double radiusPerLevel = 1.5;
    @ConfigDoc(value = "Shockwave knockback force before level scaling.", impact = "Higher values hurl targets farther.")
    double forceBase = 0.7;
    @ConfigDoc(value = "Shockwave knockback force added per level.", impact = "Higher values reward higher levels more.")
    double forcePerLevel = 0.2;
    @ConfigDoc(value = "Shockwave damage before level scaling.", impact = "Higher values hurt everything the wave hits more.")
    double waveDamageBase = 2;
    @ConfigDoc(value = "Shockwave damage added per level.", impact = "Higher values reward higher levels more.")
    double waveDamagePerLevel = 2;
    @ConfigDoc(value = "Fall distance in blocks a smash needs to release the shockwave.", impact = "Higher values demand longer dives.")
    double minFallDistance = 2.5;
    @ConfigDoc(value = "Time between two shockwaves, in milliseconds.", impact = "Lower values allow more frequent waves.")
    double waveCooldownMillis = 3000;
    @ConfigDoc(value = "Armor points removed before level scaling.", impact = "Higher values make shredded targets take more damage.")
    double armorShredBase = 3;
    @ConfigDoc(value = "Armor points removed per level.", impact = "Higher values reward higher levels more.")
    double armorShredPerLevel = 2;
    @ConfigDoc(value = "Armor toughness removed before level scaling.", impact = "Higher values weaken heavily armored targets more.")
    double toughnessShredBase = 1;
    @ConfigDoc(value = "Armor toughness removed per level.", impact = "Higher values reward higher levels more.")
    double toughnessShredPerLevel = 1;
    @ConfigDoc(value = "Shred duration before level scaling, in ticks.", impact = "Higher values keep targets shredded longer.")
    double shredTicksBase = 80;
    @ConfigDoc(value = "Shred duration added per level, in ticks.", impact = "Higher values keep targets shredded longer at higher levels.")
    double shredTicksPerLevel = 30;
    @ConfigDoc(value = "Time before the same target can be shredded again by a direct smash, in milliseconds.", impact = "Higher values stop repeated shreds on one target.")
    double targetCooldownMillis = 2000;
    @ConfigDoc(value = "Gravity multiplier bonus while diving before level scaling.", impact = "Higher values make dives drop faster.")
    double gravityBoostBase = 0.5;
    @ConfigDoc(value = "Gravity multiplier bonus added per level.", impact = "Higher values reward higher levels more.")
    double gravityBoostPerLevel = 0.15;
    @ConfigDoc(value = "Air drag removed while diving.", impact = "Higher values keep more speed through the air.")
    double dragCut = 0.5;
    @ConfigDoc(value = "Downward speed added each tick while diving, before level scaling.", impact = "Higher values reach full dive speed sooner.")
    double downwardAccelerationBase = 0.3;
    @ConfigDoc(value = "Downward speed per tick added per level.", impact = "Higher values reward higher levels more.")
    double downwardAccelerationPerLevel = 0.1;
    @ConfigDoc(value = "Fastest fall speed the dive can produce, in blocks per tick.", impact = "Higher values allow faster dives.")
    double terminalFallSpeed = 4.0;
    @ConfigDoc(value = "Skill XP for each natural mob or player a shockwave hits.", impact = "Higher values reward diving smashes.")
    double xpPerTargetHit = 4;
    @ConfigDoc(value = "Size change of Titan and Pocket form before level scaling, as a fraction.", impact = "Higher values grow and shrink the forms more and strengthen their effects.")
    double formSizeBase = 0.10;
    @ConfigDoc(value = "Size change of the forms added per level, as a fraction.", impact = "Higher values reward higher levels more.")
    double formSizePerLevel = 0.05;
    @ConfigDoc(value = "Extra shockwave radius in Titan form before level scaling, in blocks.", impact = "Higher values widen Titan shockwaves.")
    double titanRadiusBase = 0.5;
    @ConfigDoc(value = "Extra Titan shockwave radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double titanRadiusPerLevel = 0.5;
    @ConfigDoc(value = "Share of fall damage removed in Pocket form for each point of size change.", impact = "Higher values make Pocket landings lighter.")
    double pocketLandingCutPerSize = 2.0;
    @ConfigDoc(value = "Hunger points a form switch costs.", impact = "Higher values make switching forms more expensive.")
    int formSwitchHunger = 2;
    @ConfigDoc(value = "Time between two form switches, in milliseconds.", impact = "Lower values allow faster switching.")
    double formSwitchCooldownMillis = 10000;
  }
}
