package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.skill.AdaptationGroup;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The skills of fourteen adaptations or more show them in two to four named groups that hold every registered
 * adaptation exactly once; every other skill shows one plain list. Groups only arrange the pages.
 */
class SkillGroupsTest {
  private static final Path SKILLS = Path.of("src/main/java/art/arcane/adapt/content/skill");
  private static final Path ADAPTATIONS = Path.of("src/main/java/art/arcane/adapt/content/adaptation");
  private static final Pattern REGISTRATION = Pattern.compile("registerAdaptation\\((?:new (\\w+)\\(|(\\w+)\\))");
  private static final Pattern ADAPTATION_ID = Pattern.compile("super\\(\"([^\"]+)\"\\)");
  private static final Map<String, List<AdaptationGroup>> GROUPED = grouped();

  private static Map<String, List<AdaptationGroup>> grouped() {
    Map<String, List<AdaptationGroup>> grouped = new LinkedHashMap<>();
    grouped.put("SkillBlocking", SkillBlocking.GROUPS);
    grouped.put("SkillDiscovery", SkillDiscovery.GROUPS);
    grouped.put("SkillEnchanting", SkillEnchanting.GROUPS);
    grouped.put("SkillGolemancy", SkillGolemancy.GROUPS);
    grouped.put("SkillHerbalism", SkillHerbalism.GROUPS);
    grouped.put("SkillHunter", SkillHunter.GROUPS);
    grouped.put("SkillKinetics", SkillKinetics.GROUPS);
    grouped.put("SkillNether", SkillNether.GROUPS);
    grouped.put("SkillSeaborne", SkillSeaborne.GROUPS);
    grouped.put("SkillStealth", SkillStealth.GROUPS);
    grouped.put("SkillSwords", SkillSwords.GROUPS);
    grouped.put("SkillTaming", SkillTaming.GROUPS);
    grouped.put("SkillTragOul", SkillTragOul.GROUPS);
    return grouped;
  }

  @Test
  void golemancyKeepsItsFourGroupsNowNamed() {
    assertThat(SkillGolemancy.GROUPS).extracting(AdaptationGroup::label).containsExactly(GroupMessages.GOLEMANCY_BINDING,
        GroupMessages.GOLEMANCY_FRAMES, GroupMessages.GOLEMANCY_CORES, GroupMessages.GOLEMANCY_COMMAND);
    assertThat(SkillGolemancy.GROUPS).extracting(AdaptationGroup::adaptations).containsExactly(
        List.of("golem-animus-binding", "golem-node-weaving"),
        List.of("golem-frost-frame", "golem-copper-frame", "golem-iron-frame", "golem-bone-frame"),
        List.of("golem-guardian-core", "golem-gatherer-core", "golem-harvester-core", "golem-lumber-core",
            "golem-breaker-core", "golem-rancher-core", "golem-sorter-core", "golem-angler-core"),
        List.of("golem-runic-augments", "golem-masterwork", "golem-overseer", "golem-mending", "golem-stasis"));
  }

  @Test
  void everySkillOfFourteenOrMoreIsGroupedAndNoSmallerOneIs() throws IOException {
    try (Stream<Path> files = Files.list(SKILLS)) {
      for (Path file : files.filter((Path path) -> path.getFileName().toString().matches("Skill[A-Z]\\w+\\.java")).toList()) {
        String name = file.getFileName().toString().replace(".java", "");
        String source = Files.readString(file);
        if (!source.contains("registerAdaptation(")) {
          continue;
        }
        Set<String> registered = registered(source);
        if (registered.size() >= 14) {
          assertThat(GROUPED).as(name).containsKey(name);
        } else {
          assertThat(GROUPED).as(name).doesNotContainKey(name);
          assertThat(source).as(name).doesNotContain("getAdaptationGroups");
        }
        List<AdaptationGroup> groups = GROUPED.get(name);
        if (groups == null) {
          continue;
        }
        assertThat(groups).as(name).hasSizeBetween(2, 4);
        List<String> declared = new ArrayList<>();
        for (AdaptationGroup group : groups) {
          assertThat(group.adaptations()).as(name).isNotEmpty();
          declared.addAll(group.adaptations());
        }
        assertThat(declared).as(name).doesNotHaveDuplicates();
        assertThat(declared).as(name).containsExactlyInAnyOrderElementsOf(registered);
      }
    }
  }

  @Test
  void everyGroupNameIsUsedOnce() {
    List<TextKey> used = new ArrayList<>();
    for (List<AdaptationGroup> groups : GROUPED.values()) {
      for (AdaptationGroup group : groups) {
        used.add(group.label());
      }
    }
    assertThat(used).doesNotHaveDuplicates();
    assertThat(used).containsExactlyInAnyOrderElementsOf(GroupMessages.ALL);
  }

  @Test
  void golemancysChestPageStillHoldsItsFourGroupsInFourRows() {
    List<List<String>> rows = new ArrayList<>();
    for (AdaptationGroup group : SkillGolemancy.GROUPS) {
      rows.add(group.adaptations());
    }
    List<String> all = new ArrayList<>();
    rows.forEach(all::addAll);
    List<List<String>> arranged = GuiGroupLayout.arrange(all, (String name) -> name, (String name) -> GuiConfig.UNRANKED,
        Comparator.<String>naturalOrder(), rows);
    assertThat(arranged).isEqualTo(rows);
    for (boolean navigation : new boolean[]{false, true}) {
      for (boolean settings : new boolean[]{false, true}) {
        GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(arranged, navigation, settings);
        assertThat(plan.pageCount()).isEqualTo(1);
        assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(2, 4, 8, 5);
      }
    }
  }

  /** The ids the skill's source registers, read from each adaptation's own source. */
  private static Set<String> registered(String source) throws IOException {
    Map<String, String> variables = new HashMap<>();
    Matcher declared = Pattern.compile("(\\w+) (\\w+) = new (\\w+)\\(").matcher(source);
    while (declared.find()) {
      variables.put(declared.group(2), declared.group(3));
    }
    Set<String> ids = new HashSet<>();
    Matcher registrations = REGISTRATION.matcher(source);
    while (registrations.find()) {
      String type = registrations.group(1) != null ? registrations.group(1) : variables.get(registrations.group(2));
      assertThat(type).as(registrations.group()).isNotNull();
      Path file;
      try (Stream<Path> found = Files.walk(ADAPTATIONS)) {
        file = found.filter((Path path) -> path.getFileName().toString().equals(type + ".java")).findFirst().orElse(null);
      }
      assertThat(file).as(type).isNotNull();
      Matcher id = ADAPTATION_ID.matcher(Files.readString(file));
      assertThat(id.find()).as("id of %s", type).isTrue();
      ids.add(id.group(1));
    }
    return ids;
  }
}
