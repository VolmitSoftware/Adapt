package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KineticsMassShift extends SimpleAdaptation<KineticsMassShift.Config> {
  static final int FORM_NORMAL = 0;
  static final int FORM_TITAN = 1;
  static final int FORM_POCKET = 2;
  static final double FORM_COMBAT_SCALAR = 0.2D;
  static final float LOOK_PITCH_THRESHOLD = 25F;
  private static final String SLOT_SCALE = "form-scale";
  private static final String SLOT_STEP = "form-step";
  private static final String SLOT_CAMERA = "form-camera";
  private static final String SLOT_DAMAGE = "form-damage";
  private static final String SLOT_HEALTH = "form-health";
  private static final double TITAN_STEP_HEIGHT = 1.0D;
  private static final double TITAN_CAMERA_DISTANCE = 2.0D;
  private static final int FORM_EFFECT_DURATION_TICKS = 60;
  private static final int FORM_EFFECT_REFRESH_TICKS = 30;
  private static final long RECONCILE_INTERVAL_MS = 1000L;

  private final Map<UUID, FormState> forms = playerState();

  public KineticsMassShift() {
    super("kinetics-mass-shift");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(RECONCILE_INTERVAL_MS);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getTitanScale(level), 2), 1);
    statLore(v, Form.f(getPocketScale(level), 2), 2);
    statLore(v, Form.pc(FORM_COMBAT_SCALAR, 0), 3);
    statLore(v, C.RED, "- ", Form.pc(FORM_COMBAT_SCALAR, 0), 4);
    v.addLore(C.YELLOW + "* " + C.GRAY + Localizer.dLocalize("kinetics.mass_shift.lore5"));
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    forms.replaceAll((id, state) -> new FormState(state.form(), -1, state.ownsEffect()));
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  public void onTick() {
    Set<UUID> activeForms = new HashSet<>(forms.keySet());
    for (UUID id : activeForms) {
      Player player = Bukkit.getPlayer(id);
      if (player == null) {
        forms.remove(id);
        continue;
      }
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          forms.remove(id);
          return;
        }
        reconcileForm(player);
      });
    }
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
    applyForm(p, formForPitch(p.getLocation().getPitch(), LOOK_PITCH_THRESHOLD), level);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    resetForm(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerDeathEvent e) {
    resetForm(e.getEntity());
  }

  static int formForPitch(float pitch, float threshold) {
    if (!Float.isFinite(pitch)) {
      return FORM_NORMAL;
    }

    float safeThreshold = Float.isFinite(threshold)
        ? Math.min(89F, Math.max(5F, Math.abs(threshold)))
        : LOOK_PITCH_THRESHOLD;
    if (pitch <= -safeThreshold) {
      return FORM_TITAN;
    }
    if (pitch >= safeThreshold) {
      return FORM_POCKET;
    }
    return FORM_NORMAL;
  }

  static double combatScalar(int form) {
    return switch (form) {
      case FORM_TITAN -> FORM_COMBAT_SCALAR;
      case FORM_POCKET -> -FORM_COMBAT_SCALAR;
      default -> 0D;
    };
  }

  private void applyForm(Player p, int form, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    FormState previous = forms.remove(p.getUniqueId());
    clearOwnedEffect(p, previous);
    attributes.removeAll(p, getName());
    if (form == FORM_NORMAL) {
      clampHealth(p);
      playFormFeedback(p, form);
      return;
    }

    applyCombatAttributes(p, attributes, form);
    if (form == FORM_TITAN) {
      if (Attributes.SCALE != null) {
        attributes.apply(p, getName(), SLOT_SCALE, Attributes.SCALE, getTitanScale(level), AttributeModifier.Operation.ADD_NUMBER);
      }
      if (Attributes.STEP_HEIGHT != null) {
        attributes.apply(p, getName(), SLOT_STEP, Attributes.STEP_HEIGHT, TITAN_STEP_HEIGHT, AttributeModifier.Operation.ADD_NUMBER);
      }
      if (Attributes.CAMERA_DISTANCE != null) {
        attributes.apply(p, getName(), SLOT_CAMERA, Attributes.CAMERA_DISTANCE, TITAN_CAMERA_DISTANCE, AttributeModifier.Operation.ADD_NUMBER);
      }
    } else {
      if (Attributes.SCALE != null) {
        attributes.apply(p, getName(), SLOT_SCALE, Attributes.SCALE, -getPocketScale(level), AttributeModifier.Operation.ADD_NUMBER);
      }
    }

    FormState initialState = new FormState(form, level, false);
    boolean ownsEffect = ensureFormEffect(p, initialState);
    forms.put(p.getUniqueId(), new FormState(form, level, ownsEffect));
    clampHealth(p);
    playFormFeedback(p, form);
  }

  private void applyCombatAttributes(Player p, AdaptAttributeService attributes, int form) {
    double scalar = combatScalar(form);
    if (Attributes.ATTACK_DAMAGE != null) {
      attributes.apply(p, getName(), SLOT_DAMAGE, Attributes.ATTACK_DAMAGE, scalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }
    if (Attributes.MAX_HEALTH != null) {
      attributes.apply(p, getName(), SLOT_HEALTH, Attributes.MAX_HEALTH, scalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }
  }

  private void reconcileForm(Player p) {
    FormState state = forms.get(p.getUniqueId());
    if (state == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      resetForm(p);
      return;
    }
    if (state.level() != level) {
      applyForm(p, state.form(), level);
      return;
    }

    boolean ownsEffect = ensureFormEffect(p, state);
    if (ownsEffect != state.ownsEffect()) {
      forms.replace(p.getUniqueId(), state, new FormState(state.form(), state.level(), ownsEffect));
    }
  }

  private boolean ensureFormEffect(Player p, FormState state) {
    PotionEffectType type = effectType(state.form());
    if (type == null) {
      return false;
    }

    PotionEffect active = p.getPotionEffect(type);
    if (active != null) {
      if (!state.ownsEffect() || !isOwnedFormEffect(active, type)) {
        return false;
      }
      if (active.getDuration() > FORM_EFFECT_REFRESH_TICKS) {
        return true;
      }
      p.removePotionEffect(type);
    }

    PotionEffect formEffect = new PotionEffect(type, FORM_EFFECT_DURATION_TICKS, 0, true, false, true);
    return p.addPotionEffect(formEffect, false);
  }

  private void resetForm(Player p) {
    FormState state = forms.remove(p.getUniqueId());
    clearOwnedEffect(p, state);
    AdaptAttributeService.get().removeAll(p, getName());
    clampHealth(p);
  }

  private void clearOwnedEffect(Player p, FormState state) {
    if (state == null || !state.ownsEffect()) {
      return;
    }

    PotionEffectType type = effectType(state.form());
    PotionEffect active = type == null ? null : p.getPotionEffect(type);
    if (active != null && isOwnedFormEffect(active, type)) {
      p.removePotionEffect(type);
    }
  }

  private boolean isOwnedFormEffect(PotionEffect effect, PotionEffectType expectedType) {
    return effect.getType().equals(expectedType)
        && isOwnedFormEffectSignature(effect.getAmplifier(), effect.getDuration(), effect.isAmbient(),
            effect.hasParticles(), effect.hasIcon());
  }

  static boolean isOwnedFormEffectSignature(
      int amplifier,
      int duration,
      boolean ambient,
      boolean particles,
      boolean icon
  ) {
    return amplifier == 0
        && duration > 0
        && duration <= FORM_EFFECT_DURATION_TICKS
        && ambient
        && !particles
        && icon;
  }

  static FormMovementEffect movementEffect(int form) {
    return switch (form) {
      case FORM_TITAN -> FormMovementEffect.SLOWNESS;
      case FORM_POCKET -> FormMovementEffect.SPEED;
      default -> FormMovementEffect.NONE;
    };
  }

  private PotionEffectType effectType(int form) {
    return switch (movementEffect(form)) {
      case SLOWNESS -> PotionEffectType.SLOWNESS;
      case SPEED -> PotionEffectType.SPEED;
      case NONE -> null;
    };
  }

  private void clampHealth(Player p) {
    AttributeInstance maxHealth = Attributes.MAX_HEALTH == null ? null : p.getAttribute(Attributes.MAX_HEALTH);
    if (maxHealth != null && p.getHealth() > maxHealth.getValue()) {
      p.setHealth(maxHealth.getValue());
    }
  }

  private void playFormFeedback(Player p, int form) {
    if (form == FORM_TITAN) {
      fx(p, FxPriority.GAMEPLAY)
          .ring(Particle.CLOUD, 0.9D, 12, 0.03D)
          .sound(Sound.ENTITY_IRON_GOLEM_REPAIR, 0.7F, 0.55F);
      return;
    }
    if (form == FORM_POCKET) {
      fx(p, FxPriority.GAMEPLAY)
          .ring(Particle.CLOUD, 0.45D, 8, 0.03D)
          .sound(Sound.ENTITY_RABBIT_JUMP, 0.6F, 1.6F);
      return;
    }
    fx(p, FxPriority.GAMEPLAY)
        .ring(Particle.CLOUD, 0.6D, 8, 0.02D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.2F);
  }

  private double getTitanScale(int level) {
    return getConfig().titanScaleBase + (getLevelPercent(level) * getConfig().titanScaleFactor);
  }

  private double getPocketScale(int level) {
    return getConfig().pocketScaleBase + (getLevelPercent(level) * getConfig().pocketScaleFactor);
  }

  private record FormState(int form, int level, boolean ownsEffect) {
  }

  enum FormMovementEffect {
    NONE,
    SLOWNESS,
    SPEED
  }

  @ConfigDescription("Sneak and swap hands while looking up for Titan form, down for Pocket form, or level to return to normal.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base size increase of the titan form.", impact = "Higher values make titan form larger.")
    double titanScaleBase = 0.25;
    @ConfigDoc(value = "Additional titan size increase gained at maximum level.", impact = "Higher values make max-level titan form larger.")
    double titanScaleFactor = 0.35;
    @ConfigDoc(value = "Base size decrease of the pocket form.", impact = "Higher values make pocket form smaller.")
    double pocketScaleBase = 0.25;
    @ConfigDoc(value = "Additional pocket size decrease gained at maximum level.", impact = "Higher values make max-level pocket form smaller.")
    double pocketScaleFactor = 0.25;

    public Config() {
      baseCost = 6;
      costFactor = 0.45;
      maxLevel = 3;
      initialCost = 5;
    }
  }
}
