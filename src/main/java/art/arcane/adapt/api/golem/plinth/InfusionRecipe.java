package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.util.config.ConfigDoc;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class InfusionRecipe {
  public static final String RUNIC_AUGMENTS = "golem-runic-augments";
  public static final String MASTERWORK = "golem-masterwork";
  private static final String BLANK_RUNE = "golemancy:blank_rune";
  private static final int[] TIER_ESSENCE = {2, 5, 10};
  private static final int[] TIER_DURATION = {100, 160, 240};
  private static final int[] TIER_UNLOCK = {1, 3, 5};

  @ConfigDoc(value = "Item this infusion makes: a rune such as golemancy:rune/swiftness/1, or golemancy:masterwork_heart.", impact = "Runes take an aspect (swiftness, might, ember, tide, order, entropy or bulwark) and a tier from 1 to 3.")
  private String result = "";
  @ConfigDoc(value = "Item that must sit on the lodestone.", impact = "Accepts a material name such as HEART_OF_THE_SEA or a Golemancy item such as golemancy:blank_rune. A material never matches a Golemancy item made from it.")
  private String center = "";
  @ConfigDoc(value = "The four items that must sit on the pedestals, in any order.", impact = "List exactly four material names or Golemancy items; repeat an entry to need it on more than one pedestal.")
  private List<String> pedestals = new ArrayList<>();
  @ConfigDoc(value = "Experience levels charged when the infusion completes.", impact = "0 makes the infusion free. Creative players never pay.")
  private int essenceLevels = 0;
  @ConfigDoc(value = "Ticks the ritual takes, clamped to 20-6000.", impact = "Higher values make the player wait longer beside the plinth.")
  private int durationTicks = 100;
  @ConfigDoc(value = "Id of the Golemancy adaptation that unlocks this infusion.", impact = "Players need this adaptation at unlockLevel or higher; its region protection rules apply to the ritual.")
  private String unlockAdaptation = "";
  @ConfigDoc(value = "Lowest level of unlockAdaptation that can perform this infusion; at least 1.", impact = "Higher values unlock the infusion later.")
  private int unlockLevel = 1;
  @ConfigDoc(value = "Allows this infusion.", impact = "Set to false to turn the infusion off without deleting it.")
  private boolean enabled = true;

  public static Map<String, InfusionRecipe> defaults() {
    Map<String, InfusionRecipe> recipes = new LinkedHashMap<>();
    rune(recipes, "swiftness", List.of("SUGAR", "SUGAR", "FEATHER", "FEATHER"), List.of("RABBIT_FOOT", "RABBIT_FOOT", "PHANTOM_MEMBRANE", "PHANTOM_MEMBRANE"),
        List.of("BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD"));
    rune(recipes, "might", List.of("IRON_INGOT", "IRON_INGOT", "PISTON", "PISTON"), List.of("IRON_BLOCK", "IRON_BLOCK", "STICKY_PISTON", "STICKY_PISTON"),
        List.of("NETHERITE_SCRAP", "NETHERITE_SCRAP", "IRON_BLOCK", "IRON_BLOCK"));
    rune(recipes, "ember", List.of("BLAZE_POWDER", "BLAZE_POWDER", "COAL", "COAL"), List.of("BLAZE_ROD", "BLAZE_ROD", "MAGMA_CREAM", "MAGMA_CREAM"),
        List.of("GHAST_TEAR", "GHAST_TEAR", "BLAZE_ROD", "BLAZE_ROD"));
    rune(recipes, "tide", List.of("PRISMARINE_SHARD", "PRISMARINE_SHARD", "KELP", "KELP"),
        List.of("PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL", "NAUTILUS_SHELL"),
        List.of("HEART_OF_THE_SEA", "PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS"));
    rune(recipes, "order", List.of("QUARTZ", "QUARTZ", "REDSTONE", "REDSTONE"), List.of("COMPARATOR", "COMPARATOR", "QUARTZ_BLOCK", "QUARTZ_BLOCK"),
        List.of("ECHO_SHARD", "ECHO_SHARD", "COMPARATOR", "COMPARATOR"));
    rune(recipes, "entropy", List.of("GUNPOWDER", "GUNPOWDER", "FERMENTED_SPIDER_EYE", "FERMENTED_SPIDER_EYE"),
        List.of("ENDER_EYE", "ENDER_EYE", "GUNPOWDER", "GUNPOWDER"), List.of("SHULKER_SHELL", "SHULKER_SHELL", "ENDER_EYE", "ENDER_EYE"));
    rune(recipes, "bulwark", List.of("IRON_INGOT", "IRON_INGOT", "OBSIDIAN", "OBSIDIAN"), List.of("DIAMOND", "DIAMOND", "OBSIDIAN", "OBSIDIAN"),
        List.of("NETHERITE_INGOT", "CRYING_OBSIDIAN", "CRYING_OBSIDIAN", "CRYING_OBSIDIAN"));
    recipes.put("masterwork_heart", new InfusionRecipe()
        .result("golemancy:masterwork_heart")
        .center("HEART_OF_THE_SEA")
        .pedestals("DIAMOND_BLOCK", "IRON_BLOCK", "COPPER_BLOCK", "SNOW_BLOCK")
        .essence(15)
        .duration(300)
        .unlock(MASTERWORK, 1));
    return recipes;
  }

  public InfusionRecipe result(String item) {
    result = item;
    return this;
  }

  public InfusionRecipe center(String item) {
    center = item;
    return this;
  }

  public InfusionRecipe pedestals(String... items) {
    pedestals = new ArrayList<>(List.of(items));
    return this;
  }

  public InfusionRecipe essence(int levels) {
    essenceLevels = levels;
    return this;
  }

  public InfusionRecipe duration(int ticks) {
    durationTicks = ticks;
    return this;
  }

  public InfusionRecipe unlock(String adaptation, int level) {
    unlockAdaptation = adaptation;
    unlockLevel = level;
    return this;
  }

  public InfusionRecipe enabled(boolean allowed) {
    enabled = allowed;
    return this;
  }

  private static void rune(Map<String, InfusionRecipe> recipes, String aspect, List<String> tierOne, List<String> tierTwo, List<String> tierThree) {
    List<List<String>> tiers = List.of(tierOne, tierTwo, tierThree);
    for (int tier = 1; tier <= tiers.size(); tier++) {
      recipes.put(aspect + "_" + tier, new InfusionRecipe()
          .result(runeId(aspect, tier))
          .center(tier == 1 ? BLANK_RUNE : runeId(aspect, tier - 1))
          .pedestals(tiers.get(tier - 1).toArray(String[]::new))
          .essence(TIER_ESSENCE[tier - 1])
          .duration(TIER_DURATION[tier - 1])
          .unlock(RUNIC_AUGMENTS, TIER_UNLOCK[tier - 1]));
    }
  }

  private static String runeId(String aspect, int tier) {
    return "golemancy:rune/" + aspect + "/" + tier;
  }
}
