package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.api.mutation.SkillWords;
import art.arcane.adapt.api.mutation.blocks.BarterGesture;
import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The parts mutation names and descriptions are composed from (see
 * {@link art.arcane.adapt.api.mutation.blocks.MutatedText}): templates, skill words, recipe and slot names, Barter
 * gestures, each skill's six block phrases and the hand-written override rows, plus the stat labels of the mutated
 * adaptations and the list of kept originals. A translation translates these parts, never a composed pair.
 */
public final class MutatedMessages {
  public static final TextKey STAT_STRENGTH = TextKey.of("mutated.stat.strength", "Strength");
  public static final TextKey STAT_COOLDOWN = TextKey.of("mutated.stat.cooldown", "Cooldown");
  public static final TextKey STAT_INTERVAL = TextKey.of("mutated.stat.interval", "Interval");
  public static final TextKey KEPT = TextKey.of("mutated.kept", "Kept without learning");
  public static final TextKey KEPT_ADAPTATION = TextKey.of("mutated.kept_adaptation", "{adaptation} {level}");
  public static final TextKey KEPT_LIST = TextKey.of("mutated.kept_list", "Kept without learning: {adaptations}.");
  public static final TextKey KEPT_STATE = TextKey.of("mutated.kept_state", "Kept at level {level}");
  public static final TextKey RAISED_STATE = TextKey.of("mutated.raised_state", "Kept at level {kept}, raised to {level}");
  public static final TextKey LENT_STATE = TextKey.of("mutated.lent_state", "Lent level {level} by {upgrade}");
  public static final TextKey MUTATION_NAME = TextKey.of("mutated.name.mutation", "{adjective} {noun}");
  public static final TextKey ADAPTATION_NAME = TextKey.of("mutated.name.adaptation", "{adjective} {recipe}");
  public static final TextKey BLOCK_NAME = TextKey.of("mutated.name.block", "{skill} {slot}");
  public static final TextKey TRIGGERED = TextKey.of("mutated.template.triggered", "When you {trigger}, {strike}.");
  public static final TextKey EITHER = TextKey.of("mutated.template.either",
      "{fight} if a hostile is within 6 blocks; otherwise {work}");
  public static final TextKey BARTER = TextKey.of("mutated.template.barter",
      "{gesture} to spend {fuel}: you gain {boon}.");
  public static final TextKey ATTUNEMENT = TextKey.of("mutated.template.attunement",
      "While {condition}, every 20 s you gain {boon}.");
  public static final TextKey CONVERGENCE = TextKey.of("mutated.template.convergence",
      "When you {trigger} while {condition}: {strike}. Also, {also}. {seconds} s cooldown.");
  public static final TextKey SKILL = TextKey.of("mutated.template.skill",
      "{lead} and {partner} in one skill: {lead} leads, {partner} is bound to it, and both bring their upgrades.");

  private static final List<TextKey> TEMPLATES = List.of(STAT_STRENGTH, STAT_COOLDOWN, STAT_INTERVAL, KEPT, KEPT_ADAPTATION, KEPT_LIST, KEPT_STATE, RAISED_STATE, LENT_STATE, MUTATION_NAME,
      ADAPTATION_NAME, BLOCK_NAME, TRIGGERED, EITHER, BARTER, ATTUNEMENT, CONVERGENCE, SKILL);
  private static final Map<String, TextKey> NOUNS = new LinkedHashMap<>();
  private static final Map<String, TextKey> ADJECTIVES = new LinkedHashMap<>();
  private static final Map<String, TextKey> SKILLS = new LinkedHashMap<>();
  private static final Map<BlockSlot, TextKey> SLOTS = new EnumMap<>(BlockSlot.class);
  private static final Map<Recipe, TextKey> RECIPES = new EnumMap<>(Recipe.class);
  private static final Map<BarterGesture, TextKey> GESTURES = new EnumMap<>(BarterGesture.class);
  private static final Map<BlockRef, TextKey> PHRASES = new LinkedHashMap<>();
  private static final Map<MutationOverride, TextKey> OVERRIDES = new HashMap<>();
  private static final Map<MutationOverride, TextKey> SIGNATURE_NAMES = new HashMap<>();
  private static final List<TextKey> ALL = new ArrayList<>(TEMPLATES);

