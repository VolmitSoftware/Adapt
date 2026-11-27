package art.arcane.adapt.content;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.advancement.ToastQuips;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Text lookups no longer throw for undeclared keys, so this is where built-in content proves it declares its own. */
class BuiltInTextCoverageTest {
  private static final String MISSING = "[missing:";

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
  void everySkillRendersItsTextFromDeclaredKeys() {
    List<String> missing = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      SkillHost host = SkillHost.of(skill);
      List<String> shown = new ArrayList<>(List.of(host.localizedName(), host.emoji(), host.description(), host.quip()));
      shown.addAll(host.unlockQuips());
      for (AdaptationGroup group : skill.getAdaptationGroups()) {
        shown.add(group.label().resolve());
      }
      for (String text : shown) {
        if (text.isBlank() || text.contains(MISSING)) {
          missing.add(skill.getId() + ": '" + text + "'");
        }
      }
      assertThat(host.unlockQuips()).describedAs(skill.getId().toString()).isNotEmpty();
    }
    assertThat(missing).isEmpty();
  }

  @Test
  void everyAdaptationRendersItsTextFromDeclaredKeys() {
    List<String> missing = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        List<String> shown = new ArrayList<>();
        shown.add(adaptation.getDisplayName());
        shown.add(adaptation.getShortName());
        shown.add(adaptation.getDescription());
        shown.add(ToastQuips.unlockLine(adaptation));
        for (String text : shown) {
          if (text != null && text.contains(MISSING)) {
            missing.add(adaptation.getId() + ": " + text);
          }
        }
      }
    }
    assertThat(missing).isEmpty();
  }

  @Test
  void everyPreferenceLabelRendersFromADeclaredKey() {
    List<String> missing = new ArrayList<>();
    int checked = 0;
    for (Skill<?> skill : skills()) {
      checked += requireLabels(skill.getId().toString(), PlayerPreferences.ownerKey(skill),
          CommonPreferences.SKILL_ENABLED, missing);
      List<Adaptation<?>> adaptations = new ArrayList<>(skill.getAdaptations());
      adaptations.addAll(SkillHost.of(skill).getUpgrades());
      for (Adaptation<?> adaptation : adaptations) {
        String owner = PlayerPreferences.ownerKey(adaptation);
        for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
          checked += requireLabels(adaptation.getId().toString(), owner, preference, missing);
        }
      }
    }
    assertThat(missing).isEmpty();
    assertThat(checked).isGreaterThan(100);
  }

  private static <E extends Enum<E>> int requireLabels(String content, String owner, PlayerPreference<E> preference,
                                                       List<String> missing) {
    List<String> shown = new ArrayList<>();
    shown.add(PlayerPreferences.shown(PlayerPreferences.label(owner, preference)));
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      shown.add(PlayerPreferences.shown(PlayerPreferences.label(owner, preference, choice.value())));
    }
    for (String text : shown) {
      if (text.isBlank() || text.contains(MISSING)) {
        missing.add(content + " " + preference.id() + ": '" + text + "'");
      }
    }
    return 1;
  }

  @Test
  void everyRegisteredConfigDeclaresItsFieldText() {
    List<String> undeclared = new ArrayList<>();
    int checked = 0;
    for (Skill<?> skill : skills()) {
      checked += requireFieldText("skills." + skill.getId(), skill.getConfig(), undeclared);
      List<Adaptation<?>> adaptations = new ArrayList<>(skill.getAdaptations());
      adaptations.addAll(SkillHost.of(skill).getUpgrades());
      for (Adaptation<?> adaptation : adaptations) {
        checked += requireFieldText("adaptations." + adaptation.getId(), adaptation.getConfig(), undeclared);
      }
    }
    assertThat(undeclared).isEmpty();
    assertThat(checked).isGreaterThan(300);
  }

  private static int requireFieldText(String path, Object config, List<String> undeclared) {
    if (config == null) {
      return 0;
    }
    for (Class<?> type = config.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (field.isSynthetic() || Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }
        if (!ConfigFieldMessages.declaresLabel(path + "." + field.getName())) {
          undeclared.add(path + "." + field.getName() + " (" + config.getClass().getName() + ")");
        }
      }
    }
    return 1;
  }

  private List<Skill<?>> skills() {
    List<Skill<?>> skills = adapt.core().getAdaptServer().getSkillRegistry().getSkills();
    assertThat(skills).hasSize(24);
    return skills;
  }
}
