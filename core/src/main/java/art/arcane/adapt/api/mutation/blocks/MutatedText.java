package art.arcane.adapt.api.mutation.blocks;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

/**
 * Composes mutation names and descriptions from the parts in {@link MutatedMessages}, in English or in the viewer's
 * language. An override row, when the pair has one, replaces the composed description.
 */
public final class MutatedText {
  /** The catalog's English text of each part. */
  public static final Words ENGLISH = TextKey::english;
  /** Each part in the language of the player the text is being rendered for. */
  public static final Words VIEWER = AdaptLanguage::template;

  private MutatedText() {
  }

  /** Reads the raw text of one part. */
  @FunctionalInterface
  public interface Words {
    String text(TextKey key);
  }

  /** The mutation's name: the partner's adjective with the lead's noun, such as Silent Duelist. */
  public static String mutationName(Words words, String leadId, String partnerId) {
    return words.text(MutatedMessages.MUTATION_NAME)
        .replace("{adjective}", words.text(MutatedMessages.adjective(partnerId)))
        .replace("{noun}", words.text(MutatedMessages.noun(leadId)));
  }

  /** What a mutated skill is: its two parents and which one leads. */
  public static String skillDescription(Words words, String leadId, String partnerId) {
    return words.text(MutatedMessages.SKILL)
        .replace("{lead}", words.text(MutatedMessages.skill(leadId)))
        .replace("{partner}", words.text(MutatedMessages.skill(partnerId)));
  }

  public static String adaptationName(Words words, String partnerId, Recipe recipe) {
    return words.text(MutatedMessages.ADAPTATION_NAME)
        .replace("{adjective}", words.text(MutatedMessages.adjective(partnerId)))
        .replace("{recipe}", words.text(MutatedMessages.recipe(recipe)));
  }

  public static String blockName(Words words, BlockRef ref) {
    return words.text(MutatedMessages.BLOCK_NAME)
        .replace("{skill}", words.text(MutatedMessages.skill(ref.skillId())))
        .replace("{slot}", words.text(MutatedMessages.slot(ref.slot())));
  }

  /** The description players see: the override row when the pair has one, otherwise the composed text. */
  public static String description(Words words, SkillBlocks lead, SkillBlocks partner, Recipe recipe) {
    MutationOverride override = MutationOverrides.find(lead.skillId(), partner.skillId(), recipe);
    return override == null ? generated(words, lead, partner, recipe) : override(words, override);
  }

  /** The name players see: the signature's when the pair has one for the recipe, otherwise the composed name. */
  public static String name(Words words, String leadId, String partnerId, Recipe recipe) {
    MutationOverride override = MutationOverrides.find(leadId, partnerId, recipe);
    return override != null && override.isSignature() ? signatureName(words, override)
        : adaptationName(words, partnerId, recipe);
  }

  /**
   * The name on the mutated skill's own pages: the recipe alone, since the skill's name already carries the partner's
   * adjective; null for a signature, whose name is its own.
   */
  public static @Nullable String shortName(Words words, String leadId, String partnerId, Recipe recipe) {
    MutationOverride override = MutationOverrides.find(leadId, partnerId, recipe);
    return override != null && override.isSignature() ? null : words.text(MutatedMessages.recipe(recipe));
  }

  /** A signature row's own name. */
  public static String signatureName(Words words, MutationOverride signature) {
    return words.text(MutatedMessages.signatureName(signature));
  }

  public static String override(Words words, MutationOverride override) {
    return words.text(MutatedMessages.override(override))
        .replace("{gesture}", gesture(words, override.leadId(), override.partnerId()));
  }

  /** The description exactly as the recipe composes it, before overrides. */
  public static String generated(Words words, SkillBlocks lead, SkillBlocks partner, Recipe recipe) {
    return switch (recipe) {
      case GRAFT -> triggered(words, lead, partner);
      case ECHO -> triggered(words, partner, lead);
      case BARTER -> words.text(MutatedMessages.BARTER)
          .replace("{gesture}", gesture(words, lead.skillId(), partner.skillId()))
          .replace("{fuel}", phrase(words, partner, BlockSlot.FUEL))
          .replace("{boon}", phrase(words, lead, BlockSlot.BOON));
      case ATTUNEMENT -> words.text(MutatedMessages.ATTUNEMENT)
          .replace("{condition}", phrase(words, lead, BlockSlot.STATE))
          .replace("{boon}", phrase(words, partner, BlockSlot.BOON));
      case CONVERGENCE -> words.text(MutatedMessages.CONVERGENCE)
          .replace("{trigger}", phrase(words, lead, BlockSlot.TRIGGER))
          .replace("{condition}", phrase(words, partner, BlockSlot.STATE))
          .replace("{strike}", strike(words, lead, lead))
          .replace("{also}", strike(words, lead, partner))
          .replace("{seconds}", String.valueOf(recipe.price().cooldownSeconds()));
    };
  }

  private static String triggered(Words words, SkillBlocks trigger, SkillBlocks source) {
    return words.text(MutatedMessages.TRIGGERED)
        .replace("{trigger}", phrase(words, trigger, BlockSlot.TRIGGER))
        .replace("{strike}", strike(words, trigger, source));
  }

  private static String strike(Words words, SkillBlocks trigger, SkillBlocks source) {
    return switch (trigger.triggerKind()) {
      case FIGHT -> phrase(words, source, BlockSlot.STRIKE_FIGHT);
      case WORK -> phrase(words, source, BlockSlot.STRIKE_WORK);
      case EITHER -> words.text(MutatedMessages.EITHER)
          .replace("{fight}", phrase(words, source, BlockSlot.STRIKE_FIGHT))
          .replace("{work}", phrase(words, source, BlockSlot.STRIKE_WORK));
    };
  }

  private static String gesture(Words words, String leadId, String partnerId) {
    return words.text(MutatedMessages.gesture(BarterGesture.of(leadId, partnerId)));
  }

  private static String phrase(Words words, SkillBlocks skill, BlockSlot slot) {
    return words.text(MutatedMessages.phrase(skill.ref(slot)));
  }
}
