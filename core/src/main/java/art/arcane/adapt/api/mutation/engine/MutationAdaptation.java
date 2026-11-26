package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.blocks.MutationPrice;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One of the five adaptations a mutation owns (Graft, Echo, Barter, Attunement or Convergence), built by its
 * {@link MutatedSkill} from the composed recipe and the parents' registered blocks. It is learned on the mutated line,
 * holds one power slot, and is inert for anyone who does not hold its mutation. It has no listeners of its own:
 * {@link MutationEngine} runs it when one of its blocks fires.
 */
public final class MutationAdaptation extends SimpleAdaptation<MutationAdaptation.Config> {
  private final SkillMutation mutation;
  private final MutatedAdaptation recipe;
  private final List<MutationBlock<?>> blocks;
  private final @Nullable Signature<?> signature;

  public MutationAdaptation(MutatedSkill skill, MutatedAdaptation recipe, List<MutationBlock<?>> blocks) {
    this(skill, recipe, blocks, null);
  }

  /** The adaptation a signature replaces: no blocks, and {@code signature} is its effect. */
  public MutationAdaptation(MutatedSkill skill, MutatedAdaptation recipe, Signature<?> signature) {
    this(skill, recipe, List.of(), signature);
  }

  private MutationAdaptation(MutatedSkill skill, MutatedAdaptation recipe, List<MutationBlock<?>> blocks,
                             @Nullable Signature<?> signature) {
    super(id(skill.getMutation(), recipe.recipe()));
    this.mutation = skill.getMutation();
    this.recipe = recipe;
    this.blocks = List.copyOf(blocks);
    this.signature = signature;
    setSkill(skill);
    MutationPrice price = recipe.price();
    setMaxLevel(price.maxLevel());
    setBaseCost(price.baseCost());
    setInitialCost(price.initialCost());
    setCostFactor(price.costFactor());
    registerConfiguration(Config.class);
    setConfig(Config.of(recipe.recipe()));
    setIcon(signature != null ? signature.getIcon() : icon(recipe.recipe()));
  }

  /** The id of a mutation's adaptation, such as {@code mutated-swords-herbalism-graft}. */
  public static String id(SkillMutation mutation, Recipe recipe) {
    return mutation.id() + "-" + recipe.name().toLowerCase(Locale.ROOT);
  }

  /** The mutation and recipe an id names, or null when it is not a mutated adaptation id. */
  public static @Nullable Key parse(String id) {
    if (id == null) {
      return null;
    }
    for (Recipe recipe : Recipe.values()) {
      String suffix = "-" + recipe.name().toLowerCase(Locale.ROOT);
      if (id.endsWith(suffix)) {
        SkillMutation mutation = SkillMutation.parse(id.substring(0, id.length() - suffix.length()));
        return mutation == null ? null : new Key(mutation, recipe);
      }
    }
    return null;
  }

  public record Key(SkillMutation mutation, Recipe recipe) {
  }

  public SkillMutation getMutation() {
    return mutation;
  }

  public Recipe getRecipe() {
    return recipe.recipe();
  }

  /** The composed recipe with overrides applied: blocks, Strike form and State window. */
  public MutatedAdaptation getRecipeData() {
    return recipe;
  }

  /** The signature that is this adaptation's effect, or null for one built from blocks. */
  public @Nullable Signature<?> getSignature() {
    return signature;
  }

  @Override
  protected String localizedName() {
    return AdaptLanguage.render(MutatedText.name(MutatedText.VIEWER, mutation.lead(), mutation.partner(), recipe.recipe()));
  }

  @Override
  protected String localizedShortName() {
    String shortName = MutatedText.shortName(MutatedText.VIEWER, mutation.lead(), mutation.partner(), recipe.recipe());
    return shortName == null ? null : AdaptLanguage.render(shortName);
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.render(MutatedText.description(MutatedText.VIEWER, SkillBlockCatalog.require(mutation.lead()),
        SkillBlockCatalog.require(mutation.partner()), recipe.recipe()));
  }

