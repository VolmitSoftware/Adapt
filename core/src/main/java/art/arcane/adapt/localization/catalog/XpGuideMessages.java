package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How each skill earns XP, a few short lines per skill taken from the XP its skill class awards, and the rules a mutated
 * skill adds. Lines name actions, never amounts, so they stay true when a server changes its XP config.
 */
public final class XpGuideMessages {
  public static final TextKey TITLE = TextKey.of("xp_guide.title", "Earning XP");
  public static final TextKey MUTATION = TextKey.of("xp_guide.mutation_title", "Mutation");
  public static final TextKey ONE_BAR = TextKey.of("xp_guide.mutation.one_bar", "Both parents' actions fill this one bar.");
  public static final TextKey SAME_ACTION = TextKey.of("xp_guide.mutation.same_action",
      "An action that pays both parents pays the larger award plus {share}% of the smaller.");
  public static final TextKey FARMED_NEVER = TextKey.of("xp_guide.mutation.farmed", "Farmed sources never pay.");
  public static final TextKey ADAPTATIONS = TextKey.of("xp_guide.adaptations", "Using its adaptations and finishing its challenges");
  public static final TextKey FARMED_MOBS = TextKey.of("xp_guide.farmed_mobs",
      "Mobs from spawners, eggs, buckets, breeding or portals pay nothing");
  public static final TextKey PLACED_BLOCKS = TextKey.of("xp_guide.placed_blocks", "Blocks you placed pay nothing");

  private static final Map<String, List<TextKey>> LINES = new LinkedHashMap<>();

