package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Composes mutation names and descriptions for any pair of skills, in English or in the viewer's language. The parts
 * are the templates in {@link MutatedMessages} and each parent's own words and phrases: its kit's, else its messages
 * by convention, else its name. A claim an installed addon made on the pair (a name, a signature or a text) replaces
 * the composed part it covers.
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

  /** The two parents of a mutation and the claims installed addons made on pairs. */
  public record Parents(Skill<?> lead, Skill<?> partner, PairRegistry claims) {
    public Parents {
      Objects.requireNonNull(lead, "lead");
      Objects.requireNonNull(partner, "partner");
      Objects.requireNonNull(claims, "claims");
    }

    public SkillPair pair() {
      return new SkillPair(lead.getId(), partner.getId());
    }

    public MutationSlot slot(Recipe recipe) {
      return new MutationSlot(pair(), recipe);
    }
  }

  /** The mutation's name: the name an addon gave the pair, else the partner's adjective with the lead's noun. */
  public static String mutationName(Words words, Parents parents) {
    Optional<PairRegistry.Claim<Text>> claimed = parents.claims().name(parents.pair());
    if (claimed.isPresent()) {
      return text(words, claimed.get().value());
    }
    return words.text(MutatedMessages.MUTATION_NAME)
        .replace("{adjective}", adjective(words, parents.partner()))
        .replace("{noun}", noun(words, parents.lead()));
  }

  /** What a mutated skill is: its two parents and which one leads. */
  public static String skillDescription(Words words, Parents parents) {
    return words.text(MutatedMessages.SKILL)
        .replace("{lead}", skillName(words, parents.lead()))
        .replace("{partner}", skillName(words, parents.partner()));
  }

  /** The adaptation's composed name, the partner's adjective with the recipe. */
  public static String adaptationName(Words words, Parents parents, Recipe recipe) {
    return words.text(MutatedMessages.ADAPTATION_NAME)
        .replace("{adjective}", adjective(words, parents.partner()))
        .replace("{recipe}", words.text(MutatedMessages.recipe(recipe)));
  }

  /** The name players see: the signature's when one claims the slot, otherwise the composed name. */
  public static String name(Words words, Parents parents, Recipe recipe) {
    MutationSlot slot = parents.slot(recipe);
    Optional<String> signature = signatureOwner(parents, slot);
    return signature.isPresent() ? message(words, pairKey(signature.get(), slot) + ".name")
        : adaptationName(words, parents, recipe);
  }

  /**
   * The name on the mutated skill's own pages: the recipe alone, since the skill's name already carries the partner's
   * adjective; null for a signature, whose name is its own.
   */
  public static @Nullable String shortName(Words words, Parents parents, Recipe recipe) {
    return signatureOwner(parents, parents.slot(recipe)).isPresent() ? null
        : words.text(MutatedMessages.recipe(recipe));
  }

  /** The skill's block in {@code slot}, such as Swords Fuel. */
  public static String blockName(Words words, Skill<?> skill, BlockSlot slot) {
    return words.text(MutatedMessages.BLOCK_NAME)
        .replace("{skill}", skillName(words, skill))
        .replace("{slot}", words.text(MutatedMessages.slot(slot)));
  }

  /**
   * The description players see: the signature's when one claims the slot, else the text an addon wrote for it, else
   * the composed one.
   */
  public static String description(Words words, Parents parents, Recipe recipe) {
    MutationSlot slot = parents.slot(recipe);
    Optional<String> signature = signatureOwner(parents, slot);
    if (signature.isPresent()) {
      return withGesture(words, parents, recipe, message(words, pairKey(signature.get(), slot) + ".description"));
    }
    Optional<PairRegistry.Claim<PairText>> text = parents.claims().text(slot);
    return text.isPresent() ? withGesture(words, parents, recipe, text(words, text.get().value().description()))
        : generated(words, parents, recipe);
  }

  /** The description exactly as the recipe composes it from both kits, before any claim. */
  public static String generated(Words words, Parents parents, Recipe recipe) {
    Skill<?> lead = parents.lead();
    Skill<?> partner = parents.partner();
    return switch (recipe) {
      case GRAFT -> triggered(words, lead, partner);
      case ECHO -> triggered(words, partner, lead);
      case BARTER -> gesture(parents)
          .map((BarterGesture gesture) -> words.text(MutatedMessages.BARTER)
              .replace("{gesture}", words.text(MutatedMessages.gesture(gesture)))
              .replace("{fuel}", phrase(words, partner, BlockSlot.FUEL))
              .replace("{boon}", phrase(words, lead, BlockSlot.BOON)))
          .orElseGet(() -> words.text(MutatedMessages.BARTER_UNAVAILABLE));
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

  /** The Barter gesture of the pair: the first neither kit reserves; empty when they reserve all of them. */
  public static Optional<BarterGesture> gesture(Parents parents) {
    return BarterGesture.of(Kits.of(parents.lead()).reservedGestures(), Kits.of(parents.partner()).reservedGestures());
  }

  /**
   * What one block of the skill does: its message by convention ({@code adapt.mutated.block.<path>.<slot>.description} for
   * Adapt's skills, {@link TextKeys#block} for an addon's), or the generic phrase of a skill with no kit.
   */
  public static String phrase(Words words, Skill<?> skill, BlockSlot slot) {
    if (skill.getMutationKit().isEmpty()) {
      return words.text(MutatedMessages.defaultPhrase(slot)).replace("{skill}", skillName(words, skill));
    }
    Id id = skill.getId();
    return message(words, builtIn(id)
        ? AdaptKeys.id("mutated.block." + id.path() + "." + lower(slot.name()) + ".description")
        : TextKeys.block(id, slot));
  }

  /** The skill's name as mutations show it. */
  public static String skillName(Words words, Skill<?> skill) {
    Id id = skill.getId();
    TextKey key = declared(builtIn(id) ? AdaptKeys.id("mutated.skill." + id.path()) : TextKeys.skill(id) + ".name");
    return key == null ? skill.getLocalizedName() : words.text(key);
  }

  /**
   * The key prefix of the messages of a claimed slot ({@code .name}, {@code .description}, {@code .lore1}...): Adapt's
   * own claims on pairs of its own skills keep {@code adapt.mutated.signature.<lead>.<partner>.<recipe>}, any other
   * claim is under {@link TextKeys#pair} in the claiming namespace.
   */
  public static String pairKey(String owner, MutationSlot slot) {
    SkillPair pair = slot.pair();
    if (Id.ADAPT.equals(owner) && builtIn(pair.lead()) && builtIn(pair.partner())) {
      return AdaptKeys.id("mutated.signature." + pair.lead().path() + "." + pair.partner().path() + "."
          + slot.adaptationKey());
    }
    return TextKeys.pair(owner, slot);
  }

  /** The noun the skill gives a mutation it leads. */
  public static String noun(Words words, Skill<?> skill) {
    return word(words, skill, MutationKit::noun, "noun");
  }

  /** The adjective the skill gives a mutation it joins as partner. */
  public static String adjective(Words words, Skill<?> skill) {
    return word(words, skill, MutationKit::adjective, "adjective");
  }

  /** The message {@code id} in {@code words}; a key no bundle declares shows as missing. */
  public static String message(Words words, String id) {
    TextKey key = declared(id);
    return key == null ? AdaptLanguage.missing(id) : words.text(key);
  }

  private static Optional<String> signatureOwner(Parents parents, MutationSlot slot) {
    return parents.claims().signature(slot).map(PairRegistry.Claim::owner);
  }

  private static String word(Words words, Skill<?> skill, Function<MutationKit, Optional<Text>> given, String part) {
    Optional<Text> named = skill.getMutationKit().flatMap(given);
    if (named.isPresent()) {
      return text(words, named.get());
    }
    Id id = skill.getId();
    TextKey key = declared(builtIn(id) ? AdaptKeys.id("mutated." + part + "." + id.path())
        : TextKeys.skill(id) + ".mutated." + part);
    return key == null ? skillName(words, skill) : words.text(key);
  }

  private static String triggered(Words words, Skill<?> trigger, Skill<?> source) {
    return words.text(MutatedMessages.TRIGGERED)
        .replace("{trigger}", phrase(words, trigger, BlockSlot.TRIGGER))
        .replace("{strike}", strike(words, trigger, source));
  }

  private static String strike(Words words, Skill<?> trigger, Skill<?> source) {
    return switch (Kits.of(trigger).triggerKind()) {
      case FIGHT -> phrase(words, source, BlockSlot.STRIKE_FIGHT);
      case WORK -> phrase(words, source, BlockSlot.STRIKE_WORK);
      case EITHER -> words.text(MutatedMessages.EITHER)
          .replace("{fight}", phrase(words, source, BlockSlot.STRIKE_FIGHT))
          .replace("{work}", phrase(words, source, BlockSlot.STRIKE_WORK));
    };
  }

  /** A Barter text with its gesture: in place of {@code {gesture}} when it names one, else in front. */
  private static String withGesture(Words words, Parents parents, Recipe recipe, String text) {
    if (recipe != Recipe.BARTER) {
      return text;
    }
    Optional<BarterGesture> gesture = gesture(parents);
    if (gesture.isEmpty()) {
      return words.text(MutatedMessages.BARTER_UNAVAILABLE);
    }
    String shown = words.text(MutatedMessages.gesture(gesture.get()));
    return text.contains("{gesture}") ? text.replace("{gesture}", shown) : shown + " " + text;
  }

  /** {@code text} with its arguments in place; a key no bundle declares shows as missing. */
  private static String text(Words words, Text text) {
    if (text.isLiteral()) {
      return text.literal();
    }
    String shown = message(words, text.key());
    for (Text.Argument argument : text.arguments()) {
      String value = argument.value() instanceof Text nested ? text(words, nested) : String.valueOf(argument.value());
      shown = shown.replace("{" + argument.name() + "}", value);
    }
    return shown;
  }

  private static @Nullable TextKey declared(String id) {
    MessageKey key = AdaptLanguage.declared(id);
    return key instanceof TextKey text ? text : null;
  }

  private static boolean builtIn(Id skill) {
    return Id.ADAPT.equals(skill.namespace());
  }

  private static String lower(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
