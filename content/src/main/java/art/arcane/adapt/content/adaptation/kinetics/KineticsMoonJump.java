package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class KineticsMoonJump extends SimpleAdaptation<KineticsMoonJump.Config> {
  public static final Id ID = Id.adapt("kinetics/moon_jump");
  public static final PlayerPreference<CommonPreferences.Toggle> BASE_JUMP = CommonPreferences.toggle("base-jump", Text.key("adapt.kineticsmoonjump.preferences.preference_base_jump"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<Hop> HOP = PlayerPreference.of("hop", Hop.class, Hop.SNEAK, List.of(
          new PlayerPreference.Choice<>(Hop.SNEAK, Text.key("adapt.kineticsmoonjump.preferences.preference_hop_sneak"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Hop.ALWAYS, Text.key("adapt.kineticsmoonjump.preferences.preference_hop_always"), Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Hop.NEVER, Text.key("adapt.kineticsmoonjump.preferences.preference_hop_never"), Id.minecraft("red_stained_glass_pane"), 1))).labelled(Text.key("adapt.kineticsmoonjump.preferences.preference_hop"));

  private static final double BASE_JUMP_HEIGHT_PER_LEVEL = 0.5D;
  private static final long RECONCILE_INTERVAL_MS = 1000L;

  private final KineticsJumpBoost boost = new KineticsJumpBoost(this, cooldowns());

  public KineticsMoonJump() {
    super(ID);
    needs(KineticsJumpBoost.NEEDS.toArray(new PlatformNeed[0]));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_foot"));
    setInterval(RECONCILE_INTERVAL_MS);
    pulseOwners(this::getInterval, this::reconcileBaseJump);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BASE_JUMP, HOP);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    reconcileBaseJump(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(baseJumpHeight(level), 2), 1);
    statLore(v, Format.f(getJumpBonus(level), 2), 2);
    statLore(v, Format.pc(getGravityReduction(level), 0), 3);
    statLore(v, Format.duration(getFloatWindowTicks(level) * 50D, 1), 4);
  }

  @Override
  public void unregister() {
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJumpEvent e) {
    PlayerRef p = e.player();
    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        removeBaseJump(p);
        return;
      }

      applyBaseJump(p, level);
      if (preference(p, HOP) == Hop.NEVER || (preference(p, HOP) == Hop.SNEAK && !p.isSneaking()) || !canUse(p)) {
        return;
      }

      boost.hop(p, new KineticsJumpBoost.Hop(getJumpBonus(level), getGravityReduction(level), getFloatWindowTicks(level)));
    });
  }

  public static double jumpBonus(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  public static double gravityReduction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.min(1D, Math.max(0D, value));
  }

  public static long floatWindowTicks(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 1L;
    }
    return Math.max(1L, Math.round(value));
  }

  public static double baseJumpHeight(int level) {
    return KineticsJumpPhysics.VANILLA_JUMP_HEIGHT + (Math.max(0, level) * BASE_JUMP_HEIGHT_PER_LEVEL);
  }

  public static double baseJumpStrengthBonus(int level) {
    return KineticsJumpPhysics.bonusForHeight(baseJumpHeight(level));
  }

  private void reconcileBaseJump(PlayerRef p) {
    int level = getActiveLevel(p);
    if (level <= 0) {
      removeBaseJump(p);
      return;
    }

    applyBaseJump(p, level);
  }

  private void applyBaseJump(PlayerRef p, int level) {
    if (!preferenceEnabled(p, BASE_JUMP)) {
      removeBaseJump(p);
      return;
    }
    boost.applyBase(p, baseJumpStrengthBonus(level));
  }

  private void removeBaseJump(PlayerRef p) {
    boost.removeBase(p);
  }

  private double getJumpBonus(int level) {
    return jumpBonus(getConfig().jumpBonusBase, getConfig().jumpBonusFactor, getLevelPercent(level));
  }

  private double getGravityReduction(int level) {
    return gravityReduction(getConfig().gravityReductionBase, getConfig().gravityReductionFactor, getLevelPercent(level));
  }

  private long getFloatWindowTicks(int level) {
    return floatWindowTicks(getConfig().floatWindowTicksBase, getConfig().floatWindowTicksFactor, getLevelPercent(level));
  }

  public static void hopFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.GAMEPLAY)
        .ring(Particles.CLOUD, 0.6D, 8, 0.05D)
        .sound(Sounds.ENTITY_RABBIT_JUMP, 0.6F, 0.7F);
  }

  @ConfigDescription("Each level raises every jump by 0.5 blocks; sneak-jump for an additional floaty, low-gravity hop.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base jump strength bonus applied on a sneak-jump before level scaling.", impact = "Higher values raise the hop height at every level.")
    double jumpBonusBase = 0.06;
    @ConfigDoc(value = "Additional jump strength bonus granted at max level.", impact = "Higher values widen the hop-height gain from leveling.")
    double jumpBonusFactor = 0.10;
    @ConfigDoc(value = "Base fraction of gravity removed during the float window before level scaling.", impact = "Higher values make the jump peak floatier at every level.")
    double gravityReductionBase = 0.15;
    @ConfigDoc(value = "Additional gravity reduction granted at max level.", impact = "Higher values widen the floatiness gain from leveling.")
    double gravityReductionFactor = 0.30;
    @ConfigDoc(value = "Base duration in ticks of the low-gravity float window before level scaling.", impact = "Higher values keep the floaty peak active longer at every level.")
    double floatWindowTicksBase = 20;
    @ConfigDoc(value = "Additional float window ticks granted at max level.", impact = "Higher values extend the float window when leveled.")
    double floatWindowTicksFactor = 20;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Hop { SNEAK, ALWAYS, NEVER }
}
