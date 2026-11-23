package art.arcane.adapt.content;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the adaptation cost sheet ({@code docs/adapt-assets/adaptation-costs.csv}) and the generated tables of the
 * progression page ({@code docs/adapt/38-progression-costs.md}) equal to the live registry with default configs. Run
 * with {@code -PupdateAdaptationCosts} to rewrite both after an intended change.
 */
class AdaptationCostSheetTest {
  private static final Path DOCS_ROOT = Path.of(System.getProperty("user.dir")).resolve("../../docs").normalize();
  private static final Path SHEET = DOCS_ROOT.resolve("adapt-assets/adaptation-costs.csv");
  private static final Path PAGE = DOCS_ROOT.resolve("adapt/38-progression-costs.md");
  private static final String BEGIN = "<!-- generated from the live registry: begin -->";
  private static final String END = "<!-- generated from the live registry: end -->";
  private static final String HEADER = "Skill,Adaptation,Description,Max level,Tier,Base cost,Initial cost,Cost factor,"
      + "Level 1 cost,Cost per level,Total cost,Permanent";
  private static final List<String> TIERS = List.of("Minor", "Utility", "Core", "Strong", "Keystone");
  private static final List<String> MULTI_LEVEL = List.of("1/1/0.5", "2/1/0.5", "3/2/0.6", "5/3/0.6", "7/6/0.7");
  private static final List<String> SINGLE_LEVEL = List.of("1/1/1", "2/2/1", "3/4/1", "5/6/1", "8/8/1");
  private static final int[] KNOWLEDGE_LEVELS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 40, 50};
  private static final int[] MASTER_LEVELS = {1, 5, 10, 20, 30, 40, 50, 75, 100};
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
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), CoreContent.skillTypes());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void costSheetAndProgressionPageMatchTheLiveRegistry() throws IOException {
    List<Row> rows = rows();
    List<String> sheet = sheet(rows);
    String page = Files.readString(PAGE, StandardCharsets.UTF_8);
    int begin = page.indexOf(BEGIN);
    int end = page.indexOf(END);
    assertThat(begin).as("generated block start in %s", PAGE).isGreaterThanOrEqualTo(0);
    assertThat(end).as("generated block end in %s", PAGE).isGreaterThan(begin);
    String generated = BEGIN + "\n\n" + pageTables(rows) + "\n" + END;
    if (Boolean.getBoolean("adapt.updateAdaptationCosts")) {
      Files.write(SHEET, sheet, StandardCharsets.UTF_8);
      Files.writeString(PAGE, page.substring(0, begin) + generated + page.substring(end + END.length()),
          StandardCharsets.UTF_8);
      page = Files.readString(PAGE, StandardCharsets.UTF_8);
      begin = page.indexOf(BEGIN);
      end = page.indexOf(END);
    }
    assertThat(Files.readAllLines(SHEET, StandardCharsets.UTF_8))
        .as("%s is out of date; run with -PupdateAdaptationCosts to regenerate", SHEET)
        .containsExactlyElementsOf(sheet);
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
      skills.add(skill.getName());
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

  private String pageTables(List<Row> rows) {
    StringBuilder out = new StringBuilder();
    tierSection(out, rows);
    knowledgeSection(out);
    powerSection(out);
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
    out.append("Each learned adaptation level uses 1 power. Maximum power is the master level times ")
        .append(number(config.getPowerPerLevel())).append(", rounded down. Every skill level a player reaches adds ")
        .append(number(config.getPlayerXpPerSkillLevelUpBase())).append(" master XP plus ")
        .append(number(config.getPlayerXpPerSkillLevelUpLevelMultiplier()))
        .append(" for each level that skill had before. Master levels use the same XP curve as skills.\n\n");
    out.append("| Master level | Total master XP needed | Maximum power |\n");
    out.append("|---|---|---|\n");
    for (int level : MASTER_LEVELS) {
      out.append("| ").append(level).append(" | ").append(number(XP.getXpForLevel(level))).append(" | ")
          .append((int) Math.floor(level * config.getPowerPerLevel())).append(" |\n");
    }
    out.append('\n');
  }

  private void estimateSection(StringBuilder out) {
    out.append("## Estimated time to level\n\n");
    out.append("Estimates only. Each row assumes one hour of the activity shown and the default configuration, ")
        .append("before repeat-location decay, farm limits, XP boosts, and multipliers.\n\n");
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
      names.put(skill.getName(), skillName(skill));
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
