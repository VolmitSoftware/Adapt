package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.content.adaptation.brewing.BrewingBrews;
import art.arcane.adapt.content.adaptation.brewing.BrewingMixer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BrewingMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bottled Blindness, Darkness, Decay, Fatigue, Hunger and Nausea fused: one unlock brews all six, a hostile brew you
 * throw reaches farther than a normal splash, and splash potions mix into one splash with every effect.
 */
public class WitchsShelf extends SkillUpgrade<WitchsShelf.Config> implements BrewingMixer.Mixes {
  private static final double VANILLA_SPLASH_RADIUS = 4D;
  private static final Rgb HEX = Rgb.of(0x4B2D6B);
  private static final Id SPLASH_POTION = Id.minecraft("splash_potion");
  private final BrewingMixer mixer = new BrewingMixer(this, this::mixRules, this::mixed);

  public WitchsShelf() {
    super("upgraded-brewing-witchs-shelf");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fermented_spider_eye"));
  }

  @Override
  public List<BrewingRecipe> getBrewingRecipes() {
    return BrewingBrews.recipesOf(getSkill(), BrewingBrews.HOSTILE);
  }

  @Override
  public boolean isBrewingRecipe(String recipeId) {
    return getBrewingRecipes().stream().anyMatch((BrewingRecipe recipe) -> recipe.getId().equals(recipeId));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, BrewingBrews.HOSTILE.size(), 1);
    statLore(v, Form.f(getSplashRadius(level), 1), 2);
    statLore(v, getMixEffects(level), 3);
    statLore(v, Form.pc(getMixDuration(level), 0), 4);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    mixer.watch(e);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(BrewEvent e) {
    mixer.intercept(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (e.brewerId() == null || !isBrewingRecipe(e.recipeId())) {
      return;
    }
    PlayerRef brewer = AdaptCore.platform().host().player(e.brewerId());
    if (brewer != null && getActiveLevel(brewer) > 0) {
      cue(brewer, MutationCue.WORK);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (e.isCancelled() || !(e.potion().shooter() instanceof PlayerRef thrower)) {
      return;
    }
    int level = getActiveLevel(thrower);
    if (level <= 0) {
      return;
    }
    Set<Id> hostile = BrewingBrews.effectTypesOf(getBrewingRecipes());
    List<EffectInstance> effects = new ArrayList<>();
    for (EffectInstance effect : e.effects()) {
      if (hostile.contains(effect.type())) {
        effects.add(effect);
      }
    }
    if (effects.isEmpty()) {
      return;
    }

    double radius = getSplashRadius(level);
    Loc center = e.potion().location();
    int reached = 0;
    for (EntityRef entity : e.potion().nearbyEntities(radius, radius / 2D, radius)) {
      if (!(entity instanceof LivingRef target) || e.affected().containsKey(target)
          || target.uuid().equals(thrower.uuid()) || !canDamageTarget(thrower, target)) {
        continue;
      }
      double distance = target.location().distance(center);
      if (distance >= radius) {
        continue;
      }
      double intensity = 1D - (distance / radius);
      boolean applied = false;
      for (EffectInstance effect : effects) {
        int duration = (int) ((intensity * effect.durationTicks()) + 0.5D);
        if (duration > 20) {
          applied |= target.addEffect(new EffectInstance(effect.type(), duration, effect.amplifier(), effect.ambient(),
              effect.particles(), effect.icon()));
        }
      }
      if (applied) {
        reached++;
      }
    }
    if (reached <= 0) {
      return;
    }
    getServer().addStat(thrower.uuid(), "brewing.witchs-shelf.targets-reached", reached);
    fx(center, FxPriority.COMBAT)
        .dustRing(HEX, radius, 28, 1.2F)
        .particle(Particles.WITCH, 12, 0, 0.3D, 0, radius / 3D, 0.02D);
    cue(thrower, MutationCue.FIGHT);
  }

  @Override
  public BrewingMixer.Rules mixRules(int level) {
    return new BrewingMixer.Rules(SPLASH_POTION, getMixEffects(level), getMixDuration(level),
        getConfig().mixBrewTicks, AdaptLanguage.text(BrewingMessages.WITCHS_SHELF_MIX_NAME), HEX, true);
  }

  int getMixEffects(int level) {
    return level >= getMaxLevel() ? getConfig().mixEffectsTopLevel : getConfig().mixEffects;
  }

  double getMixDuration(int level) {
    return getConfig().mixDurationBase + (level * getConfig().mixDurationPerLevel);
  }

  private void mixed(PlayerRef brewer, BlockRef block) {
    getServer().addStat(brewer.uuid(), "brewing.witchs-shelf.splashes-mixed", 1);
    fx(block.location().add(0.5D, 0.6D, 0.5D), FxPriority.TRANSITION)
        .dustRing(HEX, 0.6D, 12, 1.0F)
        .particle(Particles.WITCH, 8, 0, 0.3D, 0, 0.25D, 0.02D);
    cue(brewer, MutationCue.WORK);
  }

  double getSplashRadius(int level) {
    return VANILLA_SPLASH_RADIUS + getConfig().extraRadiusBase + (level * getConfig().extraRadiusPerLevel);
  }

  @ConfigDescription("Brew all six hostile potions, your hostile splashes reach farther, and splash potions mix.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Blocks added to the splash radius before level scaling.", impact = "Higher values widen every hostile splash.")
    double extraRadiusBase = 0.5;
    @ConfigDoc(value = "Blocks added to the splash radius per level.", impact = "Higher values reward higher levels more.")
    double extraRadiusPerLevel = 1.0;
    @ConfigDoc(value = "Effects one mixed splash may carry below the top level.", impact = "Higher values let more potions go into one mix.")
    int mixEffects = 2;
    @ConfigDoc(value = "Effects one mixed splash may carry at the top level.", impact = "Higher values let more potions go into one mix at the top level.")
    int mixEffectsTopLevel = 3;
    @ConfigDoc(value = "Share of each effect's duration a mix keeps before level scaling.", impact = "Higher values make mixed effects last longer.")
    double mixDurationBase = 0.5;
    @ConfigDoc(value = "Share of each effect's duration a mix keeps, added per level.", impact = "Higher values reward higher levels more.")
    double mixDurationPerLevel = 0.1;
    @ConfigDoc(value = "Ticks a mix takes to brew.", impact = "Lower values finish every mix sooner.")
    int mixBrewTicks = 400;
  }
}
