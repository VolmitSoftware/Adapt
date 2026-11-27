package art.arcane.adapt.content;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the adaptation cost sheet ({@code docs/adapt-assets/adaptation-costs.csv}) and the generated tables of the
 * progression page ({@code docs/adapt/38-progression-costs.md}) equal to the live registry with default configs. Run
 * with {@code -PupdateAdaptationCosts} to rewrite both after an intended change.
 */
class AdaptationCostSheetTest {
  private static final Path DOCS_ROOT = Path.of(System.getProperty("user.dir")).resolve("../../docs").normalize();
  private static final Path SHEET = DOCS_ROOT.resolve("adapt-assets/adaptation-costs.csv");
  private static final Path TIMES = DOCS_ROOT.resolve("adapt-assets/progression-times.csv");
  private static final int MAX_SIMULATED_HOURS = 5000;
  private static final int[] SLOT_TARGETS = {1, 2, 3, 4, 5, 6, 8, 10, 12};
  private static final int[] SNAPSHOT_HOURS = {7, 20, 50, 100, 200, 500};
  /** Player profiles for the milestone tables: name, skill XP per hour, and how it splits across skills. */
  private static final List<Profile> PROFILES = List.of(
      new Profile("Ordinary", 4500D, new double[]{0.30D, 0.25D, 0.15D, 0.10D, 0.10D, 0.10D}),
      new Profile("Focused", 7000D, new double[]{0.70D, 0.10D, 0.10D, 0.10D}));
  private static final Path PAGE = DOCS_ROOT.resolve("adapt/38-progression-costs.md");
  private static final String BEGIN = "<!-- generated from the live registry: begin -->";
  private static final String END = "<!-- generated from the live registry: end -->";
  private static final String HEADER = "Skill,Adaptation,Description,Max level,Tier,Base cost,Initial cost,Cost factor,"
      + "Level 1 cost,Cost per level,Total cost,Permanent";
  private static final List<String> TIERS = List.of("Minor", "Utility", "Core", "Strong", "Keystone");
  private static final List<String> MULTI_LEVEL = List.of("1/1/0.5", "2/1/0.5", "3/2/0.6", "5/3/0.6", "7/6/0.7");
  private static final List<String> SINGLE_LEVEL = List.of("1/1/1", "2/2/1", "3/4/1", "5/6/1", "8/8/1");
  private static final int[] KNOWLEDGE_LEVELS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 40, 50};
  private static final int[] MASTER_LEVELS = {1, 3, 6, 10, 15, 25, 40, 60, 100};
  private static final int[] ESTIMATE_LEVELS = {5, 10, 25, 50};

  /** XP per hour assumptions for the estimate table: skill id, activity, ordinary XP/h, focused XP/h. */
  private static final List<Estimate> ESTIMATES = List.of(
      new Estimate("agility", "Ordinary: 2,000 blocks on foot, a quarter sprinting. Focused: sprinting all hour.", 1200, 6500),
      new Estimate("architect", "Ordinary: 200 blocks placed. Focused: 1,300 blocks placed.", 900, 6000),
      new Estimate("axes", "Ordinary: 60 logs and 20 axe hits. Focused: 600 logs.", 1300, 3600),
      new Estimate("blocking", "Ordinary: 30 hits blocked. Focused: 200 hits blocked in a fight.", 900, 6000),
      new Estimate("brewing", "Ordinary: 5 potions used. Focused: 40 potions used.", 500, 4000),
      new Estimate("chronos", "Ordinary: Moving most of the hour. Focused: the same with a clock in the off hand.", 3000, 15000),
      new Estimate("crafting", "Ordinary: 30 crafts. Focused: 400 crafts of mid-value items.", 300, 4000),
      new Estimate("discovery", "Ordinary: 150 new chunks, 3 untouched chests, 20 new things. Focused: 1,500 chunks, 15 chests, 60 new things.", 900, 5000),
      new Estimate("enchanting", "Ordinary: 2 table enchants. Focused: 40 table enchants.", 300, 6000),
      new Estimate("excavation", "Ordinary: 200 blocks dug. Focused: 2,500 blocks dug.", 400, 5000),
      new Estimate("golemancy", "Ordinary: One golem working. Focused: several golems building, binding, and working.", 500, 3000),
      new Estimate("herbalism", "Ordinary: 40 mature crops and 10 meals. Focused: 250 mature crops.", 1700, 8750),
      new Estimate("hunter", "Ordinary: 15 natural mob kills. Focused: 80 natural mob kills.", 1650, 8800),
      new Estimate("kinetics", "Ordinary: A few falls and knockbacks. Focused: mace or spear combat.", 300, 3500),
      new Estimate("nether", "Ordinary: 5 Nether mobs and 10 quartz ore. Focused: 30 Nether mobs and 60 quartz ore.", 800, 4700),
      new Estimate("pickaxe", "Ordinary: 300 stone and 10 ores. Focused: 1,500 blocks and 40 ores.", 2700, 12000),
      new Estimate("ranged", "Ordinary: 40 shots, 20 hits, 5 kills. Focused: 300 shots, 150 hits, 40 kills.", 1100, 7000),
      new Estimate("rift", "Ordinary: 5 pearls and 2 portal trips. Focused: 60 pearls.", 400, 4500),
      new Estimate("seaborne", "Ordinary: 2 minutes swimming and 3 fish. Focused: fishing all hour.", 600, 7000),
      new Estimate("stealth", "Ordinary: 10 unaware openers. Focused: 30 sneaking openers and 30 sneaking kills.", 1600, 9000),
      new Estimate("swords", "Ordinary: 15 kills with a sword. Focused: 80 kills with a sword.", 2400, 12800),
      new Estimate("taming", "Ordinary: 4 breedings and 3 pet kills. Focused: 30 breedings and pet combat.", 300, 3500),
      new Estimate("tragoul", "Ordinary: 60 health taken. Focused: 300 health taken in fights.", 860, 5000),
      new Estimate("unarmed", "Ordinary: 3 kills bare-handed. Focused: 50 kills bare-handed.", 1200, 20000));

  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void costSheetAndProgressionPageMatchTheLiveRegistry() throws IOException {
    Assumptions.assumeTrue(Files.isRegularFile(SHEET) && Files.isRegularFile(TIMES) && Files.isRegularFile(PAGE),
        "docs repo is not checked out beside Adapt");
    List<Row> rows = rows();
    List<String> sheet = sheet(rows);
    String page = Files.readString(PAGE, StandardCharsets.UTF_8);
    int begin = page.indexOf(BEGIN);
    int end = page.indexOf(END);
    assertThat(begin).as("generated block start in %s", PAGE).isGreaterThanOrEqualTo(0);
    assertThat(end).as("generated block end in %s", PAGE).isGreaterThan(begin);
    List<String[]> milestones = milestoneRows(rows);
    List<String> times = new ArrayList<>();
    times.add("Table,Milestone,Ordinary,Focused");
    for (String[] row : milestones) {
      times.add(quote(row[0]) + "," + quote(row[1]) + "," + quote(row[2]) + "," + quote(row[3]));
    }
    String generated = BEGIN + "\n\n" + pageTables(rows, milestones) + "\n" + END;
    if (Boolean.getBoolean("adapt.updateAdaptationCosts")) {
      Files.write(SHEET, sheet, StandardCharsets.UTF_8);
      Files.write(TIMES, times, StandardCharsets.UTF_8);
      Files.writeString(PAGE, page.substring(0, begin) + generated + page.substring(end + END.length()),
          StandardCharsets.UTF_8);
      page = Files.readString(PAGE, StandardCharsets.UTF_8);
      begin = page.indexOf(BEGIN);
      end = page.indexOf(END);
    }
    assertThat(Files.readAllLines(SHEET, StandardCharsets.UTF_8))
        .as("%s is out of date; run with -PupdateAdaptationCosts to regenerate", SHEET)
        .containsExactlyElementsOf(sheet);
    assertThat(Files.readAllLines(TIMES, StandardCharsets.UTF_8))
        .as("%s is out of date; run with -PupdateAdaptationCosts to regenerate", TIMES)
        .containsExactlyElementsOf(times);
    assertThat(page.substring(begin, end + END.length()))
        .as("%s is out of date; run with -PupdateAdaptationCosts to regenerate", PAGE)
        .isEqualTo(generated);
  }

  @Test
  void everyAdaptationUsesOneOfTheFiveCostTiers() {
    assertThat(rows()).allSatisfy(row -> assertThat(TIERS).contains(row.tier()));
  }

  @Test
  void everySkillHasATimeEstimate() {
    List<String> skills = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      skills.add(skill.getId().path());
    }
    assertThat(ESTIMATES).extracting(Estimate::skill).containsExactlyInAnyOrderElementsOf(skills);
  }

  private List<Skill<?>> skills() {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkills();
  }

  private List<Row> rows() {
    List<Row> rows = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        rows.add(Row.of(skillName(skill), adaptation));
      }
    }
    rows.sort(Comparator.comparing(Row::skill)
        .thenComparing((Row row) -> tierRank(row.tier()))
        .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
    return rows;
  }

  private static List<String> sheet(List<Row> rows) {
    List<String> lines = new ArrayList<>();
    lines.add(HEADER);
    for (Row row : rows) {
      lines.add(row.csv());
    }
    return lines;
  }

  private String pageTables(List<Row> rows, List<String[]> milestones) {
    StringBuilder out = new StringBuilder();
    tierSection(out, rows);
    knowledgeSection(out);
    powerSection(out);
    milestoneSection(out, milestones);
    estimateSection(out);
    adaptationSection(out, rows);
    return out.toString();
  }

  private static void tierSection(StringBuilder out, List<Row> rows) {
    out.append("## Cost tiers\n\n");
    out.append("Every adaptation belongs to one of five tiers. The tier sets its knowledge price per level.\n\n");
    out.append("| Tier | Level 1 | Level 2 | Level 3 | Level 4 | Level 5 | Levels 1-5 total | Single-level adaptation |\n");
    out.append("|---|---|---|---|---|---|---|---|\n");
    for (int index = 0; index < TIERS.size(); index++) {
      String tier = TIERS.get(index);
      Row multi = example(rows, tier, false);
      Row single = example(rows, tier, true);
      out.append("| ").append(tier);
      long total = 0L;
      for (int level = 1; level <= 5; level++) {
        if (multi == null) {
          out.append(" | -");
        } else {
          int cost = multi.adaptation().getCostFor(level);
          total += cost;
          out.append(" | ").append(cost);
        }
      }
      out.append(" | ").append(multi == null ? "-" : Long.toString(total));
      out.append(" | ").append(single == null ? "-" : Integer.toString(single.adaptation().getCostFor(1))).append(" |\n");
    }
    out.append('\n');
  }

  private static Row example(List<Row> rows, String tier, boolean single) {
    for (Row row : rows) {
      if (row.tier().equals(tier) && (row.maxLevel() == 1) == single) {
        return row;
      }
    }
    return null;
  }

  private static void knowledgeSection(StringBuilder out) {
    out.append("## Knowledge and XP by skill level\n\n");
    out.append("| Skill level | Knowledge for reaching it | Knowledge earned in total | Total skill XP needed |\n");
    out.append("|---|---|---|---|\n");
    for (int level : KNOWLEDGE_LEVELS) {
      long total = 0L;
      for (int left = 0; left < level; left++) {
        total += PlayerSkillLine.knowledgeForLevelUp(left);
      }
      out.append("| ").append(level).append(" | ").append(PlayerSkillLine.knowledgeForLevelUp(level - 1)).append(" | ")
          .append(total).append(" | ").append(number(XP.getXpForLevel(level))).append(" |\n");
    }
    out.append('\n');
  }

  private static void powerSection(StringBuilder out) {
    AdaptConfig config = AdaptConfig.get();
    out.append("## Power\n\n");
    out.append("Power limits how many adaptations a player holds at once. Every learned adaptation uses 1 power, ")
        .append("whatever its level; raising a held adaptation further costs knowledge only. Maximum power is ")
        .append(number(config.getMaxPowerScale())).append(" times the square root of the master level, rounded down. ")
        .append("Every skill level a player reaches adds ").append(number(config.getPlayerXpPerSkillLevelUpBase()))
        .append(" master XP plus ").append(number(config.getPlayerXpPerSkillLevelUpLevelMultiplier()))
        .append(" for each level that skill had before. Master levels use the same XP curve as skills.\n\n");
    out.append("| Master level | Total master XP needed | Adaptations held at most |\n");
    out.append("|---|---|---|\n");
    for (int level : MASTER_LEVELS) {
      out.append("| ").append(level).append(" | ").append(number(XP.getXpForLevel(level))).append(" | ")
          .append(PlayerData.maxPowerFor(level, config.getMaxPowerScale())).append(" |\n");
    }
    out.append('\n');
  }


  private static void milestoneSection(StringBuilder out, List<String[]> milestones) {
    AdaptConfig config = AdaptConfig.get();
    out.append("## How long milestones take\n\n");
    out.append("Estimates only, simulated hour by hour with the default XP curve, knowledge, and power rules. ")
        .append("The same rows are in [progression-times.csv](/adapt-assets/progression-times.csv).\n\n");
    out.append("### Assumptions\n\n");
    out.append("| Player | Skill XP per hour | Split across skills | Main skill |\n");
    out.append("|---|---|---|---|\n");
    for (Profile profile : PROFILES) {
      List<String> split = new ArrayList<>();
      for (double share : profile.shares()) {
        split.add(Math.round(share * 100D) + "%");
      }
      out.append("| ").append(profile.name()).append(" | ").append(number(profile.xpPerHour())).append(" | ")
          .append(String.join(" / ", split)).append(" | the ").append(Math.round(profile.shares()[0] * 100D))
          .append("% skill |\n");
    }
    out.append("\nEach skill level reached adds ").append(number(config.getPlayerXpPerSkillLevelUpBase()))
        .append(" master XP plus ").append(number(config.getPlayerXpPerSkillLevelUpLevelMultiplier()))
        .append(" for each level the skill had before. Knowledge counts only the main skill.\n\n");
    String table = null;
    for (String[] row : milestones) {
      if (!row[0].equals(table)) {
        if (table != null) {
          out.append('\n');
        }
        table = row[0];
        out.append("### ").append(table).append("\n\n");
        out.append("| ").append(table.equals("Snapshot") ? "After" : "Milestone").append(" | Ordinary | Focused |\n");
        out.append("|---|---|---|\n");
      }
      out.append("| ").append(row[1]).append(" | ").append(row[2]).append(" | ").append(row[3]).append(" |\n");
    }
    out.append('\n');
  }

  private static List<String[]> milestoneRows(List<Row> rows) {
    List<Simulation> simulations = new ArrayList<>();
    for (Profile profile : PROFILES) {
      simulations.add(new Simulation(profile));
    }
    List<String[]> out = new ArrayList<>();
    for (int slots : SLOT_TARGETS) {
      String label = slots == 1 ? "1 adaptation" : slots + " adaptations";
      List<String> cells = new ArrayList<>();
      for (Simulation simulation : simulations) {
        Integer hour = simulation.firstHour(state -> state.maxPower() >= slots);
        cells.add(hour == null ? "-" : hours(hour));
      }
      out.add(new String[]{"Adaptations held", label, cells.get(0), cells.get(1)});
    }
    Map<String, Long> goals = new LinkedHashMap<>();
    for (String tier : TIERS) {
      Row example = example(rows, tier, false);
      if (example != null) {
        goals.put("First level of a " + tier + " adaptation", (long) example.adaptation().getCostFor(1));
      }
    }
    for (String tier : List.of("Core", "Strong", "Keystone")) {
      Row example = example(rows, tier, false);
      if (example != null) {
        goals.put("A " + tier + " adaptation at level 5", fiveLevels(example));
      }
    }
    Row core = example(rows, "Core", false);
    if (core != null) {
      goals.put("Three Core adaptations at level 5", 3L * fiveLevels(core));
    }
    for (Map.Entry<String, Long> goal : goals.entrySet()) {
      List<String> cells = new ArrayList<>();
      for (Simulation simulation : simulations) {
        Integer hour = simulation.firstHour(state -> state.mainKnowledge() >= goal.getValue());
        cells.add(hour == null ? "-" : hours(hour) + " (main skill level " + simulation.at(hour).levels()[0] + ")");
      }
      out.add(new String[]{"Knowledge in the main skill", goal.getKey() + " (" + goal.getValue() + " knowledge)",
          cells.get(0), cells.get(1)});
    }
    for (int hour : SNAPSHOT_HOURS) {
      List<String> cells = new ArrayList<>();
      for (Simulation simulation : simulations) {
        State state = simulation.at(hour);
        List<String> levels = new ArrayList<>();
        for (int level : state.levels()) {
          levels.add(Integer.toString(level));
        }
        cells.add("skills " + String.join(" / ", levels) + "; master level " + state.masterLevel() + "; "
            + state.maxPower() + " adaptations; " + state.mainKnowledge() + " knowledge");
      }
      out.add(new String[]{"Snapshot", hours(hour), cells.get(0), cells.get(1)});
    }
    return out;
  }

  private static long fiveLevels(Row row) {
    long total = 0L;
    for (int level = 1; level <= 5; level++) {
      total += row.adaptation().getCostFor(level);
    }
    return total;
  }

  private static String hours(int hours) {
    return String.format(Locale.ROOT, "%,d h", hours);
  }

  private record Profile(String name, double xpPerHour, double[] shares) {
  }

  private record State(int[] levels, int masterLevel, int maxPower, long mainKnowledge) {
  }

  private static final class Simulation {
    private final Profile profile;
    private final Map<Integer, State> states = new LinkedHashMap<>();

    private Simulation(Profile profile) {
      this.profile = profile;
    }

    private State at(int hour) {
      return states.computeIfAbsent(hour, this::simulate);
    }

    private Integer firstHour(Predicate<State> condition) {
      for (int hour = 1; hour <= MAX_SIMULATED_HOURS; hour++) {
        if (condition.test(at(hour))) {
          return hour;
        }
      }
      return null;
    }

    private State simulate(int hour) {
      AdaptConfig config = AdaptConfig.get();
      int[] levels = new int[profile.shares().length];
      double masterXp = 0D;
      for (int index = 0; index < levels.length; index++) {
        levels[index] = (int) Math.floor(XP.getLevelForXp(profile.xpPerHour() * profile.shares()[index] * hour));
        for (int left = 0; left < levels[index]; left++) {
          masterXp += config.getPlayerXpPerSkillLevelUpBase() + left * config.getPlayerXpPerSkillLevelUpLevelMultiplier();
        }
      }
      double masterLevel = XP.getLevelForXp(masterXp);
      long knowledge = 0L;
      for (int left = 0; left < levels[0]; left++) {
        knowledge += PlayerSkillLine.knowledgeForLevelUp(left);
      }
      return new State(levels, (int) Math.floor(masterLevel), PlayerData.maxPowerFor(masterLevel, config.getMaxPowerScale()),
          knowledge);
    }
  }

  private void estimateSection(StringBuilder out) {
    out.append("## Estimated time to level one skill\n\n");
    out.append("Estimates only. Each row assumes every hour is spent on that skill's activity as shown, with the default ")
        .append("configuration, before repeat-location decay, farm limits, XP boosts, and multipliers.\n\n");
    out.append("| Skill | Assumed hour | XP per hour (ordinary / focused) |");
    for (int level : ESTIMATE_LEVELS) {
      out.append(" Level ").append(level).append(" (ordinary / focused) |");
    }
    out.append('\n').append("|---|---|---|");
    for (int ignored : ESTIMATE_LEVELS) {
      out.append("---|");
    }
    out.append('\n');
    Map<String, String> names = new LinkedHashMap<>();
    for (Skill<?> skill : skills()) {
      names.put(skill.getId().path(), skillName(skill));
    }
    List<Estimate> sorted = new ArrayList<>(ESTIMATES);
    sorted.sort(Comparator.comparing((Estimate estimate) -> names.get(estimate.skill())));
    for (Estimate estimate : sorted) {
      out.append("| ").append(names.get(estimate.skill())).append(" | ").append(estimate.activity()).append(" | ")
          .append(number(estimate.ordinary())).append(" / ").append(number(estimate.focused())).append(" |");
      for (int level : ESTIMATE_LEVELS) {
        double xp = XP.getXpForLevel(level);
        out.append(' ').append(duration(xp / estimate.ordinary())).append(" / ").append(duration(xp / estimate.focused()))
            .append(" |");
      }
      out.append('\n');
    }
    out.append('\n');
  }

  private static void adaptationSection(StringBuilder out, List<Row> rows) {
    out.append("## Adaptations\n\n");
    out.append("The same data is available as a spreadsheet: [adaptation-costs.csv](/adapt-assets/adaptation-costs.csv).\n\n");
    String skill = null;
    for (Row row : rows) {
      if (!row.skill().equals(skill)) {
        if (skill != null) {
          out.append('\n');
        }
        skill = row.skill();
        out.append("### ").append(skill).append("\n\n");
        out.append("| Adaptation | Description | Max level | Tier | Base cost | Initial cost | Cost factor | Level 1 cost | Cost per level | Total cost | Permanent |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
      }
      out.append(row.markdown()).append('\n');
    }
  }

  static String tierOf(int maxLevel, int baseCost, int initialCost, double costFactor) {
    String triple = baseCost + "/" + initialCost + "/" + factor(costFactor);
    int index = (maxLevel == 1 ? SINGLE_LEVEL : MULTI_LEVEL).indexOf(triple);
    return index < 0 ? "Custom" : TIERS.get(index);
  }

  private static int tierRank(String tier) {
    int index = TIERS.indexOf(tier);
    return index < 0 ? TIERS.size() : index;
  }

  private static String factor(double value) {
    return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
  }

  private static String number(double value) {
    return value == Math.rint(value) ? String.format(Locale.ROOT, "%,d", (long) value) : Double.toString(value);
  }

  private static String duration(double hours) {
    if (hours < 1D) {
      return Math.max(1L, Math.round(hours * 60D)) + " min";
    }
    if (hours < 10D) {
      return String.format(Locale.ROOT, "%.1f h", hours);
    }
    return String.format(Locale.ROOT, "%,d h", Math.round(hours));
  }

  private static String skillName(Skill<?> skill) {
    return clean(skill.getDisplayName()).replaceFirst("^[^A-Za-z0-9]+", "");
  }

  private static String clean(String text) {
    return text == null ? "" : C.stripColor(text).replaceAll("\\s+", " ").trim();
  }

  private static String quote(String field) {
    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
      return "\"" + field.replace("\"", "\"\"") + "\"";
    }
    return field;
  }

  private static String cell(String field) {
    return field.replace("|", "\\|");
  }

  private record Estimate(String skill, String activity, double ordinary, double focused) {
  }

  private record Row(String skill, Adaptation<?> adaptation, String name, String description, int maxLevel, String tier,
                     List<Integer> costs) {
    static Row of(String skill, Adaptation<?> adaptation) {
      adaptation.getConfig();
      List<Integer> costs = new ArrayList<>();
      for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
        costs.add(adaptation.getCostFor(level));
      }
      return new Row(skill, adaptation, clean(adaptation.getDisplayName()), clean(adaptation.getDescription()),
          adaptation.getMaxLevel(),
          tierOf(adaptation.getMaxLevel(), adaptation.getBaseCost(), adaptation.getInitialCost(), adaptation.getCostFactor()),
          List.copyOf(costs));
    }

    private long total() {
      long total = 0L;
      for (int cost : costs) {
        total += cost;
      }
      return total;
    }

    private String perLevel() {
      List<String> perLevel = new ArrayList<>();
      for (int cost : costs) {
        perLevel.add(Integer.toString(cost));
      }
      return String.join(", ", perLevel);
    }

    private String permanent() {
      return adaptation.isPermanent() ? "yes" : "no";
    }

    String csv() {
      return String.join(",", quote(skill), quote(name), quote(description), Integer.toString(maxLevel), tier,
          Integer.toString(adaptation.getBaseCost()), Integer.toString(adaptation.getInitialCost()),
          factor(adaptation.getCostFactor()), Integer.toString(costs.getFirst()), quote(perLevel()),
          Long.toString(total()), permanent());
    }

    String markdown() {
      return "| " + cell(name) + " | " + cell(description) + " | " + maxLevel + " | " + tier + " | "
          + adaptation.getBaseCost() + " | " + adaptation.getInitialCost() + " | " + factor(adaptation.getCostFactor())
          + " | " + costs.getFirst() + " | " + perLevel() + " | " + total() + " | " + permanent() + " |";
    }
  }
}
