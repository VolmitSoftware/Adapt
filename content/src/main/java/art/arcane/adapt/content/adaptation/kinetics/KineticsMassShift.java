package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Comet;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KineticsMassShift extends SimpleAdaptation<KineticsMassShift.Config> {
  public static final Id ID = Id.adapt("kinetics/mass_shift");
  public static final PlayerPreference<Selection> SELECTION = PlayerPreference.of("selection", Selection.class, Selection.LOOK, List.of(
          new PlayerPreference.Choice<>(Selection.LOOK, Text.key("adapt.kineticsmassshift.preferences.preference_selection_look"), Id.minecraft("spyglass"), 1),
          new PlayerPreference.Choice<>(Selection.CYCLE, Text.key("adapt.kineticsmassshift.preferences.preference_selection_cycle"), Id.minecraft("compass"), 1))).labelled(Text.key("adapt.kineticsmassshift.preferences.preference_selection"));

  public static final int FORM_NORMAL = 0;
  public static final int FORM_TITAN = 1;
  public static final int FORM_POCKET = 2;
  static final double FORM_COMBAT_SCALAR = 0.2D;
  public static final float LOOK_PITCH_THRESHOLD = 25F;
  private static final String SLOT_DAMAGE = "form-damage";
  private static final String SLOT_HEALTH = "form-health";
  private static final int FORM_EFFECT_DURATION_TICKS = 60;
  private static final int FORM_EFFECT_REFRESH_TICKS = 30;
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id SPEED = Id.minecraft("speed");

  private final Map<UUID, FormState> forms = playerState();
  private final KineticsFormShape shape = new KineticsFormShape(this);

  public KineticsMassShift() {
    super(ID);
    needs(PlatformNeed.nativeAttribute(Attributes.SCALE));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(RECONCILE_INTERVAL_MS);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SELECTION);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    resetForm(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getTitanScale(level), 2), 1);
    statLore(v, Format.f(getPocketScale(level), 2), 2);
    statLore(v, Format.pc(FORM_COMBAT_SCALAR, 0), 3);
    statLore(v, TextFormat.RED, "- ", Format.pc(FORM_COMBAT_SCALAR, 0), 4);
    v.addLore(TextFormat.YELLOW + "* " + TextFormat.GRAY + Text.key("adapt.kinetics.mass_shift.lore5").resolve());
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    forms.replaceAll((id, state) -> new FormState(state.form(), -1, state.ownsEffect()));
  }


  @Override
  public boolean hasTickDemand() {
    return !forms.isEmpty();
  }

  @Override
  public void onTick() {
    Set<UUID> activeForms = new HashSet<>(forms.keySet());
    for (UUID id : activeForms) {
      PlayerRef player = AdaptApi.platform().host().player(id);
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

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || Comet.covers(this, p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    e.setCancelled(true);
    FormState current = forms.get(p.uuid());
    int next = current == null ? FORM_TITAN : current.form() == FORM_TITAN ? FORM_POCKET : FORM_NORMAL;
    applyForm(p, preference(p, SELECTION) == Selection.CYCLE ? next : formForPitch(p.location().pitch(), LOOK_PITCH_THRESHOLD), level);
  }

  /** Drops the player's form, so a Comet form never stacks on it. */
  public void release(PlayerRef p) {
    if (forms.containsKey(p.uuid())) {
      resetForm(p);
    }
  }

  /**
   * Whether sneak and swap hands shifts this player's form, through Mass Shift or Comet, so another ability on that
   * gesture must leave it.
   */
  public static boolean claimsSwap(PlayerRef player) {
    Adaptation<?> found = AdaptApi.isRunning() ? AdaptApi.content().adaptation(ID).orElse(null) : null;
    return found instanceof KineticsMassShift shift && (shift.getActiveLevel(player) > 0 || Comet.covers(shift, player));
  }

  public static KineticsMassShift of(Skill<?> skill) {
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof KineticsMassShift found) {
        return found;
      }
    }
    return null;
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    resetForm(e.player());
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerDeathEvent e) {
    resetForm(e.player());
  }

  /** The form a sneak-swap asks for: Titan looking up, Pocket looking down, normal looking level. */
  public static int formForPitch(float pitch, float threshold) {
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

  private void applyForm(PlayerRef p, int form, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    FormState previous = forms.remove(p.uuid());
    clearOwnedEffect(p, previous);
    attributes.removeAll(p, getId().toString());
    if (form == FORM_NORMAL) {
      HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
      clampHealth(p);
      playFormFeedback(p, form);
      return;
    }

    applyCombatAttributes(p, attributes, form);
    shape.shape(p, form, form == FORM_TITAN ? getTitanScale(level) : getPocketScale(level));

    FormState initialState = new FormState(form, level, false);
    boolean ownsEffect = ensureFormEffect(p, initialState);
    forms.put(p.uuid(), new FormState(form, level, ownsEffect));
    HudStatus.toggle(p.uuid(), this, true);
    clampHealth(p);
    playFormFeedback(p, form);
  }

  private void applyCombatAttributes(PlayerRef p, AdaptAttributeService attributes, int form) {
    double scalar = combatScalar(form);
    attributes.apply(p, getId().toString(), SLOT_DAMAGE, Attributes.ATTACK_DAMAGE, scalar, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    attributes.apply(p, getId().toString(), SLOT_HEALTH, Attributes.MAX_HEALTH, scalar, AttributeOperation.ADD_MULTIPLIED_TOTAL);
  }

  private void reconcileForm(PlayerRef p) {
    FormState state = forms.get(p.uuid());
    if (state == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || Comet.covers(this, p)) {
      resetForm(p);
      return;
    }
    if (state.level() != level) {
      applyForm(p, state.form(), level);
      return;
    }

    boolean ownsEffect = ensureFormEffect(p, state);
    if (ownsEffect != state.ownsEffect()) {
      forms.replace(p.uuid(), state, new FormState(state.form(), state.level(), ownsEffect));
    }
  }

  private boolean ensureFormEffect(PlayerRef p, FormState state) {
    Id type = effectType(state.form());
    if (type == null) {
      return false;
    }

    EffectInstance active = p.effect(type);
    if (active != null) {
      if (!state.ownsEffect() || !isOwnedFormEffect(active, type)) {
        return false;
      }
      if (active.durationTicks() > FORM_EFFECT_REFRESH_TICKS) {
        return true;
      }
      p.removeEffect(type);
    }

    EffectInstance formEffect = EffectInstance.of(type, FORM_EFFECT_DURATION_TICKS, 0).withAmbient(true).withParticles(false);
    return p.addEffect(formEffect);
  }

  private void resetForm(PlayerRef p) {
    FormState state = forms.remove(p.uuid());
    clearOwnedEffect(p, state);
    HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
    AdaptAttributeService.get().removeAll(p, getId().toString());
    clampHealth(p);
  }

  private void clearOwnedEffect(PlayerRef p, FormState state) {
    if (state == null || !state.ownsEffect()) {
      return;
    }

    Id type = effectType(state.form());
    EffectInstance active = type == null ? null : p.effect(type);
    if (active != null && isOwnedFormEffect(active, type)) {
      p.removeEffect(type);
    }
  }

  private boolean isOwnedFormEffect(EffectInstance effect, Id expectedType) {
    return effect.type().equals(expectedType)
        && isOwnedFormEffectSignature(effect.amplifier(), effect.durationTicks(), effect.ambient(),
            effect.particles(), effect.icon());
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

  private Id effectType(int form) {
    return switch (movementEffect(form)) {
      case SLOWNESS -> SLOWNESS;
      case SPEED -> SPEED;
      case NONE -> null;
    };
  }

  private void clampHealth(PlayerRef p) {
    if (!p.hasAttribute(Attributes.MAX_HEALTH)) {
      return;
    }
    double maxHealth = p.attributeValue(Attributes.MAX_HEALTH);
    if (p.health() > maxHealth) {
      p.setHealth(maxHealth);
    }
  }

  private void playFormFeedback(PlayerRef p, int form) {
    if (form == FORM_TITAN) {
      fx(p, FxPriority.GAMEPLAY)
          .ring(Particles.CLOUD, 0.9D, 12, 0.03D)
          .sound(Sounds.ENTITY_IRON_GOLEM_REPAIR, 0.7F, 0.55F);
      return;
    }
    if (form == FORM_POCKET) {
      fx(p, FxPriority.GAMEPLAY)
          .ring(Particles.CLOUD, 0.45D, 8, 0.03D)
          .sound(Sounds.ENTITY_RABBIT_JUMP, 0.6F, 1.6F);
      return;
    }
    fx(p, FxPriority.GAMEPLAY)
        .ring(Particles.CLOUD, 0.6D, 8, 0.02D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.2F);
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
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 3;
    }
  }

  public enum Selection { LOOK, CYCLE }
}
