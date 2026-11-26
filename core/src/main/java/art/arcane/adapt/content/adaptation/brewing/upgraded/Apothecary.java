package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.content.adaptation.brewing.BrewingBrews;
import art.arcane.adapt.content.adaptation.brewing.BrewingMixer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BrewingMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bottled Absorption, Life, Resistance and Saturation fused: one unlock brews all four and the Haste brews, plus an
 * elixir that carries the base effect of each defensive brew and lasts longer the higher the brewer's level. Drinkable
 * potions mix into one potion with every effect.
 */
public class Apothecary extends SkillUpgrade<Apothecary.Config> implements BrewingMixer.Mixes {
  public static final String ELIXIR_RECIPE = "upgraded-brewing-apothecary-elixir";
  private static final Rgb ELIXIR_COLOR = Rgb.of(0xE0B040);
  private static final Rgb MIX_COLOR = Rgb.of(0xC8E05A);
  private static final Id POTION = Id.minecraft("potion");
  private final BrewingMixer mixer = new BrewingMixer(this, this::mixRules, this::mixed);
  private volatile BrewingRecipe elixir;

  public Apothecary() {
    super("upgraded-brewing-apothecary");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_apple"));
  }

  @Override
  public List<BrewingRecipe> getBrewingRecipes() {
    List<BrewingRecipe> defensive = BrewingBrews.recipesOf(getSkill(), BrewingBrews.DEFENSIVE);
    if (defensive.isEmpty()) {
      return defensive;
    }
    List<BrewingRecipe> recipes = new ArrayList<>(defensive);
    recipes.addAll(BrewingBrews.recipesOf(getSkill(), BrewingBrews.HASTE));
    recipes.add(elixir());
    return List.copyOf(recipes);
  }

  @Override
  public boolean isBrewingRecipe(String recipeId) {
    return getBrewingRecipes().stream().anyMatch((BrewingRecipe recipe) -> recipe.getId().equals(recipeId));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, BrewingBrews.DEFENSIVE.size() + BrewingBrews.HASTE.size(), 1);
    statLore(v, Form.pc(getElixirDuration(level), 0), 2);
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

