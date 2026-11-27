package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.adaptation.axe.AxeIrisFeller;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lists every preference texture the client looks up for the catalog, relative to {@code gui/preference}, in
 * {@code build/reports/preference-art/expected.txt}: choice tiles (owner art, or {@code common/choice} for the shared
 * factory enums), toggle group members and headings, and the Iris feller that only registers beside Iris. Plain ON/OFF toggles are checkboxes and need none. Once the
 * {@code gui/preference} folder exists in the client resources, every listed file must be there and nothing else.
 */
class PreferenceArtCatalogTest {
  private static final Path REPORT = Path.of("build/reports/preference-art/expected.txt");
  private static final Path ART = Path.of("../adapters/client-common/src/main/resources/assets/adapt/textures/gui/preference");
  private static final String PREFIX = IconSprites.NAMESPACE + ':' + IconSprites.ROOT + "preference/";
  private static final Set<String> TOGGLE = Set.of(CommonPreferences.Toggle.ON.name(), CommonPreferences.Toggle.OFF.name());

  @TempDir
  Path dataFolder;

  @Test
  void everyPreferenceTextureIsListedAndPresentOnceArtExists() throws IOException {
    Set<String> expected = new TreeSet<>();
    Set<String> overrides = new HashSet<>();
    for (Enum<?>[] shared : List.<Enum<?>[]>of(CommonPreferences.Toggle.values(), CommonPreferences.DeathSavePriority.values(),
        CommonPreferences.Scale.values())) {
      for (Enum<?> value : shared) {
        expected.add(relative(IconSprites.commonChoice(value.name())));
      }
    }
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        addChoices(skill.getId(), List.of(CommonPreferences.SKILL_ENABLED), Set.of(), expected, overrides);
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          Set<String> grouped = new HashSet<>();
          for (PreferenceToggleGroup group : adaptation.getPlayerPreferenceGroups()) {
            expected.add(relative(IconSprites.preferenceGroup(adaptation.getId(), group.id())));
            for (PreferenceToggleGroup.Entry entry : group.entries()) {
              grouped.add(entry.preference().id());
              expected.add(relative(IconSprites.preferenceMember(adaptation.getId(), entry.preference().id())));
            }
          }
          addChoices(adaptation.getId(), adaptation.getPlayerPreferences(), grouped, expected, overrides);
        }
      }
      Adaptation<?> irisFeller = TestContent.adaptation(SkillAxes.ID, AxeIrisFeller::new);
      addChoices(irisFeller.getId(), irisFeller.getPlayerPreferences(), Set.of(), expected, overrides);
    }
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, expected, StandardCharsets.UTF_8);
    assertThat(expected).allMatch((String path) -> path.matches("[a-z0-9_./-]+\\.png"));
    if (!Files.isDirectory(ART)) {
      return;
    }
    Set<String> present = new TreeSet<>();
    try (Stream<Path> files = Files.walk(ART)) {
      files.filter((Path file) -> file.toString().endsWith(".png"))
          .forEach((Path file) -> present.add(ART.relativize(file).toString().replace('\\', '/')));
    }
    Set<String> missing = new TreeSet<>(expected);
    missing.removeAll(present);
    Set<String> unexpected = new TreeSet<>(present);
    unexpected.removeAll(expected);
    unexpected.removeAll(overrides);
    assertThat(missing).as("preference art missing (expected list in %s)", REPORT).isEmpty();
    assertThat(unexpected).as("preference art no lookup uses").isEmpty();
  }

  private static void addChoices(Id ownerId, List<PlayerPreference<?>> preferences, Set<String> grouped, Set<String> expected,
                                 Set<String> overrides) {
    for (PlayerPreference<?> preference : preferences) {
      if (grouped.contains(preference.id()) || values(preference).equals(TOGGLE)) {
        continue;
      }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        String own = relative(IconSprites.preferenceChoice(ownerId, preference.id(), choice.value().name()));
        if (choice.value().getDeclaringClass().getEnclosingClass() == CommonPreferences.class) {
          overrides.add(own);
        } else {
          expected.add(own);
        }
      }
    }
  }

  private static Set<String> values(PlayerPreference<?> preference) {
    Set<String> values = new HashSet<>();
    for (PlayerPreference.Choice<?> choice : preference.choices()) {
      values.add(choice.value().name());
    }
    return values;
  }

  private static String relative(String spriteId) {
    return spriteId.substring(PREFIX.length());
  }
}
