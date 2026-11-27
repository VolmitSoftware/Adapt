package art.arcane.adapt.content.adaptation.kinetics.upgraded;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.kinetics.KineticsCushion;
import art.arcane.adapt.content.adaptation.kinetics.KineticsJumpBoost;
import art.arcane.adapt.content.adaptation.kinetics.KineticsLandingWatch;
import art.arcane.adapt.content.adaptation.kinetics.KineticsSprings;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;

/**
 * Moon Jump, Rubber Soul and Soft Catch fused: every jump is higher with a floaty peak, every landing is springier,
 * and falls deal no damage on any block. Jumps need the jump strength and gravity attributes, and the springy soles
 * need bounciness; each part is inert where its attribute is missing.
 */
public class Featherweight extends SkillUpgrade<Featherweight.Config> {
  public static final Id ID = Id.adapt("kinetics/upgrade/featherweight");

  private final KineticsJumpBoost boost = new KineticsJumpBoost(this, cooldowns());
  private final KineticsLandingWatch landings = new KineticsLandingWatch(playerState(), playerState());
  private final KineticsSprings springs = new KineticsSprings(this);
  private final KineticsCushion cushion = new KineticsCushion(this, "kinetics:featherweight");

  public Featherweight() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("white_wool"));
    setInterval(1000);
    pulseOwners(this::getInterval, this::maintain);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    maintain(player.player());
  }

  @Override
  public void unregister() {
    landings.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getExtraJumpHeight(level), 2), 1);
    statLore(v, Format.f(getBounciness(level), 2), 2);
    statLore(v, Format.pc(1D, 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJumpEvent e) {
    PlayerRef p = e.player();
    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !jumpsSupported()) {
        return;
      }

      applyBaseJump(p, level);
      boost.hop(p, new KineticsJumpBoost.Hop(getConfig().hopBonus, getGravityReduction(level),
          getConfig().floatWindowTicks));
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    landings.observe(this, e, this::springload);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    if (!e.isCancelled()) {
      landings.forget(e.player().uuid());
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || e.damage() <= 0D || !(e.entity() instanceof PlayerRef p)
        || getActiveLevel(p) <= 0) {
      return;
    }

    double prevented = cushion.soften(p, e, new KineticsCushion.Softening(1D, 0D, 0D));
    if (prevented > 0D) {
      e.setCancelled(true);
      addStat(p, "kinetics.featherweight.falls-cushioned", 1);
      cue(p, MutationCue.BOON);
    }
  }

  private void springload(PlayerRef p, PlayerMoveEvent e) {
    int level = getActiveLevel(p);
    if (level > 0 && springs.canSpringload(p)) {
      springs.springload(p, getSpringloadBonus(level), getConfig().springloadTicks);
      cue(p, MutationCue.BOON);
    }
  }

  private void maintain(PlayerRef p) {
    int level = getActiveLevel(p);
    springs.maintainSole(p, level > 0, getBounciness(level));
    if (!jumpsSupported()) {
      return;
    }
    if (level > 0) {
      applyBaseJump(p, level);
    } else {
      boost.removeBase(p);
    }
  }

  private void applyBaseJump(PlayerRef p, int level) {
    boost.applyBase(p, KineticsJumpBoost.strengthBonusForExtraHeight(getExtraJumpHeight(level)));
  }

  private static boolean jumpsSupported() {
    return PlatformNeed.usable(KineticsJumpBoost.NEEDS);
  }

  double getExtraJumpHeight(int level) {
    return getConfig().extraJumpHeightBase + (level * getConfig().extraJumpHeightPerLevel);
  }

  double getGravityReduction(int level) {
    return Math.min(0.9D, getConfig().gravityReductionBase + (level * getConfig().gravityReductionPerLevel));
  }

  double getBounciness(int level) {
    return getConfig().bouncinessBase + (level * getConfig().bouncinessPerLevel);
  }

  double getSpringloadBonus(int level) {
    return getConfig().springloadBonusBase + (level * getConfig().springloadBonusPerLevel);
  }

  @ConfigDescription("Every jump goes higher with a floaty peak, landings are springier, and falls deal no damage.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Blocks added to every standing jump before level scaling.", impact = "Higher values raise every jump.")
    double extraJumpHeightBase = 0.5;
    @ConfigDoc(value = "Blocks added to every standing jump per level.", impact = "Higher values reward higher levels more.")
    double extraJumpHeightPerLevel = 0.5;
    @ConfigDoc(value = "Jump strength added to the jump itself on top of the standing bonus.", impact = "Higher values launch each jump harder.")
    double hopBonus = 0.08;
    @ConfigDoc(value = "Fraction of gravity removed at the jump's peak before level scaling.", impact = "Higher values make the peak floatier.")
    double gravityReductionBase = 0.2;
    @ConfigDoc(value = "Fraction of gravity removed per level; the total stops at 0.9.", impact = "Higher values reward higher levels more.")
    double gravityReductionPerLevel = 0.1;
    @ConfigDoc(value = "How long the floaty peak lasts, in ticks.", impact = "Higher values keep gravity low for longer after each jump.")
    long floatWindowTicks = 30;
    @ConfigDoc(value = "Standing bounciness before level scaling.", impact = "Higher values keep more momentum on every landing.")
    double bouncinessBase = 0.25;
    @ConfigDoc(value = "Standing bounciness added per level.", impact = "Higher values reward higher levels more.")
    double bouncinessPerLevel = 0.1;
    @ConfigDoc(value = "Extra bounciness after landing on a bouncy block, before level scaling.", impact = "Higher values launch bouncy-block landings harder.")
    double springloadBonusBase = 0.5;
    @ConfigDoc(value = "Extra bouncy-block bounciness added per level.", impact = "Higher values reward higher levels more.")
    double springloadBonusPerLevel = 0.15;
    @ConfigDoc(value = "How long the bouncy-block bonus lasts, in ticks.", impact = "Higher values keep the bonus active longer after each bouncy landing.")
    long springloadTicks = 40;
  }
}