  /** The elixir recipe, carrying the base brew effect of each defensive adaptation. */
  public BrewingRecipe elixir() {
    BrewingRecipe built = elixir;
    if (built == null) {
      synchronized (this) {
        built = elixir;
        if (built == null) {
          PotionBuilder potion = PotionBuilder.of(PotionBuilder.Type.REGULAR)
              .setName(AdaptLanguage.text(BrewingMessages.APOTHECARY_ELIXIR_NAME))
              .setColor(ELIXIR_COLOR);
          for (EffectInstance effect : BrewingBrews.effectsOf(BrewingBrews.baseRecipesOf(getSkill(), BrewingBrews.DEFENSIVE))) {
            potion.addEffect(effect);
          }
          built = BrewingRecipe.builder()
              .id(ELIXIR_RECIPE)
              .brewingTime(400)
              .fuelCost(32)
              .ingredient(Id.minecraft("golden_carrot"))
              .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("strong_healing")))
              .result(potion.build())
              .build();
          elixir = built;
        }
      }
    }
    return built;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (e.brewerId() == null || !isBrewingRecipe(e.recipeId())) {
      return;
    }
    PlayerRef brewer = AdaptCore.platform().host().player(e.brewerId());
    int level = brewer == null ? 0 : getActiveLevel(brewer);
    if (level <= 0) {
      return;
    }
    if (e.recipeId().equals(ELIXIR_RECIPE)) {
      lengthenElixirs(e, level);
    }
    cue(brewer, MutationCue.WORK);
  }

  private void lengthenElixirs(AdaptBrewCompleteEvent e, int level) {
    Optional<BrewingStandRef> found = e.block().brewingStand();
    if (found.isEmpty()) {
      return;
    }
    BrewingStandRef stand = found.get();
    ItemRef brewed = elixir().getResult();
    double multiplier = getElixirDuration(level);
    for (int slot = 0; slot < BrewingStandRef.BOTTLE_SLOTS; slot++) {
      ItemRef bottle = stand.bottle(slot);
      Optional<PotionContents> contents = bottle.potionContents();
      if (bottle.isEmpty() || !bottle.isSimilar(brewed) || contents.isEmpty()) {
        continue;
      }
      List<EffectInstance> lengthened = new ArrayList<>();
      for (EffectInstance effect : contents.get().customEffects()) {
        lengthened.add(new EffectInstance(effect.type(), (int) Math.max(1D, effect.durationTicks() * multiplier),
            effect.amplifier(), effect.ambient(), effect.particles(), effect.icon()));
      }
      PotionContents potion = contents.get();
      bottle.setPotionContents(new PotionContents(potion.potion(), potion.color(), lengthened, potion.name()));
      stand.setBottle(slot, bottle);
    }
    getServer().addStat(e.brewerId(), "brewing.apothecary.elixirs-brewed", e.brewedPotions());
    fx(e.block().location().add(0.5D, 0.6D, 0.5D), FxPriority.TRANSITION)
        .dustRing(ELIXIR_COLOR, 0.7D, 14, 1.0F)
        .particle(Particles.HAPPY_VILLAGER, 6, 0, 0.3D, 0, 0.25D, 0.02D);
  }

  @Override
  public BrewingMixer.Rules mixRules(int level) {
    return new BrewingMixer.Rules(POTION, getMixEffects(level), getMixDuration(level), getConfig().mixBrewTicks,
        AdaptLanguage.text(BrewingMessages.APOTHECARY_MIX_NAME), MIX_COLOR, false);
  }

  int getMixEffects(int level) {
    return level >= getMaxLevel() ? getConfig().mixEffectsTopLevel : getConfig().mixEffects;
  }

  double getMixDuration(int level) {
    return getConfig().mixDurationBase + (level * getConfig().mixDurationPerLevel);
  }

  private void mixed(PlayerRef brewer, BlockRef block) {
    getServer().addStat(brewer.uuid(), "brewing.apothecary.potions-mixed", 1);
    fx(block.location().add(0.5D, 0.6D, 0.5D), FxPriority.TRANSITION)
        .dustRing(MIX_COLOR, 0.6D, 12, 1.0F)
        .particle(Particles.HAPPY_VILLAGER, 6, 0, 0.3D, 0, 0.25D, 0.02D);
    cue(brewer, MutationCue.WORK);
  }

  double getElixirDuration(int level) {
    return getConfig().elixirDurationBase + (level * getConfig().elixirDurationPerLevel);
  }

  @ConfigDescription("Brew all four defensive potions, the Haste brews and an elixir, and mix drinkable potions.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Elixir duration multiplier before level scaling.", impact = "Higher values make every elixir last longer.")
    double elixirDurationBase = 0.75;
    @ConfigDoc(value = "Elixir duration multiplier added per level.", impact = "Higher values reward higher levels more.")
    double elixirDurationPerLevel = 0.25;
    @ConfigDoc(value = "Effects one mixed potion may carry below the top level.", impact = "Higher values let more potions go into one mix.")
    int mixEffects = 2;
    @ConfigDoc(value = "Effects one mixed potion may carry at the top level.", impact = "Higher values let more potions go into one mix at the top level.")
    int mixEffectsTopLevel = 3;
    @ConfigDoc(value = "Share of each effect's duration a mix keeps before level scaling.", impact = "Higher values make mixed effects last longer.")
    double mixDurationBase = 0.5;
    @ConfigDoc(value = "Share of each effect's duration a mix keeps, added per level.", impact = "Higher values reward higher levels more.")
    double mixDurationPerLevel = 0.1;
    @ConfigDoc(value = "Ticks a mix takes to brew.", impact = "Lower values finish every mix sooner.")
    int mixBrewTicks = 400;
  }
}
