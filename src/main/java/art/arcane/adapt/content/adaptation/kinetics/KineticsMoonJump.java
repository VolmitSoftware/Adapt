package art.arcane.adapt.content.adaptation.kinetics;

import java.util.List;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class KineticsMoonJump extends SimpleAdaptation<KineticsMoonJump.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BASE_JUMP = CommonPreferences.toggle("base-jump", KineticsMessages.KINETICSMOONJUMP_PREFERENCE_BASE_JUMP, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<Hop> HOP = new PlayerPreference<>(Hop.class,
      new PlayerPreference.Definition<>("hop", KineticsMessages.KINETICSMOONJUMP_PREFERENCE_HOP, Hop.SNEAK, List.of(
          new PlayerPreference.Choice<>(Hop.SNEAK, KineticsMessages.KINETICSMOONJUMP_PREFERENCE_HOP_SNEAK, Material.LEATHER_BOOTS, 1),
          new PlayerPreference.Choice<>(Hop.ALWAYS, KineticsMessages.KINETICSMOONJUMP_PREFERENCE_HOP_ALWAYS, Material.FEATHER, 1),
          new PlayerPreference.Choice<>(Hop.NEVER, KineticsMessages.KINETICSMOONJUMP_PREFERENCE_HOP_NEVER, Material.RED_STAINED_GLASS_PANE, 1))));

  private static final String SLOT_BASE_JUMP = "base-jump";
  private static final String SLOT_HOP = "hop";
  private static final String SLOT_FLOAT = "float";
  private static final double BASE_JUMP_HEIGHT_PER_LEVEL = 0.5D;
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final long HOP_DURATION_TICKS = 10L;

  private final Cooldowns fxCooldown = cooldowns();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsMoonJump() {
    super("kinetics-moon-jump");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_FOOT);
    setInterval(RECONCILE_INTERVAL_MS);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        this::reconcileBaseJump
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BASE_JUMP, HOP);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
    reconcileBaseJump(player.getPlayer());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(baseJumpHeight(level), 2), 1);
    statLore(v, Form.f(getJumpBonus(level), 2), 2);
    statLore(v, Form.pc(getGravityReduction(level), 0), 3);
    statLore(v, Form.duration(getFloatWindowTicks(level) * 50D, 1), 4);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    super.unregister();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerJumpEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        removeBaseJump(p);
        return;
      }

      applyBaseJump(p, level);
      if (preference(p, HOP) == Hop.NEVER || (preference(p, HOP) == Hop.SNEAK && !p.isSneaking()) || !canUse(getPlayer(p))) {
        return;
      }

      AdaptAttributeService attributes = AdaptAttributeService.get();
      if (Attributes.JUMP_STRENGTH != null) {
        attributes.applyTimed(p, getName(), SLOT_HOP, Attributes.JUMP_STRENGTH, getJumpBonus(level), AttributeModifier.Operation.ADD_NUMBER, HOP_DURATION_TICKS);
      }
      if (Attributes.GRAVITY != null) {
        attributes.applyTimed(p, getName(), SLOT_FLOAT, Attributes.GRAVITY, -getGravityReduction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1, getFloatWindowTicks(level));
      }

      if (fxCooldown.isReady(p.getUniqueId(), 1000)) {
        fxCooldown.mark(p.getUniqueId());
        fx(p.getLocation(), FxPriority.GAMEPLAY)
            .ring(Particle.CLOUD, 0.6D, 8, 0.05D)
            .sound(Sound.ENTITY_RABBIT_JUMP, 0.6F, 0.7F);
      }
    });
  }

  static double jumpBonus(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static double gravityReduction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.min(1D, Math.max(0D, value));
  }

  static long floatWindowTicks(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 1L;
    }
    return Math.max(1L, Math.round(value));
  }

  static double baseJumpHeight(int level) {
    return KineticsJumpPhysics.VANILLA_JUMP_HEIGHT + (Math.max(0, level) * BASE_JUMP_HEIGHT_PER_LEVEL);
  }

  static double baseJumpStrengthBonus(int level) {
    return KineticsJumpPhysics.bonusForHeight(baseJumpHeight(level));
  }

  private void reconcileBaseJump(Player p) {
    int level = getActiveLevel(p);
    if (level <= 0) {
      removeBaseJump(p);
      return;
    }

    applyBaseJump(p, level);
  }

  private void applyBaseJump(Player p, int level) {
    if (!preferenceEnabled(p, BASE_JUMP)) {
      removeBaseJump(p);
      return;
    }
    if (Attributes.JUMP_STRENGTH != null) {
      AdaptAttributeService.get().apply(p, getName(), SLOT_BASE_JUMP, Attributes.JUMP_STRENGTH,
          baseJumpStrengthBonus(level), AttributeModifier.Operation.ADD_NUMBER);
    }
  }

  private void removeBaseJump(Player p) {
    if (Attributes.JUMP_STRENGTH != null) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_BASE_JUMP, Attributes.JUMP_STRENGTH);
    }
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
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Hop { SNEAK, ALWAYS, NEVER }
}