  static {
    skill("agility",
        line("agility", "move", "Walking, sprinting, swimming, jumping and climbing"),
        line("agility", "riding", "Riding, flying and gliding pay nothing"));
    skill("architect",
        line("architect", "place", "Placing blocks, more for valuable ones"),
        line("architect", "throw", "Hitting mobs with thrown blocks"),
        line("architect", "storage", "Storage blocks and breaking blocks pay nothing"));
    skill("axes",
        line("axes", "combat", "Hitting mobs with an axe"),
        line("axes", "chop", "Chopping logs, wood, stems and mushroom blocks with an axe"),
        FARMED_MOBS, PLACED_BLOCKS);
    skill("blocking",
        line("blocking", "blocked_hit", "Blocking hits with a shield, more for harder hits"),
        line("blocking", "shield_hold", "Keeping a shield raised after a block, where the server allows it"));
    skill("brewing",
        line("brewing", "drink", "Drinking brewed potions, more for stronger ones"),
        line("brewing", "splash", "Throwing brewed splash potions"),
        line("brewing", "plain", "Water, mundane, thick and awkward potions pay nothing"));
    skill("chronos",
        line("chronos", "movement", "Moving about, more with a clock in hand or in your pack"),
        line("chronos", "active", "Staying active, more at night"),
        line("chronos", "survival", "Staying alive, more for each hour since you last died"),
        line("chronos", "sleep", "Sleeping through the night"),
        line("chronos", "speed", "Drinking or splashing speed potions"),
        line("chronos", "idle", "Standing idle pays almost nothing"));
    skill("crafting",
        line("crafting", "craft", "Crafting items, more for valuable results"),
        line("crafting", "smelt", "Being near a furnace as it smelts"),
        line("crafting", "convert", "Turning nuggets, ingots and blocks into each other pays nothing"));
    skill("discovery",
        line("discovery", "new", "Finding new blocks, items, mobs, biomes, foods, recipes and enchantments"),
        line("discovery", "chunk", "Exploring chunks you have never seen"),
        line("discovery", "loot", "Opening loot chests"),
        line("discovery", "advancement", "Completing advancements"),
        line("discovery", "world", "Visiting new worlds and dimensions"),
        line("discovery", "orbs", "Gaining vanilla experience"),
        line("discovery", "once", "Each discovery pays once"));
    skill("enchanting",
        line("enchanting", "enchant", "Enchanting at a table, more for higher levels"));
    skill("excavation",
        line("excavation", "combat", "Hitting mobs with a shovel"),
        line("excavation", "dig", "Digging with a shovel, more for harder blocks"),
        line("excavation", "throw", "Hitting mobs with flung blocks"),
        FARMED_MOBS, PLACED_BLOCKS);
    skill("golemancy",
        line("golemancy", "build", "Building and binding golems"),
        line("golemancy", "craft", "Crafting crooks, cores, nodes and runes, and infusing at a plinth"),
        line("golemancy", "fight", "Your golems fighting and taking hits near you"),
        line("golemancy", "work", "Your golems working near you"),
        line("golemancy", "care", "Repairing, scraping and waxing your golems"),
        line("golemancy", "witness", "Watching an unbound golem kill a hostile mob"));
    skill("herbalism",
        line("herbalism", "harvest", "Harvesting crops, more the riper they are"),
        line("herbalism", "eat", "Eating food"),
        line("herbalism", "shear", "Shearing animals"),
        line("herbalism", "compost", "Filling and emptying composters"),
        line("herbalism", "bonemeal", "Bone-mealed crops pay half, and the same spot pays less for a few minutes"));
    skill("hunter",
        line("hunter", "kill", "Killing mobs, more for tougher ones and creepers"),
        line("hunter", "farmed", "Mobs from spawners, eggs, buckets, breeding or portals pay much less"));
    skill("kinetics",
        line("kinetics", "mace", "Smashing and hitting with a mace"),
        line("kinetics", "spear", "Spear charges, jabs and mounted charges"),
        line("kinetics", "knockback", "Knocking mobs back and being knocked back"),
        line("kinetics", "fall", "Falling, breaking a fall, bouncing and launching"),
        line("kinetics", "levitation", "Levitating, or making others levitate"),
        line("kinetics", "anvil", "Dropping anvils on mobs, and using anvils and grindstones"));
    skill("nether",
        line("nether", "ore", "Mining nether quartz, nether gold and ancient debris"),
        line("nether", "kill", "Killing nether mobs, wither skeletons and the wither"),
        line("nether", "wither", "Hitting the wither and taking wither damage"),
        line("nether", "rose", "Breaking wither roses"),
        PLACED_BLOCKS);
    skill("pickaxe",
        line("pickaxe", "combat", "Hitting mobs with a pickaxe"),
        line("pickaxe", "mine", "Mining with a pickaxe, most for ores and deepslate ores"),
        line("pickaxe", "silk", "Silk Touch pays a little"),
        FARMED_MOBS, PLACED_BLOCKS);
    skill("ranged",
        line("ranged", "shoot", "Shooting arrows"),
        line("ranged", "hit", "Hitting with arrows, more from farther away"),
        line("ranged", "kill", "Killing mobs with a bow or crossbow in hand"),
        FARMED_MOBS);
    skill("rift",
        line("rift", "portal", "Travelling through portals"),
        line("rift", "teleport", "Teleporting with chorus fruit, ender pearls and other ways"),
        line("rift", "throw", "Throwing ender pearls and eyes of ender"),
        line("rift", "ender", "Hurting endermen, endermites and the ender dragon"),
        line("rift", "kill", "Destroying end crystals and killing shulkers and endermites"),
        FARMED_MOBS);
    skill("seaborne",
        line("seaborne", "swim", "Swimming, more with water breathing"),
        line("seaborne", "fish", "Fishing, most for treasure"),
        line("seaborne", "underwater", "Breaking blocks underwater"),
        line("seaborne", "kill", "Killing sea creatures"),
        line("seaborne", "hit", "Hitting drowned, and hitting with a trident"),
        FARMED_MOBS, PLACED_BLOCKS);
    skill("stealth",
        line("stealth", "opener", "Striking a mob that has not noticed you, more from behind while sneaking"),
        line("stealth", "combat", "Hitting while sneaking"),
        line("stealth", "kill", "Killing while sneaking"),
        line("stealth", "sneak", "Moving while sneaking"),
        FARMED_MOBS);
    skill("swords",
        line("swords", "combat", "Hitting mobs with a sword"),
        line("swords", "kill", "Killing mobs with a sword"),
        FARMED_MOBS);
    skill("taming",
        line("taming", "tame", "Taming animals"),
        line("taming", "breed", "Breeding animals, once per pair every few minutes"),
        line("taming", "pet_combat", "Your pets hitting mobs"),
        line("taming", "pet_kill", "Your pets killing mobs"),
        FARMED_MOBS);
    skill("tragoul",
        line("tragoul", "damage", "Taking damage from mobs and players"),
        line("tragoul", "low_health", "Surviving a hit at low health"));
    skill("unarmed",
        line("unarmed", "combat", "Hitting with an empty hand or anything that is not a tool"),
        line("unarmed", "kill", "Killing mobs that way"),
        FARMED_MOBS);
  }

  private XpGuideMessages() {
  }

  private static TextKey line(String skill, String slug, String english) {
    return TextKey.of("xp_guide." + skill + "." + slug, english);
  }

  private static void skill(String skill, TextKey... lines) {
    List<TextKey> all = new ArrayList<>(List.of(lines));
    all.add(ADAPTATIONS);
    LINES.put(skill, List.copyOf(all));
  }

  /** The lines for {@code skillId}, in order; empty for a skill this guide does not describe. */
  public static List<TextKey> lines(String skillId) {
    return LINES.getOrDefault(skillId, List.of());
  }

  /** The skills this guide describes. */
  public static List<String> skills() {
    return List.copyOf(LINES.keySet());
  }

  /** The mutation's rules, in order. */
  public static List<TextKey> rules() {
    return List.of(ONE_BAR, SAME_ACTION, FARMED_NEVER);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(TITLE);
    builder.add(MUTATION);
    for (TextKey rule : rules()) {
      builder.add(rule);
    }
    builder.add(ADAPTATIONS);
    builder.add(FARMED_MOBS);
    builder.add(PLACED_BLOCKS);
    for (List<TextKey> lines : LINES.values()) {
      for (TextKey line : lines) {
        if (line != ADAPTATIONS && line != FARMED_MOBS && line != PLACED_BLOCKS) {
          builder.add(line);
        }
      }
    }
  }
}
