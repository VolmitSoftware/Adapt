package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The parts mutation names and descriptions are composed from (see
 * {@link art.arcane.adapt.engine.mutation.blocks.MutatedText}): templates, recipe and slot names, Barter gestures and
 * the phrases of a skill that registered no kit, plus the stat labels of the mutated adaptations and the list of kept
 * originals. Each skill's words and phrases and each pair's texts are the content's own messages. A translation
 * translates these parts, never a composed pair.
 */
public final class MutatedMessages {
  public static final TextKey STAT_STRENGTH = AdaptKeys.text("mutated.stat.strength", "Strength");
  public static final TextKey STAT_COOLDOWN = AdaptKeys.text("mutated.stat.cooldown", "Cooldown");
  public static final TextKey STAT_INTERVAL = AdaptKeys.text("mutated.stat.interval", "Interval");
  public static final TextKey KEPT = AdaptKeys.text("mutated.kept", "Kept without learning");
  public static final TextKey KEPT_ADAPTATION = AdaptKeys.text("mutated.kept_adaptation", "{adaptation} {level}");
  public static final TextKey KEPT_LIST = AdaptKeys.text("mutated.kept_list", "Kept without learning: {adaptations}.");
  public static final TextKey KEPT_STATE = AdaptKeys.text("mutated.kept_state", "Kept at level {level}");
  public static final TextKey RAISED_STATE = AdaptKeys.text("mutated.raised_state", "Kept at level {kept}, raised to {level}");
  public static final TextKey LENT_STATE = AdaptKeys.text("mutated.lent_state", "Lent level {level} by {upgrade}");
  public static final TextKey MUTATION_NAME = AdaptKeys.text("mutated.name.mutation", "{adjective} {noun}");
  public static final TextKey ADAPTATION_NAME = AdaptKeys.text("mutated.name.adaptation", "{adjective} {recipe}");
  public static final TextKey BLOCK_NAME = AdaptKeys.text("mutated.name.block", "{skill} {slot}");
  public static final TextKey TRIGGERED = AdaptKeys.text("mutated.template.triggered", "When you {trigger}, {strike}.");
  public static final TextKey EITHER = AdaptKeys.text("mutated.template.either",
      "{fight} if a hostile is within 6 blocks; otherwise {work}");
  public static final TextKey BARTER = AdaptKeys.text("mutated.template.barter",
      "{gesture} to spend {fuel}: you gain {boon}.");
  public static final TextKey BARTER_UNAVAILABLE = AdaptKeys.text("mutated.template.barter_unavailable",
      "Unavailable: these two skills use every Barter gesture between them.");
  public static final TextKey ATTUNEMENT = AdaptKeys.text("mutated.template.attunement",
      "While {condition}, every 20 s you gain {boon}.");
  public static final TextKey CONVERGENCE = AdaptKeys.text("mutated.template.convergence",
      "When you {trigger} while {condition}: {strike}. Also, {also}. {seconds} s cooldown.");
  public static final TextKey SKILL = AdaptKeys.text("mutated.template.skill",
      "{lead} and {partner} in one skill: {lead} leads, {partner} is bound to it, and both bring their upgrades.");

  private static final List<TextKey> TEMPLATES = List.of(STAT_STRENGTH, STAT_COOLDOWN, STAT_INTERVAL, KEPT, KEPT_ADAPTATION,
      KEPT_LIST, KEPT_STATE, RAISED_STATE, LENT_STATE, MUTATION_NAME, ADAPTATION_NAME, BLOCK_NAME, TRIGGERED, EITHER,
      BARTER, BARTER_UNAVAILABLE, ATTUNEMENT, CONVERGENCE, SKILL);
  private static final Map<BlockSlot, TextKey> SLOTS = new EnumMap<>(BlockSlot.class);
  private static final Map<BlockSlot, TextKey> DEFAULT_PHRASES = new EnumMap<>(BlockSlot.class);
  private static final Map<Recipe, TextKey> RECIPES = new EnumMap<>(Recipe.class);
  private static final Map<BarterGesture, TextKey> GESTURES = new EnumMap<>(BarterGesture.class);
  private static final List<TextKey> ALL = new ArrayList<>(TEMPLATES);

  static {
    for (Recipe recipe : Recipe.values()) {
      register(RECIPES, recipe, "mutated.recipe." + lower(recipe.name()), recipeName(recipe));
    }
    for (BlockSlot slot : BlockSlot.values()) {
      register(SLOTS, slot, "mutated.slot." + lower(slot.name()), slotName(slot));
      register(DEFAULT_PHRASES, slot, "mutated.default." + lower(slot.name()), genericPhrase(slot));
    }
    for (BarterGesture gesture : BarterGesture.values()) {
      register(GESTURES, gesture, "mutated.gesture." + lower(gesture.name()), gesturePhrase(gesture));
    }
  }

  private MutatedMessages() {
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

  /** The phrase of a block of a skill that registered no kit; {@code {skill}} is the skill's name. */
  public static TextKey defaultPhrase(BlockSlot slot) {
    return DEFAULT_PHRASES.get(slot);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (TextKey key : ALL) {
      builder.add(key);
    }
  }

  private static <K> void register(Map<K, TextKey> keys, K owner, String id, String english) {
    TextKey key = AdaptKeys.text(id, english);
    keys.put(owner, key);
    ALL.add(key);
  }

  private static String lower(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static String recipeName(Recipe recipe) {
    return switch (recipe) {
      case GRAFT -> "Graft";
      case ECHO -> "Echo";
      case BARTER -> "Barter";
      case ATTUNEMENT -> "Attunement";
      case CONVERGENCE -> "Convergence";
    };
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

  private static String gesturePhrase(BarterGesture gesture) {
    return switch (gesture) {
      case SNEAK_SWAP -> "Sneak and swap hands";
      case DOUBLE_SNEAK -> "Double-tap sneak";
      case SNEAK_LEFT_CLICK -> "Sneak and left-click the air";
      case SNEAK_SCROLL -> "Sneak and scroll the hotbar";
    };
  }

  private static String genericPhrase(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> "earn {skill} XP";
      case STRIKE_FIGHT -> "the target is briefly weakened and slowed";
      case STRIKE_WORK -> "you gain a short burst of Haste";
      case BOON -> "a little regeneration and saturation";
      case FUEL -> "a little hunger";
      case STATE -> "you earned {skill} XP in the last few seconds";
    };
  }
}
