package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KineticsDeadZone extends SimpleAdaptation<KineticsDeadZone.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> HOSTILES = CommonPreferences.toggle("hostiles", KineticsMessages.KINETICSDEADZONE_PREFERENCE_HOSTILES, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", KineticsMessages.KINETICSDEADZONE_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);

  private static final double SHOVE_DAMPING = 0.2D;
  private static final double SHOVE_LIFT = 0.25D;
  private static final Rgb ZONE = Rgb.of(0xC9D9E8);
  private final Map<UUID, Long> riposteUntil = playerState();
  private final Cooldowns shoveCooldown = cooldowns();

  public KineticsDeadZone() {
    super("kinetics-dead-zone");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("armor_stand"));
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HOSTILES, SNEAK);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    riposteUntil.remove(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getDeadZoneRange(level), 1), 1);
    statLore(v, Form.pc(getRiposteBonus(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    handleShove(e);
    handleRiposte(e);
  }

  static boolean inDeadZone(double distance, double range) {
    if (!Double.isFinite(distance) || !Double.isFinite(range)) {
      return false;
    }

    return distance >= 0D && range > 0D && distance <= range;
  }

  static boolean riposteActive(long nowMs, long deadlineMs) {
    return deadlineMs > 0L && nowMs <= deadlineMs;
  }

  static Vec3 shoveVector(Vec3 defenderPosition, Vec3 attackerPosition, double force) {
    Vec3 away = attackerPosition.subtract(defenderPosition).withY(0);
    if (away.lengthSquared() < 1.0E-6D) {
      away = new Vec3(0, 0, 1);
    } else {
      away = away.normalize();
    }

    return away.multiply(Math.max(0D, force)).withY(SHOVE_LIFT);
  }

  private void handleShove(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef defender)) {
      return;
    }

    if (!(e.damager() instanceof LivingRef attacker) || attacker.uuid().equals(defender.uuid())) {
      return;
    }

    if ((preferenceEnabled(defender, HOSTILES) && !attacker.has(EntityTrait.MONSTER))
        || (preferenceEnabled(defender, SNEAK) && !defender.isSneaking())) {
      return;
    }
    int level = getActiveLevel(defender);
    if (level <= 0 || !isSpear(defender.inventory().mainHand())) {
      return;
    }

    double distance = defender.location().distance(attacker.location());
    if (!inDeadZone(distance, getDeadZoneRange(level))) {
      return;
    }

    if (!shoveCooldown.isReady(defender.uuid(), getConfig().cooldownMs)) {
      return;
    }

    boolean allowed = attacker instanceof PlayerRef
        ? canPVP(defender, attacker.location())
        : canPVE(defender, attacker.location());
    if (!allowed) {
      return;
    }

    shoveCooldown.mark(defender.uuid());
    Vec3 shove = shoveVector(defender.location().position(), attacker.location().position(), getShoveForce(level));
    Tasks.runEntity(attacker, () -> {
      if (!isRuntimeRegistered() || !attacker.isValid()) {
        return;
      }

      attacker.setVelocity(attacker.velocity().multiply(SHOVE_DAMPING).add(shove));
    });
    riposteUntil.put(defender.uuid(), System.currentTimeMillis() + (getConfig().riposteWindowTicks * 50L));
    fx(defender.location().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .dustBurst(ZONE, 5, 0.4D, 1.0F)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.4F, 1.5F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 0.8F);
  }

  private void handleRiposte(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef attacker)) {
      return;
    }

    Long deadline = riposteUntil.get(attacker.uuid());
    if (deadline == null || !riposteActive(System.currentTimeMillis(), deadline)) {
      return;
    }

    MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null || (preferenceEnabled(attacker, HOSTILES) && !combat.target().has(EntityTrait.MONSTER))
        || (preferenceEnabled(attacker, SNEAK) && !attacker.isSneaking())) {
      return;
    }

    riposteUntil.remove(combat.attacker().uuid());
    e.setDamage(e.damage() * (1D + getRiposteBonus(combat.level())));
    fx(combat.target().location().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.CRIT, 8, 0, 0, 0, 0.25D, 0.05D)
        .dustBurst(ZONE, 3, 0.3D, 1.0F)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.3F);
  }

  private double getDeadZoneRange(int level) {
    return getConfig().deadZoneRangeBase + (getLevelPercent(level) * getConfig().deadZoneRangeFactor);
  }

  private double getShoveForce(int level) {
    return getConfig().shoveForceBase + (getLevelPercent(level) * getConfig().shoveForceFactor);
  }

  private double getRiposteBonus(int level) {
    return getConfig().riposteBonusBase + (getLevelPercent(level) * getConfig().riposteBonusFactor);
  }

  @ConfigDescription("Enemies that crowd inside your spear's dead zone get shoved out, arming a riposte.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base radius of the dead zone around you while holding a spear.", impact = "Higher values shove attackers from farther away.")
    double deadZoneRangeBase = 2.0;
    @ConfigDoc(value = "Additional dead zone radius granted at max level.", impact = "Higher values widen the dead zone as the adaptation levels.")
    double deadZoneRangeFactor = 1.0;
    @ConfigDoc(value = "Base force of the shove applied to crowding attackers.", impact = "Higher values throw attackers farther out of the dead zone.")
    double shoveForceBase = 0.5;
    @ConfigDoc(value = "Additional shove force granted at max level.", impact = "Higher values make leveled shoves much stronger.")
    double shoveForceFactor = 0.6;
    @ConfigDoc(value = "How long the riposte window stays armed after a shove, in ticks.", impact = "Higher values give more time to land the boosted counterattack.")
    int riposteWindowTicks = 30;
    @ConfigDoc(value = "Base damage bonus applied to a riposte strike.", impact = "Higher values make counterattacks hit harder at minimum level.")
    double riposteBonusBase = 0.2;
    @ConfigDoc(value = "Additional riposte damage bonus granted at max level.", impact = "Higher values scale counterattack damage with levels.")
    double riposteBonusFactor = 0.4;
    @ConfigDoc(value = "Minimum milliseconds between dead zone shoves.", impact = "Higher values space out shoves; lower values trigger them more often.")
    long cooldownMs = 3000;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
