package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

/** The names of the groups a skill's pages show its adaptations in. */
public final class GroupMessages {
  public static final TextKey BLOCKING_GUARD = key("blocking", "guard", "Guarding");
  public static final TextKey BLOCKING_COUNTER = key("blocking", "counter", "Counters");
  public static final TextKey BLOCKING_GEAR = key("blocking", "gear", "Gear");
  public static final TextKey DISCOVERY_EXPLORING = key("discovery", "exploring", "Exploring");
  public static final TextKey DISCOVERY_EXPERIENCE = key("discovery", "experience", "Experience");
  public static final TextKey ENCHANTING_TABLE = key("enchanting", "table", "Table");
  public static final TextKey ENCHANTING_ANVIL = key("enchanting", "anvil", "Anvil & Grindstone");
  public static final TextKey ENCHANTING_BOOKS = key("enchanting", "books", "Books");
  public static final TextKey GOLEMANCY_BINDING = key("golemancy", "binding", "Binding");
  public static final TextKey GOLEMANCY_FRAMES = key("golemancy", "frames", "Frames");
  public static final TextKey GOLEMANCY_CORES = key("golemancy", "cores", "Cores");
  public static final TextKey GOLEMANCY_COMMAND = key("golemancy", "command", "Command & Care");
  public static final TextKey HERBALISM_FARMING = key("herbalism", "farming", "Farming");
  public static final TextKey HERBALISM_SURVIVAL = key("herbalism", "survival", "Survival");
  public static final TextKey HERBALISM_RECIPES = key("herbalism", "recipes", "Recipes");
  public static final TextKey HUNTER_STRUCK = key("hunter", "struck", "When Struck");
  public static final TextKey HUNTER_HUNT = key("hunter", "hunt", "The Hunt");
  public static final TextKey KINETICS_SPEAR = key("kinetics", "spear", "Spear");
  public static final TextKey KINETICS_MACE = key("kinetics", "mace", "Mace");
  public static final TextKey KINETICS_MOVEMENT = key("kinetics", "movement", "Movement");
  public static final TextKey NETHER_FIRE = key("nether", "fire", "Fire");
  public static final TextKey NETHER_WITHER = key("nether", "wither", "Wither");
  public static final TextKey NETHER_EXPLORING = key("nether", "exploring", "Exploring");
  public static final TextKey SEABORNE_SWIMMING = key("seaborne", "swimming", "Swimming");
  public static final TextKey SEABORNE_COMBAT = key("seaborne", "combat", "Combat");
  public static final TextKey SEABORNE_HARVEST = key("seaborne", "harvest", "Sea Harvest");
  public static final TextKey STEALTH_CONCEALMENT = key("stealth", "concealment", "Concealment");
  public static final TextKey STEALTH_TRICKS = key("stealth", "tricks", "Tricks");
  public static final TextKey STEALTH_AMBUSH = key("stealth", "ambush", "Ambush");
  public static final TextKey SWORDS_TECHNIQUE = key("swords", "technique", "Technique");
  public static final TextKey SWORDS_WOUNDS = key("swords", "wounds", "Wounds");
  public static final TextKey SWORDS_BLADES = key("swords", "blades", "Blades");
  public static final TextKey TAMING_CARE = key("taming", "care", "Care");
  public static final TextKey TAMING_BATTLE = key("taming", "battle", "Battle");
  public static final TextKey TRAGOUL_PAIN = key("tragoul", "pain", "Pain");
  public static final TextKey TRAGOUL_CORPSES = key("tragoul", "corpses", "Corpses");
  public static final TextKey TRAGOUL_BLOOD = key("tragoul", "blood", "Blood");

  public static final List<TextKey> ALL = List.of(BLOCKING_GUARD, BLOCKING_COUNTER, BLOCKING_GEAR, DISCOVERY_EXPLORING,
      DISCOVERY_EXPERIENCE, ENCHANTING_TABLE, ENCHANTING_ANVIL, ENCHANTING_BOOKS, GOLEMANCY_BINDING, GOLEMANCY_FRAMES,
      GOLEMANCY_CORES, GOLEMANCY_COMMAND, HERBALISM_FARMING, HERBALISM_SURVIVAL, HERBALISM_RECIPES, HUNTER_STRUCK, HUNTER_HUNT,
      KINETICS_SPEAR, KINETICS_MACE, KINETICS_MOVEMENT, NETHER_FIRE, NETHER_WITHER, NETHER_EXPLORING, SEABORNE_SWIMMING,
      SEABORNE_COMBAT, SEABORNE_HARVEST, STEALTH_CONCEALMENT, STEALTH_TRICKS, STEALTH_AMBUSH, SWORDS_TECHNIQUE, SWORDS_WOUNDS,
      SWORDS_BLADES, TAMING_CARE, TAMING_BATTLE, TRAGOUL_PAIN, TRAGOUL_CORPSES, TRAGOUL_BLOOD);

  private GroupMessages() {
  }

  private static TextKey key(String skill, String group, String english) {
    return TextKey.of("skill." + skill + ".group." + group, english);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (TextKey key : ALL) {
      builder.add(key);
    }
  }
}
