package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

public class KineticsSurfaceSkate extends SimpleAdaptation<KineticsSurfaceSkate.Config> {
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_SLIDE = "slide";
  private static final String SLOT_GRIP = "grip";

  public KineticsSurfaceSkate() {
    super("kinetics-surface-skate");
    registerConfiguration(Config.class);
    setIcon(Material.PACKED_ICE);
    setInterval(RECONCILE_INTERVAL_MS);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSlideFriction(level), 0), 1);
    statLore(v, Form.pc(getGripFriction(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    Player p = e.getPlayer();
    if (Attributes.FRICTION_MODIFIER == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSprinting()) {
      attributes.remove(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, -getSlideFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    });
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (Attributes.FRICTION_MODIFIER == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSneaking()) {
      attributes.remove(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      attributes.apply(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER, getGripFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    });
  }

  static double slideFriction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static double gripFriction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static boolean shouldSlide(boolean sprinting, int level) {
    return sprinting && level > 0;
  }

  static boolean shouldGrip(boolean sneaking, int level) {
    return sneaking && level > 0;
  }

  private void reconcile(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    int level = getActiveLevel(p);
    if (Attributes.FRICTION_MODIFIER == null || level <= 0) {
      attributes.removeAll(p, getName());
      return;
    }

    if (shouldSlide(p.isSprinting(), level)) {
      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, -getSlideFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    } else {
      attributes.remove(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
    }

    if (shouldGrip(p.isSneaking(), level)) {
      attributes.apply(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER, getGripFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    } else {
      attributes.remove(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER);
    }
  }

  private double getSlideFriction(int level) {
    return slideFriction(getConfig().slideFrictionBase, getConfig().slideFrictionFactor, getLevelPercent(level));
  }

  private double getGripFriction(int level) {
    return gripFriction(getConfig().gripFrictionBase, getConfig().gripFrictionFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sprint to slide across the ground with lowered friction; sneak to grip hard.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base fraction of ground friction removed while sprinting before level scaling.", impact = "Higher values make sprint-sliding slicker at every level.")
    double slideFrictionBase = 0.15;
    @ConfigDoc(value = "Additional friction reduction granted at max level while sprinting.", impact = "Higher values widen the slickness gain from leveling.")
    double slideFrictionFactor = 0.35;
    @ConfigDoc(value = "Base fraction of extra ground friction applied while sneaking before level scaling.", impact = "Higher values make sneak-gripping stickier at every level.")
    double gripFrictionBase = 0.2;
    @ConfigDoc(value = "Additional friction increase granted at max level while sneaking.", impact = "Higher values widen the grip gain from leveling.")
    double gripFrictionFactor = 0.4;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
