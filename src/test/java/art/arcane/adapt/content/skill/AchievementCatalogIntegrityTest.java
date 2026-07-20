package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementCatalogIntegrityTest {
  private static final Path CONTENT_ROOT = Path.of("src/main/java/art/arcane/adapt/content");
  private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
  private static final Pattern DIRECT_MILESTONE_PATTERN = Pattern.compile(
      "registerMilestone\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*([0-9_]+)"
  );
  private static final Pattern MILESTONE_STAT_PATTERN = Pattern.compile(
      "registerMilestone\\(\\s*[^,]+,\\s*\"([^\"]+)\"",
      Pattern.DOTALL
  );
  private static final Pattern STAT_TRACKER_PATTERN = Pattern.compile("\\.statTracker\\(\\s*\"([^\"]+)\"");
  private static final Pattern STAT_WRITE_PATTERN = Pattern.compile("addStat\\([^;]*?\"([^\"]+)\"", Pattern.DOTALL);
  private static final Pattern ADVANCEMENT_KEY_PATTERN = Pattern.compile("\\.key\\(\\s*\"([^\"]+)\"\\s*\\)");
  private static final Pattern LOCALE_KEY_PATTERN = Pattern.compile("(?m)^\\[advancement\\.([^]]+)]$");
  private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?m)^description\\s*=\\s*\"([^\"]*)\"\\s*$");
  private static final Set<String> RETIRED_ADVANCEMENTS = Set.of(
      "challenge_place_5m",
      "challenge_place_500k",
      "challenge_chop_5m",
      "challenge_chop_500k",
      "challenge_block_5m",
      "challenge_block_500k",
      "challenge_brew_5m",
      "challenge_brewsplash_5m",
      "challenge_brew_50k",
      "challenge_brew_500k",
      "challenge_brewsplash_50k",
      "challenge_brewsplash_500k",
      "challenge_craft_5m",
      "challenge_craft_500k",
      "challenge_enchant_5m",
      "challenge_enchant_50k",
      "challenge_enchant_500k",
      "challenge_excavate_5m",
      "challenge_excavate_500k",
      "challenge_pickaxe_5m",
      "challenge_pickaxe_500k",
      "horrible_person",
      "challenge_turtle_egg_smasher",
      "challenge_turtle_egg_annihilator",
      "challenge_chronos_168h",
      "challenge_chronos_deaths_10",
      "challenge_chronos_deaths_100",
      "challenge_trag_deaths_10",
      "challenge_trag_deaths_100",
      "challenge_trag_fire_500",
      "challenge_trag_fire_5k",
      "challenge_trag_fall_500",
      "challenge_trag_fall_5k",
      "challenge_stealth_time_1h",
      "challenge_stealth_time_10h",
      "challenge_taming_2500",
      "challenge_taming_25k",
      "challenge_axe_swing_500",
      "challenge_axe_swing_5k",
      "challenge_dig_swing_500",
      "challenge_dig_swing_5k",
      "challenge_pick_swing_500",
      "challenge_pick_swing_5k",
      "challenge_enchant_levels_1k",
      "challenge_enchant_levels_10k",
      "challenge_craft_mass_25k",
      "challenge_craft_mass_250k",
      "challenge_discovery_armor_24hr",
      "challenge_taming_health_boost_1728k",
      "challenge_sprint_5k",
      "challenge_sprint_50k",
      "challenge_sprint_500k",
      "challenge_agility_wind_up_2hr",
      "challenge_agility_armor_up_5hr",
      "challenge_unarmed_flurry_1k",
      "challenge_unarmed_flurry_10k",
      "challenge_axe_orchardist_500",
      "challenge_axe_sap_tap_500",
      "challenge_axe_timber_200",
      "challenge_axe_timber_40",
      "challenge_excavation_dowsing_200",
      "challenge_rift_step_50",
      "challenge_rift_step_500"
  );
  private static final Set<String> RETIRED_STATS = Set.of(
      "killed.turtleeggs",
      "chronos.deaths",
      "trag.deaths",
      "trag.fire.damage",
      "trag.fall.damage",
      "stealth.time",
      "axes.swings",
      "excavation.swings",
      "pickaxe.swings",
      "enchanted.levels.spent",
      "unarmed.flurry.flurry-hits",
      "axe.orchardist.trees-replanted",
      "axe.sap-tap.sap-drawn",
      "axe.timber-mark.marks-felled",
      "excavation.dowsing.pockets-found",
      "rift.step.saves"
  );
  private static final Map<String, Integer> HUNTER_GOALS = Map.of(
      "challenge_novice_hunter", 100,
      "challenge_intermediate_hunter", 500,
      "challenge_advanced_hunter", 5000,
      "challenge_creeper_conqueror", 50,
      "challenge_creeper_annihilator", 200
  );

  @Test
  void retiredCatalogBranchesStayRemoved() throws IOException {
    String source = readJavaCatalog();
    String resources = readResourceCatalog();

    for (String key : RETIRED_ADVANCEMENTS) {
      assertThat(source).as("retired advancement source %s", key).doesNotContain(key);
      assertThat(resources).as("retired advancement locale %s", key).doesNotContain(key);
    }
    for (String stat : RETIRED_STATS) {
      assertThat(source).as("retired stat %s", stat).doesNotContain(stat);
    }
    assertThat(source).doesNotContain("RiftStep", "\"rift-step\"", "\"rift.step.", "challenge_rift_step");
    assertThat(resources).doesNotContain("[rift.step]", "[advancement.challenge_rift_step");
  }

  @Test
  void literalMilestonesHaveDefinitionsAndEnglishCopy() throws IOException {
    String source = readJavaCatalog();
    Set<String> milestoneKeys = matches(DIRECT_MILESTONE_PATTERN, source, 1);
    Set<String> definitionKeys = matches(ADVANCEMENT_KEY_PATTERN, source, 1);
    Set<String> localeKeys = matches(LOCALE_KEY_PATTERN, Files.readString(RESOURCE_ROOT.resolve("en_US.toml")), 1);

    assertThat(definitionKeys).containsAll(milestoneKeys);
    assertThat(localeKeys).containsAll(milestoneKeys);
  }

  @Test
  void trackedStatsHaveLiteralWriters() throws IOException {
    String source = readJavaCatalog();
    Set<String> trackedStats = matches(MILESTONE_STAT_PATTERN, source, 1);
    trackedStats.addAll(matches(STAT_TRACKER_PATTERN, source, 1));
    Set<String> writtenStats = matches(STAT_WRITE_PATTERN, source, 1);

    assertThat(writtenStats).containsAll(trackedStats);
  }

  @Test
  void hunterMilestonesMatchEveryShippedLocale() throws IOException {
    Map<String, Integer> registeredGoals = registeredGoals(readJavaCatalog());
    List<Path> localeFiles = localeFiles();

    for (Map.Entry<String, Integer> entry : HUNTER_GOALS.entrySet()) {
      String key = entry.getKey();
      int expectedGoal = entry.getValue();
      assertThat(registeredGoals).containsEntry(key, expectedGoal);
      for (Path localeFile : localeFiles) {
        String description = advancementDescription(Files.readString(localeFile), key);
        String numericText = description.replaceAll("[^0-9]", "");
        assertThat(numericText)
            .as("%s threshold in %s", key, localeFile.getFileName())
            .isEqualTo(Integer.toString(expectedGoal));
      }
    }
  }

  @Test
  void activeTimeAndMarathonGoalsStayIntentional() throws IOException {
    String source = readJavaCatalog();
    Map<String, Integer> registeredGoals = registeredGoals(source);

    assertThat(registeredGoals).containsEntry("challenge_taming_health_boost_72k", 72000);
    assertThat(registeredGoals).containsEntry("challenge_stealth_sight_sneak_1h", 72000);
    assertThat(registeredGoals).containsEntry("challenge_sprint_marathon", 42195);
    assertThat(source).contains("registerMilestone(\"challenge_sprint_marathon\", \"move.sprint\", 42195");
  }

  @Test
  void progressionWritersAndCopyUseAdvertisedUnits() throws IOException {
    String source = readJavaCatalog();
    String english = Files.readString(RESOURCE_ROOT.resolve("en_US.toml"));
    String resources = readResourceCatalog();

    assertThat(source).doesNotContain("stealth.sight.time-in-darkness");
    assertThat(source).doesNotContain("chronos.time-bottle.charges-spent");
    assertThat(source).doesNotContain("challenge_stealth_sight_72k");
    assertThat(source).doesNotContain("challenge_chronos_bottle_1k");
    assertThat(source).doesNotContain("challenge_chronos_bottle_25k");
    assertThat(resources).doesNotContain("challenge_stealth_sight_72k");
    assertThat(resources).doesNotContain("challenge_chronos_bottle_1k");
    assertThat(resources).doesNotContain("challenge_chronos_bottle_25k");
    assertThat(source).contains("addStat(p, \"chronos.time-bottle.seconds-spent\", result.spentSeconds())");
    assertThat(advancementDescription(english, "challenge_chronos_bottle_seconds_1k"))
        .isEqualTo("Spend 1,000 seconds of stored time");
    assertThat(advancementDescription(english, "challenge_hunter_regen_500"))
        .isEqualTo("Trigger Hunter regeneration 500 times after being struck");
    assertThat(advancementDescription(english, "challenge_nether_blaze_200"))
        .isEqualTo("Trigger Blaze Leech 200 times");
    assertThat(advancementDescription(english, "challenge_swords_bloody_500"))
        .isEqualTo("Inflict bleeding 500 times");
  }

  private String readJavaCatalog() throws IOException {
    StringBuilder source = new StringBuilder();
    for (Path path : javaFiles()) {
      source.append(Files.readString(path)).append('\n');
    }
    return source.toString();
  }

  private String readResourceCatalog() throws IOException {
    StringBuilder resources = new StringBuilder();
    for (Path path : localeFiles()) {
      resources.append(Files.readString(path)).append('\n');
    }
    return resources.toString();
  }

  private List<Path> javaFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(CONTENT_ROOT)) {
      return paths.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
    }
  }

  private List<Path> localeFiles() throws IOException {
    try (Stream<Path> paths = Files.list(RESOURCE_ROOT)) {
      return paths.filter(path -> path.getFileName().toString().endsWith(".toml")).sorted().toList();
    }
  }

  private Set<String> matches(Pattern pattern, String input, int group) {
    Set<String> values = new HashSet<>();
    Matcher matcher = pattern.matcher(input);
    while (matcher.find()) {
      values.add(matcher.group(group));
    }
    return values;
  }

  private Map<String, Integer> registeredGoals(String source) {
    Map<String, Integer> goals = new HashMap<>();
    Matcher matcher = DIRECT_MILESTONE_PATTERN.matcher(source);
    while (matcher.find()) {
      goals.put(matcher.group(1), Integer.parseInt(matcher.group(3).replace("_", "")));
    }
    return goals;
  }

  private String advancementDescription(String locale, String key) {
    String header = "[advancement." + key + "]";
    int start = locale.indexOf(header);
    if (start < 0) {
      throw new IllegalStateException("Missing advancement locale for " + key);
    }
    int end = locale.indexOf("\n[", start + header.length());
    String section = end < 0 ? locale.substring(start) : locale.substring(start, end);
    Matcher matcher = DESCRIPTION_PATTERN.matcher(section);
    if (!matcher.find()) {
      throw new IllegalStateException("Missing advancement description for " + key);
    }
    return matcher.group(1);
  }
}
