package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Map;
import java.util.UUID;

public class KineticsMassShift extends SimpleAdaptation<KineticsMassShift.Config> {
  static final int FORM_NORMAL = 0;
  static final int FORM_TITAN = 1;
  static final int FORM_POCKET = 2;
  private static final String SLOT_SCALE = "form-scale";
  private static final String SLOT_STEP = "form-step";
  private static final String SLOT_CAMERA = "form-camera";
  private static final String SLOT_SPEED = "form-speed";
  private static final double TITAN_STEP_HEIGHT = 1.0D;
  private static final double TITAN_CAMERA_DISTANCE = 2.0D;

  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, FormState> forms = playerState();

  public KineticsMassShift() {
    super("kinetics-mass-shift");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getTitanScale(level), 2), 1);
    statLore(v, Form.f(getPocketScale(level), 2), 2);
    statLore(v, Form.f(getConfig().durationTicks / 20.0D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    e.setCancelled(true);
    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, getConfig().cooldownMs)) {
      return;
    }

    cooldowns.mark(id);
    FormState state = forms.get(id);
    Integer storedForm = state == null ? null : state.form();
    applyForm(p, nextFormFromStored(storedForm), level);
  }

  static int nextForm(int currentForm) {
    return switch (currentForm) {
      case FORM_NORMAL -> FORM_TITAN;
      case FORM_TITAN -> FORM_POCKET;
      default -> FORM_NORMAL;
    };
  }

  static int nextFormFromStored(Integer storedForm) {
    return nextForm(storedForm == null ? FORM_NORMAL : storedForm);
  }

  private void applyForm(Player p, int form, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.removeAll(p, getName());
    if (form == FORM_NORMAL || Attributes.SCALE == null) {
      forms.remove(p.getUniqueId());
      return;
    }

    int durationTicks = getConfig().durationTicks;
    if (form == FORM_TITAN) {
      attributes.applyTimed(p, getName(), SLOT_SCALE, Attributes.SCALE, getTitanScale(level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
      if (Attributes.STEP_HEIGHT != null) {
        attributes.applyTimed(p, getName(), SLOT_STEP, Attributes.STEP_HEIGHT, TITAN_STEP_HEIGHT, AttributeModifier.Operation.ADD_NUMBER, durationTicks);
      }
      if (Attributes.CAMERA_DISTANCE != null) {
        attributes.applyTimed(p, getName(), SLOT_CAMERA, Attributes.CAMERA_DISTANCE, TITAN_CAMERA_DISTANCE, AttributeModifier.Operation.ADD_NUMBER, durationTicks);
      }
      if (Attributes.MOVEMENT_SPEED != null) {
        attributes.applyTimed(p, getName(), SLOT_SPEED, Attributes.MOVEMENT_SPEED, -getConfig().titanSpeedPenalty, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
      }
    } else {
      attributes.applyTimed(p, getName(), SLOT_SCALE, Attributes.SCALE, -getPocketScale(level), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
      if (Attributes.MOVEMENT_SPEED != null) {
        attributes.applyTimed(p, getName(), SLOT_SPEED, Attributes.MOVEMENT_SPEED, getConfig().pocketSpeedBonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
      }
    }

    forms.put(p.getUniqueId(), new FormState(form));
  }

  private double getTitanScale(int level) {
    return getConfig().titanScaleBase + (getLevelPercent(level) * getConfig().titanScaleFactor);
  }

  private double getPocketScale(int level) {
    return getConfig().pocketScaleBase + (getLevelPercent(level) * getConfig().pocketScaleFactor);
  }

  private record FormState(int form) {
  }

  @ConfigDescription("Sneak and swap hands to cycle between titan and pocket forms for a short window.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base size increase of the titan form.", impact = "Higher values make titan form larger.")
    double titanScaleBase = 0.25;
    @ConfigDoc(value = "Additional titan size increase gained at maximum level.", impact = "Higher values make max-level titan form larger.")
    double titanScaleFactor = 0.35;
    @ConfigDoc(value = "Base size decrease of the pocket form.", impact = "Higher values make pocket form smaller.")
    double pocketScaleBase = 0.25;
    @ConfigDoc(value = "Additional pocket size decrease gained at maximum level.", impact = "Higher values make max-level pocket form smaller.")
    double pocketScaleFactor = 0.25;
    @ConfigDoc(value = "Movement speed penalty while in titan form.", impact = "Higher values slow titan form more.")
    double titanSpeedPenalty = 0.15;
    @ConfigDoc(value = "Movement speed bonus while in pocket form.", impact = "Higher values speed pocket form up more.")
    double pocketSpeedBonus = 0.1;
    @ConfigDoc(value = "How long in ticks a form lasts before reverting to normal.", impact = "Higher values keep titan and pocket forms active longer.")
    int durationTicks = 200;
    @ConfigDoc(value = "Cooldown in milliseconds between form activations.", impact = "Higher values force longer waits between form swaps.")
    long cooldownMs = 30000;

    public Config() {
      baseCost = 6;
      costFactor = 0.45;
      maxLevel = 3;
      initialCost = 5;
    }
  }
}
