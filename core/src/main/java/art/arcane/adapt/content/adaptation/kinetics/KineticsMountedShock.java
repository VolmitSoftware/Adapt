package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

public class KineticsMountedShock extends SimpleAdaptation<KineticsMountedShock.Config> {
  private final Cooldowns chargeCooldown = cooldowns();

  public KineticsMountedShock() {
    super("kinetics-mounted-shock");
    needs(PlatformNeed.item("iron_spear"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("saddle"));
    setInterval(9999);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getMountSpeedFactor(level), 2), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    EntityRef vehicle = p.vehicle();
    if (vehicle == null) {
      return;
    }

    if (!chargeCooldown.isReady(p.uuid(), getConfig().cooldownMs)) {
      return;
    }

    double bonus = mountedBonus(vehicle.velocity().length(), getMountSpeedFactor(combat.level()), getBonusCap(combat.level()));
    if (bonus <= 0D) {
      return;
    }

    chargeCooldown.mark(p.uuid(), getConfig().cooldownMs);
    e.setDamage(e.damage() * (1D + bonus));
    addStat(p, "kinetics.mounted.charges", 1);
    fx(combat.target().location().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .particle(Particles.CRIT, 6, 0.2D, 0.1D, 0.2D, 0.2D, 0.05D)
        .chord(Sounds.ENTITY_HORSE_GALLOP, 0.4F, 1.2F, Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.5F, 1.1F);
  }

  static double mountedBonus(double mountSpeed, double speedFactor, double cap) {
    if (!Double.isFinite(mountSpeed) || !Double.isFinite(speedFactor) || !Double.isFinite(cap)) {
      return 0D;
    }

    double bonus = Math.max(0D, mountSpeed) * Math.max(0D, speedFactor);
    return Math.max(0D, Math.min(cap, bonus));
  }

  private double getMountSpeedFactor(int level) {
    return getConfig().mountSpeedFactorBase + (getLevelPercent(level) * getConfig().mountSpeedFactorFactor);
  }

  private double getBonusCap(int level) {
    return getConfig().bonusCapBase + (getLevelPercent(level) * getConfig().bonusCapFactor);
  }

  @ConfigDescription("Spear charges from the saddle hit harder the faster your mount moves.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base multiplier converting mount speed into bonus spear damage.", impact = "Higher values pay more damage per unit of mount speed; this stacks with Taming Mounted Tactics, so keep it modest.")
    double mountSpeedFactorBase = 1.0;
    @ConfigDoc(value = "Additional mount-speed multiplier granted at max level.", impact = "Higher values widen the speed-scaling gain from leveling; stacks with Taming Mounted Tactics mounted damage.")
    double mountSpeedFactorFactor = 1.5;
    @ConfigDoc(value = "Base hard cap on the mounted damage bonus.", impact = "Lower values keep charges tame even on fast mounts and limit stacking with Taming Mounted Tactics.")
    double bonusCapBase = 0.4;
    @ConfigDoc(value = "Additional damage-bonus cap granted at max level.", impact = "Higher values let leveled charges keep more of their speed bonus.")
    double bonusCapFactor = 0.6;
    @ConfigDoc(value = "Minimum milliseconds between boosted mounted charges.", impact = "Higher values space out boosted charges; lower values trigger them more often.")
    long cooldownMs = 2000;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
