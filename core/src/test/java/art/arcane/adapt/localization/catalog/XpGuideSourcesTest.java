package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The XP guide names every source its skill class pays. Each xp call in a {@code Skill<Name>} class is read from source
 * and named by its reward key, or by the config field it pays when it has no key; every name must map to a guide line
 * the skill shows. A new award fails here until the guide says how to earn it.
 */
class XpGuideSourcesTest {
  private static final Path SKILLS = Path.of("src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern ID = Pattern.compile("super\\(\"([a-z]+)\"");
  private static final Pattern CALL = Pattern.compile("\\b(xp|xpS|xpSilent)\\(");
  private static final Pattern KEY = Pattern.compile("\"([a-z]+:[a-z0-9_:.-]*)\"");
  private static final Pattern FIELD = Pattern.compile("getConfig\\(\\)\\.(?:get)?(\\w+)");
  private static final Map<String, Map<String, String>> SOURCES = new LinkedHashMap<>();

  static {
    sources("agility", "agility:move", "move", "agility:sprint", "move", "agility:swim", "move", "agility:jump", "move",
        "agility:climb", "move");
    sources("architect", "architect:place", "place");
    sources("axes", "axes:combat", "combat", "axes:chop", "chop");
    sources("blocking", "blocking:blocked-hit", "blocked_hit", "blocking:shield-hold", "shield_hold");
    sources("brewing", "splashXP", "drink", "splashXP", "splash");
    sources("chronos", "chronos:movement", "movement", "chronos:passive", "active", "chronos:survival", "survival",
        "chronos:sleep", "sleep", "chronos:speed-potion", "speed");
    sources("crafting", "baseCraftingXP", "craft", "furnaceBaseXP", "smelt");
    sources("discovery", "discovery:chunk", "chunk", "discovery:loot-container", "loot", "discovery:advancement", "advancement",
        "amount", "orbs", "discoverBlockBaseXP", "new", "discoverItemBaseXP", "new", "discoverRecipeBaseXP", "new",
        "discoverFoodTypeXP", "new", "discoverEntityTypeXP", "new", "discoverPlayerXP", "new", "discoverEnchantBaseXP", "new",
        "discoverWorldXP", "world", "discoverEnvironmentXP", "world", "discoverPotionXP", "new", "discoverBiomeXP", "new");
    sources("enchanting", "enchantPowerXPMultiplier", "enchant");
    sources("excavation", "excavation:combat", "combat", "excavation:dig", "dig");
    sources("golemancy", "golemancy:build:", "build", "GolemRepairXp.REWARD_KEY", "care", "rewardKey", "work",
        "golemancy:bind", "build", "golemancy:first_bind:", "build", "golemancy:craft:", "craft", "infusionRewardKey(result)", "craft",
        "golemancy:assist", "fight", "golemancy:tank", "fight", "golemancy:witness", "witness", "golemancy:copper:scrape", "care",
        "golemancy:copper:wax", "care");
    sources("herbalism", "herbalism:harvest", "harvest", "foodConsumeXP", "eat", "shearXP", "shear", "composterBaseXP", "compost");
    sources("hunter", "rewardKey", "kill");
    sources("kinetics", "kinetics:smash", "mace", "kinetics:mace-hit", "mace", "kinetics:mounted-charge", "spear",
        "kinetics:spear-charge", "spear", "kinetics:spear-jab", "spear", "kinetics:knockback-dealt", "knockback",
        "kinetics:knockback-taken", "knockback", "kinetics:break-fall", "fall", "kinetics:fall", "fall", "kinetics:bounce", "fall",
        "kinetics:launch", "fall", "kinetics:levitation-received", "levitation", "kinetics:levitation-applied", "levitation",
        "kinetics:levitation-airtime", "levitation", "kinetics:anvil-crush", "anvil", "kinetics:anvil-share", "anvil",
        "kinetics:anvil-use", "anvil", "kinetics:grindstone-use", "anvil", "rewardKey", "fall");
    sources("nether", "WitherDamageXp", "wither", "nether:ore", "ore", "WitherRoseBreakXp", "rose", "nether:kill", "kill",
        "WitherSkeletonKillXp", "kill", "WitherKillXp", "kill", "WitherAttackXp", "wither");
    sources("pickaxe", "pickaxe:combat", "combat", "pickaxe:mine", "mine");
    sources("ranged", "shootXP", "shoot", "ranged:combat", "hit", "ranged:kill", "kill");
    sources("rift", "rift:portal", "portal", "rift:chorus", "teleport", "rift:teleport", "teleport", "rift:throw:ender-pearl", "throw",
        "rift:throw:ender-eye", "throw", "rewardKey", "ender", "rift:kill:end-crystal", "kill", "rift:kill", "kill");
    sources("seaborne", "seaborne:swim", "swim", "amount", "fish", "seaborne:sea-pickle", "underwater",
        "seaborne:underwater-block", "underwater", "seaborne:sea-kill", "kill", "seaborne:drowned-hit", "hit",
        "seaborne:trident-hit", "hit");
    sources("stealth", "stealth:opener", "opener", "stealth:combat", "combat", "stealth:kill", "kill", "stealth:sneak", "sneak");
    sources("swords", "swords:combat", "combat", "swords:kill", "kill");
    sources("taming", "taming:breed", "breed", "taming:pet-combat", "pet_combat", "tameSuccessXP", "tame", "taming:pet-kill", "pet_kill");
    sources("tragoul", "tragoul:damage-received", "damage", "lowHealthSurvivalXP", "low_health");
    sources("unarmed", "unarmed:combat", "combat", "unarmed:kill", "kill");
  }

  private static void sources(String skill, String... pairs) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      map.put(pairs[index], pairs[index + 1]);
    }
    SOURCES.put(skill, map);
  }

  @Test
  void everySkillHasAGuideAndEveryGuideHasLines() throws IOException {
    Set<String> ids = skillFiles().keySet();
    assertThat(ids).hasSize(24);
    assertThat(new TreeSet<>(XpGuideMessages.skills())).isEqualTo(new TreeSet<>(ids));
    for (String id : ids) {
      assertThat(XpGuideMessages.lines(id)).as(id).hasSizeGreaterThan(1).contains(XpGuideMessages.ADAPTATIONS);
    }
  }

  @Test
  void everyXpAwardInASkillClassIsNamedByAGuideLine() throws IOException {
    Map<String, List<String>> unknown = new TreeMap<>();
    for (Map.Entry<String, Path> skill : skillFiles().entrySet()) {
      Map<String, String> known = SOURCES.getOrDefault(skill.getKey(), Map.of());
      for (String source : awards(Files.readString(skill.getValue()))) {
        if (!known.containsKey(source)) {
          unknown.computeIfAbsent(skill.getKey(), (String ignored) -> new ArrayList<>()).add(source);
        }
      }
    }
    assertThat(unknown).as("xp awards no guide line names; add a line and map it here").isEmpty();
  }

  @Test
  void everyMappedLineIsShownForItsSkill() {
    for (Map.Entry<String, Map<String, String>> skill : SOURCES.entrySet()) {
      Set<String> shown = new LinkedHashSet<>();
      for (TextKey line : XpGuideMessages.lines(skill.getKey())) {
        shown.add(line.id());
      }
      for (String slug : skill.getValue().values()) {
        assertThat(shown).as(skill.getKey()).contains("xp_guide." + skill.getKey() + "." + slug);
      }
    }
  }

  /** Each xp call's reward key, or the config field it pays, or its last argument when it has neither. */
  static List<String> awards(String source) {
    List<String> awards = new ArrayList<>();
    Matcher call = CALL.matcher(source);
    while (call.find()) {
      String arguments = arguments(source, call.end());
      if (arguments.isBlank() || source.substring(Math.max(0, call.start() - 20), call.start()).contains("void ")) {
        continue;
      }
      Matcher key = KEY.matcher(arguments);
      Matcher field = FIELD.matcher(arguments);
      if (key.find()) {
        awards.add(key.group(1));
      } else if (field.find()) {
        awards.add(field.group(1));
      } else {
        String[] parts = arguments.split(",");
        String last = parts[parts.length - 1].trim();
        awards.add(last.startsWith("e.") ? last.substring(2).replace("()", "") : last.replace("()", ""));
      }
    }
    return awards;
  }

  private static String arguments(String source, int from) {
    int depth = 1;
    for (int index = from; index < source.length(); index++) {
      char c = source.charAt(index);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return source.substring(from, index);
      }
    }
    return "";
  }

  private static Map<String, Path> skillFiles() throws IOException {
    Map<String, Path> files = new TreeMap<>();
    try (Stream<Path> paths = Files.list(SKILLS)) {
      for (Path path : paths.filter((Path p) -> p.getFileName().toString().matches("Skill[A-Z]\\w+\\.java")).toList()) {
        Matcher id = ID.matcher(Files.readString(path));
        if (id.find()) {
          files.put(id.group(1), path);
        }
      }
    }
    return files;
  }
}
