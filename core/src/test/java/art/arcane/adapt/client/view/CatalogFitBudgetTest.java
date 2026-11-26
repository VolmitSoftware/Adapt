package art.arcane.adapt.client.view;

import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native client screens are proven to fit without scrolling against a generated catalog with these maxima
 * (client-common screen/MaxCatalog and CompactFitTest). This test keeps the real catalog inside them on every version:
 * when content grows past a number here, raise it in both places and check the compact layout again. Besides each
 * single maximum, {@code adaptationRows} caps how many lines one adaptation page needs: its stat lines, its plain
 * switches three to a line, one line per choice preference and one per fifteen toggle-group entries.
 */
class CatalogFitBudgetTest {
  static final Map<String, Integer> BUDGET = budget();
  private static final int SWITCH_ROW = 3;
  private static final int GROUP_ROW = 15;

  @TempDir
  Path data;

  private static Map<String, Integer> budget() {
    Map<String, Integer> budget = new LinkedHashMap<>();
    budget.put("skills", 24);
    budget.put("adaptationsPerSkill", 19);
    budget.put("rowGroups", 4);
    budget.put("rowGroupSize", 8);
    budget.put("skillPreferences", 1);
    budget.put("levels", 8);
    budget.put("statLinesPerLevel", 9);
    budget.put("statLineChars", 114);
    budget.put("requirements", 2);
    budget.put("adaptationNameChars", 25);
    budget.put("preferences", 24);
    budget.put("ungroupedToggles", 19);
    budget.put("choicePreferences", 4);
    budget.put("adaptationRows", 13);
    budget.put("choices", 13);
    budget.put("preferenceGroups", 1);
    budget.put("groupEntries", 20);
    budget.put("preferenceLabelChars", 37);
    budget.put("groupLabelChars", 9);
    return budget;
  }

  @Test
  void theRealCatalogStaysInsideTheCompactLayoutBudget() {
    for (FakeGameContent game : List.of(FakeGameContent.v1_20_1(), FakeGameContent.v1_21_1(), FakeGameContent.v26_1_2(),
        FakeGameContent.v26_3())) {
      TestPlatform platform = TestPlatform.builder(data.resolve(game.version().toString())).build();
      platform.host().runGame(game);
      try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
        CatalogView catalog = CatalogViews.build(adapt.core().getAdaptServer().getSkillRegistry(), UUID.randomUUID(), List.of(), false);
        Map<String, String> owners = new LinkedHashMap<>();
        Map<String, Integer> measured = measure(catalog, owners);
        List<String> over = new ArrayList<>();
        for (Map.Entry<String, Integer> limit : BUDGET.entrySet()) {
          int value = measured.get(limit.getKey());
          if (value > limit.getValue()) {
            over.add(limit.getKey() + " " + value + " > " + limit.getValue() + " (" + owners.get(limit.getKey()) + ")");
          }
        }
        assertThat(over).as("over the compact layout budget on " + game.version()).isEmpty();
      }
    }
  }

  private static Map<String, Integer> measure(CatalogView catalog, Map<String, String> owners) {
    Map<String, Integer> measured = new LinkedHashMap<>();
    for (String key : BUDGET.keySet()) {
      measured.put(key, 0);
    }
    raise(measured, owners, "catalog", "skills", catalog.skills().size());
    for (SkillView skill : catalog.skills()) {
      raise(measured, owners, skill.id(), "adaptationsPerSkill", skill.adaptations().size());
      raise(measured, owners, skill.id(), "rowGroups", skill.rowGroups().size());
      raise(measured, owners, skill.id(), "skillPreferences", skill.preferences().size());
      for (List<String> row : skill.rowGroups()) {
        raise(measured, owners, skill.id(), "rowGroupSize", row.size());
      }
      for (AdaptationView adaptation : skill.adaptations()) {
        measureAdaptation(measured, owners, adaptation);
      }
    }
    return measured;
  }

  private static void measureAdaptation(Map<String, Integer> measured, Map<String, String> owners, AdaptationView adaptation) {
    raise(measured, owners, adaptation.id(), "levels", adaptation.maxLevel());
    raise(measured, owners, adaptation.id(), "requirements", adaptation.requirements().size());
    raise(measured, owners, adaptation.id(), "adaptationNameChars", plain(adaptation.name()).length());
    raise(measured, owners, adaptation.id(), "preferences", adaptation.preferences().size());
    raise(measured, owners, adaptation.id(), "preferenceGroups", adaptation.preferenceGroups().size());
    for (List<String> lines : adaptation.statLines()) {
      raise(measured, owners, adaptation.id(), "statLinesPerLevel", lines.size());
      for (String line : lines) {
        raise(measured, owners, adaptation.id(), "statLineChars", plain(line).length());
      }
    }
    Set<String> grouped = new HashSet<>();
    for (PreferenceGroupView group : adaptation.preferenceGroups()) {
      raise(measured, owners, adaptation.id(), "groupEntries", group.entries().size());
      raise(measured, owners, adaptation.id(), "groupLabelChars", plain(group.label()).length());
      for (PreferenceGroupView.Entry entry : group.entries()) {
        grouped.add(entry.preferenceId());
      }
    }
    int toggles = 0;
    int choicePreferences = 0;
    for (PreferenceView preference : adaptation.preferences()) {
      raise(measured, owners, adaptation.id(), "preferenceLabelChars", plain(preference.label()).length());
      raise(measured, owners, adaptation.id(), "choices", preference.choices().size());
      if (grouped.contains(preference.id())) {
        continue;
      }
      if (preference.choices().size() > 2) {
        choicePreferences++;
      } else {
        toggles++;
      }
    }
    raise(measured, owners, adaptation.id(), "ungroupedToggles", toggles);
    raise(measured, owners, adaptation.id(), "choicePreferences", choicePreferences);
    int statLines = 0;
    for (List<String> lines : adaptation.statLines()) {
      statLines = Math.max(statLines, lines.size());
    }
    int groupRows = 0;
    for (PreferenceGroupView group : adaptation.preferenceGroups()) {
      groupRows += (group.entries().size() + GROUP_ROW - 1) / GROUP_ROW;
    }
    raise(measured, owners, adaptation.id(), "adaptationRows",
        statLines + (toggles + SWITCH_ROW - 1) / SWITCH_ROW + choicePreferences + groupRows);
  }

  private static void raise(Map<String, Integer> measured, Map<String, String> owners, String owner, String key, int value) {
    if (value > measured.get(key)) {
      measured.put(key, value);
      owners.put(key, owner);
    }
  }

  private static String plain(String text) {
    return text.replaceAll("§.", "");
  }
}
