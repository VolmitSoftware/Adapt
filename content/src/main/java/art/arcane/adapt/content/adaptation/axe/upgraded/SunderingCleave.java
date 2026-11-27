package art.arcane.adapt.content.adaptation.axe.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.axe.AxeCleaveArc;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.content.adaptation.axe.AxeSunderMarks;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

/**
 * Sunder and Cleave fused: every axe swing strikes everything in a wide frontal arc, and the struck target and every
 * target the arc hits take a stack of armor shred.
 */
public class SunderingCleave extends SkillUpgrade<SunderingCleave.Config> {
  public static final Id ID = Id.adapt("axes/upgrade/sundering_cleave");

  private static final Rgb RUST = Rgb.of(0x8A3B12);
  private final AxeCleaveArc arc = new AxeCleaveArc(this, () -> getConfig().ignorePassiveMobs);
  private final AxeSunderMarks marks = new AxeSunderMarks(this, Id.of("adapt", "upgraded-axes-sundering-cleave"),
      (LivingRef target) -> fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
          .dustRing(RUST, 0.8D, 12, 1.1F),
      (PlayerRef attacker) -> addStat(attacker, "axes.sundering-cleave.stacks-applied", 1));

  public SunderingCleave() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_axe"));
    setInterval(9973);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.IGNORE_PASSIVE, AxePreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, (int) Math.round(getHalfArcDegrees(level) * 2D) + "°", 1);
    statLore(v, Format.pc(getDamageShare(level), 0), 2);
    statLore(v, Format.f(getShredPerStack(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    long now = System.currentTimeMillis();
    if (arc.isCleaving(e.entity().uuid(), now)) {
      return;
    }

    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isAxe);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    int level = combat.level();
    int maxStacks = getMaxStacks(level);
    double shred = getShredPerStack(level);
    marks.apply(p, combat.target(), maxStacks, shred, getConfig().toughnessShredPerStackRatio, getConfig().shredDurationTicks);
    cue(p, MutationCue.FIGHT);

    double secondary = e.damage() * getDamageShare(level);
    if (secondary <= 0D) {
      return;
    }

    List<LivingRef> targets = arc.targets(p, combat.target(), getConfig().radius, getHalfArcDegrees(level),
        getConfig().maxTargets, preferenceEnabled(p, AxePreferences.PLAYERS),
        getConfig().ignorePassiveMobs || preferenceEnabled(p, AxePreferences.IGNORE_PASSIVE));
    for (LivingRef target : targets) {
      arc.strike(target, p, secondary, now, (LivingRef struck) -> marks.apply(p, struck, maxStacks, shred,
          getConfig().toughnessShredPerStackRatio, getConfig().shredDurationTicks));
    }

    if (!targets.isEmpty()) {
      int total = targets.size();
      AdaptApi.scheduler().runEntity(p, () -> {
        damageHand(p, getConfig().durabilityCost);
        addStat(p, "axes.sundering-cleave.targets-hit", total);
        xpOnMobs(p, targets, getConfig().xpPerTarget);
      });
      Loc eye = p.eyeLocation();
      fx(eye.add(eye.direction().normalize().multiply(1.8D)), FxPriority.COMBAT)
          .burst(Particles.SCRAPE, Math.min(12, (total * 2) + 2), 0.6D);
    }
  }

  /** Applies one shred stack at the attacker's level of this upgrade; false when they do not hold it. */
  public boolean shred(PlayerRef attacker, LivingRef target) {
    int level = getActiveLevel(attacker);
    if (level <= 0) {
      return false;
    }
    marks.apply(attacker, target, getMaxStacks(level), getShredPerStack(level), getConfig().toughnessShredPerStackRatio,
        getConfig().shredDurationTicks);
    return true;
  }

  @Override
  public void onTick() {
    arc.prune(System.currentTimeMillis());
  }

  @Override
  public void unregister() {
    arc.clear();
    marks.clear();
    super.unregister();
  }

  double getHalfArcDegrees(int level) {
    return getConfig().halfArcDegreesBase + (level * getConfig().halfArcDegreesPerLevel);
  }

  double getDamageShare(int level) {
    return getConfig().damageShareBase + (level * getConfig().damageSharePerLevel);
  }

  double getShredPerStack(int level) {
    return getConfig().shredPerStackBase + (level * getConfig().shredPerStackPerLevel);
  }

  int getMaxStacks(int level) {
    return Math.max(1, getConfig().maxStacksBase + level);
  }

  @ConfigDescription("Axe swings strike everything in a frontal arc and shred armor on every target hit.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Exclude passive and neutral mobs from the arc and its shred.", impact = "When enabled, protected mobs are skipped. The struck target is unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Half the arc width in degrees before level scaling.", impact = "Higher values widen the arc.")
    double halfArcDegreesBase = 45;
    @ConfigDoc(value = "Half-arc degrees added per level.", impact = "Higher values widen the arc faster with level.")
    double halfArcDegreesPerLevel = 10;
    @ConfigDoc(value = "Arc reach in blocks around the struck target.", impact = "Higher values reach enemies farther out.")
    double radius = 4;
    @ConfigDoc(value = "Most extra targets one swing can hit.", impact = "Lower values cap damage and work in large crowds.")
    int maxTargets = 16;
    @ConfigDoc(value = "Fraction of the swing's damage dealt to each arc target, before level scaling.", impact = "Higher values make the arc hit harder.")
    double damageShareBase = 0.45;
    @ConfigDoc(value = "Damage fraction added per level.", impact = "Higher values reward higher levels more.")
    double damageSharePerLevel = 0.15;
    @ConfigDoc(value = "Armor points removed per stack before level scaling.", impact = "Higher values strip more armor per hit.")
    double shredPerStackBase = 1.5;
    @ConfigDoc(value = "Armor points per stack added per level.", impact = "Higher values reward higher levels more.")
    double shredPerStackPerLevel = 0.75;
    @ConfigDoc(value = "Fraction of each stack's armor shred also removed from armor toughness.", impact = "Higher values bite harder against heavy armor; 0 leaves toughness alone.")
    double toughnessShredPerStackRatio = 0.5;
    @ConfigDoc(value = "Shred stacks a target can carry, before adding the level.", impact = "Higher values let repeated swings strip more armor.")
    int maxStacksBase = 2;
    @ConfigDoc(value = "Ticks a shred layer lasts after the last stack.", impact = "Higher values keep armor down longer between swings.")
    int shredDurationTicks = 120;
    @ConfigDoc(value = "Axe durability spent when the arc hits something.", impact = "Higher values wear the axe faster.")
    int durabilityCost = 1;
    @ConfigDoc(value = "Skill XP for each arc target hit.", impact = "Higher values reward fighting crowds.")
    double xpPerTarget = 4;
  }
}