  /**
   * The enable switch, then the signature's or each block's own preferences, so their choices are set on the adaptation
   * they run in.
   */
  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    Set<PlayerPreference<?>> preferences = new LinkedHashSet<>();
    preferences.add(CommonPreferences.ENABLED);
    if (signature != null) {
      preferences.addAll(signature.getPlayerPreferences());
    }
    for (MutationBlock<?> block : blocks) {
      preferences.addAll(block.getPlayerPreferences());
    }
    return List.copyOf(preferences);
  }

  /** The resolved blocks, in the order of {@link MutatedAdaptation#blocks()}. */
  public List<MutationBlock<?>> getBlocks() {
    return blocks;
  }

  public boolean uses(MutationBlock<?> block) {
    for (MutationBlock<?> candidate : blocks) {
      if (candidate == block) {
        return true;
      }
    }
    return false;
  }

  public <B extends MutationBlock<?>> List<B> blocks(Class<B> type) {
    List<B> matching = new ArrayList<>();
    for (MutationBlock<?> block : blocks) {
      if (type.isInstance(block)) {
        matching.add(type.cast(block));
      }
    }
    return matching;
  }

  public List<StrikeBlock<?>> strikes(BlockSlot slot) {
    List<StrikeBlock<?>> matching = new ArrayList<>();
    for (MutationBlock<?> block : blocks) {
      if (block instanceof StrikeBlock<?> strike && block.getSlot() == slot) {
        matching.add(strike);
      }
    }
    return matching;
  }

  /** How long after a run this adaptation waits: its signature's cooldown, or the recipe's. */
  public long getCooldownMillis(int level) {
    return signature == null ? getCooldownMillisForRecipe(level) : signature.cooldownMillis(this, level);
  }

  /** The recipe's cooldown: by level for Graft and Echo, fixed for Convergence and Barter, none for Attunement. */
  public long getCooldownMillisForRecipe(int level) {
    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    return switch (recipe.recipe()) {
      case GRAFT, ECHO -> {
        double power = getMaxLevel() <= 1 ? 1D : Math.min(1D, Math.max(0D, (level - 1D) / (getMaxLevel() - 1D)));
        yield Math.round(config.getTriggerCooldownMillisAtFirstLevel()
            + ((config.getTriggerCooldownMillisAtTopLevel() - config.getTriggerCooldownMillisAtFirstLevel()) * power));
      }
      case CONVERGENCE -> config.getConvergenceCooldownMillis();
      case BARTER -> config.getBarterCooldownMillis();
      case ATTUNEMENT -> 0L;
    };
  }

  @Override
  public List<PlatformNeed> getPlatformNeeds() {
    Set<PlatformNeed> needs = new LinkedHashSet<>();
    for (MutationBlock<?> block : blocks) {
      needs.addAll(block.getPlatformNeeds());
    }
    if (signature != null) {
      needs.addAll(signature.getPlatformNeeds());
    }
    return List.copyOf(needs);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.GREEN, "+ ", Form.pc(level / (double) getMaxLevel(), 0), MutatedMessages.STAT_STRENGTH);
    if (signature != null) {
      signature.addSignatureStats(level, v);
      long cooldown = getCooldownMillis(level);
      if (cooldown > 0L) {
        statLore(v, C.YELLOW, "* ", Form.duration(cooldown, 1), MutatedMessages.STAT_COOLDOWN);
      }
      return;
    }
    switch (recipe.recipe()) {
      case GRAFT, ECHO, CONVERGENCE, BARTER ->
          statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), MutatedMessages.STAT_COOLDOWN);
      case ATTUNEMENT ->
          statLore(v, C.YELLOW, "* ", Form.duration(AdaptConfig.get().getMutations().getAttunementIntervalMillis(), 1), MutatedMessages.STAT_INTERVAL);
    }
  }

  private static Id icon(Recipe recipe) {
    return switch (recipe) {
      case GRAFT -> Id.minecraft("vine");
      case ECHO -> Id.minecraft("amethyst_shard");
      case BARTER -> Id.minecraft("emerald");
      case ATTUNEMENT -> Id.minecraft("compass");
      case CONVERGENCE -> Id.minecraft("nether_star");
    };
  }

  /** Pricing of a recipe from {@link MutationPrice}; the engine's timing is in {@link AdaptConfig.Mutations}. */
  public static final class Config extends AdaptationConfig {
    static Config of(Recipe recipe) {
      Config config = new Config();
      MutationPrice price = recipe.price();
      config.baseCost = price.baseCost();
      config.initialCost = price.initialCost();
      config.costFactor = price.costFactor();
      config.maxLevel = price.maxLevel();
      return config;
    }
  }
}
