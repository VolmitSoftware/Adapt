package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationPrice;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
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
  private final MutatedSkill skill;
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
    super(id(skill.getPair(), recipe.recipe()));
    this.skill = skill;
    this.recipe = recipe;
    this.blocks = List.copyOf(blocks);
    this.signature = signature;
    Set<PlatformNeed> needed = new LinkedHashSet<>();
    for (MutationBlock<?> block : this.blocks) {
      needed.addAll(block.getPlatformNeeds());
    }
    if (signature != null) {
      needed.addAll(signature.getPlatformNeeds());
    }
    needs(needed.toArray(new PlatformNeed[0]));
    registerConfiguration(Config.class);
    AdaptationHost.of(this).useFixedConfig(Config.of(recipe.recipe()));
    if (signature != null) {
      setIcon(signature.getIcon());
    } else {
      setIcon(icon(recipe.recipe()));
    }
  }

  /** The id of a mutation's adaptation, such as {@code adapt:mutated/swords/adapt/herbalism/graft}. */
  public static Id id(SkillPair pair, Recipe recipe) {
    return pair.child(recipe.name().toLowerCase(Locale.ROOT));
  }

  public SkillPair getPair() {
    return skill.getPair();
  }

  public MutationSlot getSlot() {
    return recipe.slot();
  }

  public Recipe getRecipe() {
    return recipe.recipe();
  }

  /** The composed recipe with the pair's claims applied: blocks, Strike form and State window. */
  public MutatedAdaptation getRecipeData() {
    return recipe;
  }

  /** The signature that is this adaptation's effect, or null for one built from blocks. */
  public @Nullable Signature<?> getSignature() {
    return signature;
  }

  @Override
  protected String localizedName() {
    return AdaptLanguage.render(MutatedText.name(MutatedText.VIEWER, skill.parents(), recipe.recipe()));
  }

  /** The short name of the mutated adaptation, or null when it has none. */
  public String mutatedShortName() {
    String shortName = MutatedText.shortName(MutatedText.VIEWER, skill.parents(), recipe.recipe());
    return shortName == null ? null : AdaptLanguage.render(shortName);
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.render(MutatedText.description(MutatedText.VIEWER, skill.parents(), recipe.recipe()));
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
    return signature == null ? recipeCooldownMillis(recipe.recipe(), level) : signature.cooldownMillis(level);
  }

  /** A recipe's cooldown: by level for Graft and Echo, fixed for Convergence and Barter, none for Attunement. */
  public static long recipeCooldownMillis(Recipe recipe, int level) {
    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    int maxLevel = recipe.price().maxLevel();
    return switch (recipe) {
      case GRAFT, ECHO -> {
        double power = maxLevel <= 1 ? 1D : Math.min(1D, Math.max(0D, (level - 1D) / (maxLevel - 1D)));
        yield Math.round(config.getTriggerCooldownMillisAtFirstLevel()
            + ((config.getTriggerCooldownMillisAtTopLevel() - config.getTriggerCooldownMillisAtFirstLevel()) * power));
      }
      case CONVERGENCE -> config.getConvergenceCooldownMillis();
      case BARTER -> config.getBarterCooldownMillis();
      case ATTUNEMENT -> 0L;
    };
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.GREEN, "+ ", Form.pc(level / (double) getMaxLevel(), 0), Text.key("adapt.mutated.stat.strength"));
    if (signature != null) {
      signature.addSignatureStats(level, v);
      long cooldown = getCooldownMillis(level);
      if (cooldown > 0L) {
        statLore(v, TextFormat.YELLOW, "* ", Form.duration(cooldown, 1), Text.key("adapt.mutated.stat.cooldown"));
      }
      return;
    }
    switch (recipe.recipe()) {
      case GRAFT, ECHO, CONVERGENCE, BARTER ->
          statLore(v, TextFormat.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), Text.key("adapt.mutated.stat.cooldown"));
      case ATTUNEMENT ->
          statLore(v, TextFormat.YELLOW, "* ", Form.duration(AdaptConfig.get().getMutations().getAttunementIntervalMillis(), 1), Text.key("adapt.mutated.stat.interval"));
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
