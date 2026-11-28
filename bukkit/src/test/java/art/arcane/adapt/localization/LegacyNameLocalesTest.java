package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Text names adaptations and skills by their display names, never by the flat ids they had before ids were
 * namespaced ({@code agility-armor-up}, {@code agility}). The flat names come from the started built-in registry, so
 * new content is covered as it is added.
 */
class LegacyNameLocalesTest extends AdaptTestBase {
  private static final MessageCatalog CATALOG = BuiltInText.installWithConfigText();
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");
  private static final String SKILL_FIELDS = "adapt.config.fields.skills.";
  private static final Pattern ADAPTATION_NAME = adaptationNames();

  @AfterAll
  static void uninstallBuiltInText() {
    BuiltInText.uninstall();
  }

  @Test
  void registryYieldsFlatNamesToGuard() {
    assertThat(BuiltInText.legacyAdaptationNames()).contains("agility-armor-up").hasSizeGreaterThan(300);
    assertThat(BuiltInText.legacySkillNames()).contains("agility", "seaborne");
  }

  @Test
  void englishTextNamesNoAdaptationOrSkillByItsFlatId() {
    List<String> failures = new ArrayList<>();
    for (MessageKey key : CATALOG.keys()) {
      for (String text : texts(key.englishValue())) {
        if (ADAPTATION_NAME.matcher(text).find()) {
          failures.add(key.id() + ": " + text);
        }
        String skill = skillOf(key.id());
        if (skill != null && text.contains("the " + skill + " skill")) {
          failures.add(key.id() + ": " + text);
        }
      }
    }
    assertThat(failures).isEmpty();
  }

  @Test
  void translationsNameNoAdaptationOrSkillByItsFlatId() throws IOException {
    List<String> failures = new ArrayList<>();
    for (String locale : VolmitLocales.nonEnglish()) {
      Path file = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
      for (MessageKey key : CATALOG.keys()) {
        MessageValue translated = overlay.value(key.id());
        if (translated == null) {
          continue;
        }
        String skill = skillOf(key.id());
        int englishSkillMentions = skill == null ? 0 : count(skillName(skill), String.join("\n", texts(key.englishValue())));
        String joined = String.join("\n", texts(translated));
        if (ADAPTATION_NAME.matcher(joined).find()
            || skill != null && count(skillName(skill), joined) > englishSkillMentions) {
          failures.add(locale + ":" + key.id() + ": " + joined);
        }
      }
    }
    assertThat(failures).isEmpty();
  }

  /** The skill a skill config field key belongs to, or null for any other key. */
  private static String skillOf(String id) {
    if (!id.startsWith(SKILL_FIELDS)) {
      return null;
    }
    String rest = id.substring(SKILL_FIELDS.length());
    int dot = rest.indexOf('.');
    String skill = dot < 0 ? rest : rest.substring(0, dot);
    return BuiltInText.legacySkillNames().contains(skill) ? skill : null;
  }

  private static Pattern adaptationNames() {
    List<String> names = new ArrayList<>(BuiltInText.legacyAdaptationNames());
    names.removeIf((String name) -> !name.contains("-"));
    names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
    List<String> quoted = names.stream().map(Pattern::quote).toList();
    return Pattern.compile("(?<![a-z0-9-])(?:" + String.join("|", quoted) + ")(?![a-z0-9])");
  }

  /** A skill's flat id as a word of its own, not part of a key such as {@code stealth.kills} or {@code adapt:stealth}. */
  private static Pattern skillName(String skill) {
    return Pattern.compile("(?<![\\w.:/-])" + Pattern.quote(skill) + "(?![\\w:/-]|\\.\\w)");
  }

  private static int count(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static List<String> texts(MessageValue value) {
    if (value instanceof TextValue text) {
      return List.of(text.template());
    }
    if (value instanceof LinesValue lines) {
      return lines.lines();
    }
    if (value instanceof PluralValue plural) {
      return List.copyOf(plural.forms().values());
    }
    return List.of();
  }
}
