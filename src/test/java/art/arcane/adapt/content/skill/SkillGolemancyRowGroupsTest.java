package art.arcane.adapt.content.skill;

import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGolemancyRowGroupsTest {
  private static final Path GOLEMANCY_SKILL = Path.of("src/main/java/art/arcane/adapt/content/skill/SkillGolemancy.java");
  private static final Path GOLEMANCY_ADAPTATIONS = Path.of("src/main/java/art/arcane/adapt/content/adaptation/golemancy");
  private static final Pattern REGISTRATION = Pattern.compile("registerAdaptation\\(new (\\w+)\\(\\)\\)");
  private static final Pattern ADAPTATION_ID = Pattern.compile("super\\(\"([^\"]+)\"\\)");

  @Test
  void groupsAreFoundationFramesCoresAndAugments() {
    List<List<String>> groups = Mockito.mock(SkillGolemancy.class, Mockito.CALLS_REAL_METHODS).getAdaptationRowGroups();

    assertThat(groups).containsExactly(
        List.of("golem-animus-binding", "golem-node-weaving"),
        List.of("golem-frost-frame", "golem-copper-frame", "golem-iron-frame", "golem-bone-frame"),
        List.of("golem-guardian-core", "golem-gatherer-core", "golem-harvester-core", "golem-lumber-core",
            "golem-breaker-core", "golem-rancher-core", "golem-sorter-core", "golem-angler-core"),
        List.of("golem-runic-augments", "golem-masterwork", "golem-overseer", "golem-mending", "golem-stasis"));
  }

  @Test
  void groupsNameEveryRegisteredAdaptationExactlyOnce() throws IOException {
    List<String> declared = new ArrayList<>();
    for (List<String> group : SkillGolemancy.ROW_GROUPS) {
      declared.addAll(group);
    }

    assertThat(declared).doesNotHaveDuplicates();
    assertThat(declared).containsExactlyInAnyOrderElementsOf(registeredIds());
    assertThat(declared).hasSize(19);
  }

  @Test
  void allNineteenFitOnePageInFourRows() {
    List<List<String>> arranged = GuiGroupLayout.arrange(
        flatten(SkillGolemancy.ROW_GROUPS), name -> name, name -> GuiConfig.UNRANKED,
        Comparator.<String>naturalOrder(), SkillGolemancy.ROW_GROUPS);

    assertThat(arranged).isEqualTo(SkillGolemancy.ROW_GROUPS);
    for (boolean navigation : new boolean[]{false, true}) {
      for (boolean settings : new boolean[]{false, true}) {
        GuiGroupLayout.Plan<String> plan = GuiGroupLayout.plan(arranged, navigation, settings);
        assertThat(plan.pageCount()).isEqualTo(1);
        assertThat(plan.pages().get(0).rows()).extracting(List::size).containsExactly(2, 4, 8, 5);
        assertThat(plan.rows()).isEqualTo(4 + (navigation ? 1 : 0) + (settings ? 1 : 0));
      }
    }
  }

  private static List<String> flatten(List<List<String>> groups) {
    List<String> all = new ArrayList<>();
    for (List<String> group : groups) {
      all.addAll(group);
    }
    return all;
  }

  private static Set<String> registeredIds() throws IOException {
    Set<String> ids = new HashSet<>();
    Matcher registrations = REGISTRATION.matcher(Files.readString(GOLEMANCY_SKILL));
    while (registrations.find()) {
      Path source = GOLEMANCY_ADAPTATIONS.resolve(registrations.group(1) + ".java");
      if (!Files.exists(source)) {
        continue;
      }
      Matcher id = ADAPTATION_ID.matcher(Files.readString(source));
      assertThat(id.find()).as("id of %s", registrations.group(1)).isTrue();
      ids.add(id.group(1));
    }
    return ids;
  }
}
