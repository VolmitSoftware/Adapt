package art.arcane.adapt.content.skill;

import java.util.ArrayList;

import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.api.mutation.MutationPrice;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each upgraded adaptation is documented once in {@code docs/adapt/upgrades/<skill>.md}, under a heading that ends
 * with its id in backticks and with a config table listing exactly its documented fields. One file per skill keeps
 * parallel skill work from editing the same page; its price line must match the price tier its kit declares. Skipped until
 * the upgrades folder exists in the docs repo.
 */
class SkillUpgradeDocumentationTest {
  private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
  private static final Path CORE_ADAPTATION_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/adaptation").normalize();
  private static final Path UPGRADE_DOCS = PROJECT_ROOT.resolve("../../docs/adapt/upgrades").normalize();
  private static final Path SKILL_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/skill").normalize();
  private static final Pattern UPGRADE_CLASS = Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+SkillUpgrade<");
  private static final Pattern UPGRADE_ID = Pattern.compile("\\bId\\s+ID\\s*=\\s*Id\\.adapt\\(\"(([a-z0-9_]+)/upgrade/[a-z0-9_]+)\"\\);");
  private static final Pattern UPGRADE_HEADING = Pattern.compile("(?m)^###\\s+.+?\\s+\\(`(upgraded-[a-z0-9-]+)`\\)\\s*$");
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.api\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*=");
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");

  @Test
  void everyUpgradeIsDocumentedOnceInItsSkillPageWithItsConfigTable() throws IOException {
    Assumptions.assumeTrue(Files.isDirectory(UPGRADE_DOCS), "docs/adapt/upgrades does not exist yet");
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
      for (Path path : files.filter((Path file) -> file.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(path);
        Matcher upgradeClass = UPGRADE_CLASS.matcher(source);
        if (!upgradeClass.find()) {
          continue;
        }
        Matcher id = UPGRADE_ID.matcher(source);
        assertThat(id.find()).as("upgrade id in %s", path).isTrue();
        Id upgradeId = Id.adapt(id.group(1));
        String skill = id.group(2);
        MutationPrice price = price(upgradeClass.group(1));
        Path doc = UPGRADE_DOCS.resolve(skill + ".md");
        assertThat(doc).as("upgrade page for %s", skill).exists();
        String markdown = Files.readString(doc);
        List<Integer> starts = new ArrayList<>();
        Matcher headings = UPGRADE_HEADING.matcher(markdown);
        while (headings.find()) {
          if (documentedUpgrade(headings.group(1)).equals(upgradeId)) {
            starts.add(headings.start());
          }
        }
        assertThat(starts).as("heading for %s in %s", upgradeId, doc).hasSize(1);
        int start = starts.get(0);
        int end = markdown.indexOf("\n### ", start + 4);
        String section = markdown.substring(start, end < 0 ? markdown.length() : end);
        List<Integer> costs = price.costs();
        assertThat(section).as("price line of %s", upgradeId)
            .contains(costs.size() + " levels · " + costs.get(0) + ", " + costs.get(1) + " and " + costs.get(2) + " knowledge");
        assertThat(keys(TABLE_KEY, section)).as("config table of %s", upgradeId)
            .containsExactlyInAnyOrderElementsOf(keys(CONFIG_FIELD, source));
      }
    }
  }

  /** The price tier the upgrade's skill kit declares it at: {@code toolUpgrade} for the tool tier. */
  private static MutationPrice price(String upgradeClass) throws IOException {
    List<MutationPrice> found = new ArrayList<>();
    try (Stream<Path> skills = Files.list(SKILL_ROOT)) {
      for (Path skill : skills.filter((Path file) -> file.toString().endsWith(".java")).toList()) {
        String source = Files.readString(skill);
        if (source.contains(".toolUpgrade(" + upgradeClass + ".ID,")) {
          found.add(MutationPrice.TOOL);
        } else if (source.contains(".upgrade(" + upgradeClass + ".ID,")) {
          found.add(MutationPrice.UPGRADED);
        }
      }
    }
    assertThat(found).as("kits declaring %s", upgradeClass).hasSize(1);
    return found.getFirst();
  }

  /** The docs name an upgrade {@code upgraded-<skill>-<slug>}; that is the upgrade {@code adapt:<skill>/upgrade/<slug>}. */
  private static Id documentedUpgrade(String flat) {
    String rest = flat.substring("upgraded-".length());
    int split = rest.indexOf('-');
    return Id.adapt(rest.substring(0, split) + "/upgrade/" + rest.substring(split + 1).replace('-', '_'));
  }

  private static Set<String> keys(Pattern pattern, String text) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }
}