  static {
    for (Recipe recipe : Recipe.values()) {
      register(RECIPES, recipe, "mutated.recipe." + lower(recipe.name()), recipe.displayName());
    }
    for (BlockSlot slot : BlockSlot.values()) {
      register(SLOTS, slot, "mutated.slot." + lower(slot.name()), slotName(slot));
    }
    for (BarterGesture gesture : BarterGesture.values()) {
      register(GESTURES, gesture, "mutated.gesture." + lower(gesture.name()), gesture.phrase());
    }
    for (SkillBlocks skill : SkillBlockCatalog.all()) {
      String id = skill.skillId();
      SkillWords words = SkillWords.of(id);
      register(NOUNS, id, "mutated.noun." + id, words.noun());
      register(ADJECTIVES, id, "mutated.adjective." + id, words.adjective());
      register(SKILLS, id, "mutated.skill." + id, skill.skillName());
      for (BlockSlot slot : BlockSlot.values()) {
        BlockRef ref = skill.ref(slot);
        register(PHRASES, ref, MutationBlocks.localizationKey(ref) + ".description", skill.text(slot));
      }
    }
    for (MutationOverride override : MutationOverrides.all()) {
      String description = override.recipe() == Recipe.BARTER
          ? "{gesture} " + override.description() : override.description();
      if (!override.isSignature()) {
        register(OVERRIDES, override, "mutated.override." + override.leadId() + "." + override.partnerId() + "."
            + lower(override.recipe().name()), description);
        continue;
      }
      String prefix = signaturePrefix(override);
      register(SIGNATURE_NAMES, override, prefix + ".name", override.name());
      register(OVERRIDES, override, prefix + ".description", description);
      for (int index = 0; index < override.lore().size(); index++) {
        ALL.add(TextKey.of(prefix + ".lore" + (index + 1), override.lore().get(index)));
      }
    }
  }

  private MutatedMessages() {
  }

  public static TextKey noun(String skillId) {
    return NOUNS.get(skillId);
  }

  public static TextKey adjective(String skillId) {
    return ADJECTIVES.get(skillId);
  }

  public static TextKey skill(String skillId) {
    return SKILLS.get(skillId);
  }

  public static TextKey slot(BlockSlot slot) {
    return SLOTS.get(slot);
  }

  public static TextKey recipe(Recipe recipe) {
    return RECIPES.get(recipe);
  }

  public static TextKey gesture(BarterGesture gesture) {
    return GESTURES.get(gesture);
  }

  public static TextKey phrase(BlockRef ref) {
    return PHRASES.get(ref);
  }

  /** The key prefix of a signature row's own messages: {@code mutated.signature.<lead>.<partner>.<recipe>}. */
  public static String signaturePrefix(MutationOverride signature) {
    return "mutated.signature." + signature.leadId() + "." + signature.partnerId() + "."
        + lower(signature.recipe().name());
  }

  /** A signature row's name. */
  public static TextKey signatureName(MutationOverride signature) {
    return SIGNATURE_NAMES.get(signature);
  }

  /** The override row's text; a Barter row starts with the {@code {gesture}} placeholder. */
  public static TextKey override(MutationOverride override) {
    return OVERRIDES.get(override);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (TextKey key : ALL) {
      builder.add(key);
    }
  }

  private static <K> void register(Map<K, TextKey> keys, K owner, String id, String english) {
    TextKey key = TextKey.of(id, english);
    keys.put(owner, key);
    ALL.add(key);
  }

  private static String lower(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static String slotName(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> "Trigger";
      case STRIKE_FIGHT -> "Fight Strike";
      case STRIKE_WORK -> "Work Strike";
      case BOON -> "Boon";
      case FUEL -> "Fuel";
      case STATE -> "State";
    };
  }
}
