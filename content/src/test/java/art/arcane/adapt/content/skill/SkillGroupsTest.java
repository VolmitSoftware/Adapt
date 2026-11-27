package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The skills of fourteen adaptations or more show them in two to four named groups that hold every registered
 * adaptation exactly once; every other skill shows one plain list. Groups only arrange the pages.
 */
class SkillGroupsTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void golemancyKeepsItsFourGroupsNowNamed() {
    List<AdaptationGroup> groups = skill("golemancy").getAdaptationGroups();
    assertThat(groups).extracting((AdaptationGroup group) -> group.label().resolve()).containsExactly(
        Text.key("adapt.skill.golemancy.group.binding").resolve(), Text.key("adapt.skill.golemancy.group.frames").resolve(),
        Text.key("adapt.skill.golemancy.group.cores").resolve(), Text.key("adapt.skill.golemancy.group.command").resolve());
    assertThat(rows(groups)).containsExactly(
        List.of("adapt:golem/animus_binding", "adapt:golem/node_weaving"),
        List.of("adapt:golem/frost_frame", "adapt:golem/copper_frame", "adapt:golem/iron_frame", "adapt:golem/bone_frame"),
        List.of("adapt:golem/guardian_core", "adapt:golem/gatherer_core", "adapt:golem/harvester_core", "adapt:golem/lumber_core",
            "adapt:golem/breaker_core", "adapt:golem/rancher_core", "adapt:golem/sorter_core", "adapt:golem/angler_core"),
        List.of("adapt:golem/runic_augments", "adapt:golem/masterwork", "adapt:golem/overseer", "adapt:golem/mending", "adapt:golem/stasis"));
  }

  @Test
  void everySkillOfFourteenOrMoreIsGroupedAndNoSmallerOneIs() {
    for (Skill<?> skill : skills()) {
      String name = skill.getId().toString();
      List<AdaptationGroup> groups = skill.getAdaptationGroups();
      Set<String> registered = new LinkedHashSet<>();
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        registered.add(adaptation.getId().toString());
      }
      if (registered.size() < 14) {
        assertThat(groups).as(name).isEmpty();
        continue;
      }
      assertThat(groups).as(name).hasSizeBetween(2, 4);
      List<String> declared = new ArrayList<>();
      for (List<String> row : rows(groups)) {
        assertThat(row).as(name).isNotEmpty();
        declared.addAll(row);
      }
      assertThat(declared).as(name).doesNotHaveDuplicates();
      assertThat(declared).as(name).containsExactlyInAnyOrderElementsOf(registered);
    }
  }

  @Test
  void everyGroupNameIsUsedOnce() {
    List<String> used = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      for (AdaptationGroup group : skill.getAdaptationGroups()) {
        used.add(skill.getId() + ": " + group.label().resolve());
      }
    }
    assertThat(used).doesNotHaveDuplicates();
    assertThat(used).hasSameSizeAs(AdaptLanguage.builtInCatalog().ids().stream()
        .filter((String id) -> id.matches("adapt\\.skill\\.[a-z]+\\.group\\.[a-z_]+")).toList());
  }

  @Test
  void golemancysChestPageStillHoldsItsFourGroupsInFourRows() {
    List<List<String>> rows = rows(skill("golemancy").getAdaptationGroups());
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

  private static List<List<String>> rows(List<AdaptationGroup> groups) {
    List<List<String>> rows = new ArrayList<>();
    for (AdaptationGroup group : groups) {
      List<String> row = new ArrayList<>();
      for (Id id : group.adaptations()) {
        row.add(id.toString());
      }
      rows.add(row);
    }
    return rows;
  }

  private static List<Skill<?>> skills() {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkills();
  }

  private static Skill<?> skill(String path) {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkill(Id.adapt(path));
  }
}
