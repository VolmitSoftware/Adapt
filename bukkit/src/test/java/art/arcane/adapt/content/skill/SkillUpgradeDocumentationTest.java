package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.mutation.upgrade.UpgradeSpec;
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
 * parallel skill work from editing the same page; its price line must match the catalog's price tier. Skipped until
 * the upgrades folder exists in the docs repo.
 */
class SkillUpgradeDocumentationTest {
  private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
  private static final Path CORE_ADAPTATION_ROOT =
      PROJECT_ROOT.resolve("../core/src/main/java/art/arcane/adapt/content/adaptation").normalize();
  private static final Path UPGRADE_DOCS = PROJECT_ROOT.resolve("../../docs/adapt/upgrades").normalize();
  private static final Pattern UPGRADE_CLASS = Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+\\w+\\s+extends\\s+SkillUpgrade<");
  private static final Pattern UPGRADE_ID = Pattern.compile("\\bsuper\\(\"([a-z0-9-]+)\"\\)");
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.util\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*=");
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");

  @Test
  void everyUpgradeIsDocumentedOnceInItsSkillPageWithItsConfigTable() throws IOException {
    Assumptions.assumeTrue(Files.isDirectory(UPGRADE_DOCS), "docs/adapt/upgrades does not exist yet");
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
      for (Path path : files.filter((Path file) -> file.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(path);
        if (!UPGRADE_CLASS.matcher(source).find()) {
          continue;
        }
        Matcher id = UPGRADE_ID.matcher(source);
        assertThat(id.find()).as("upgrade id in %s", path).isTrue();
        UpgradeSpec spec = SkillUpgrades.require(id.group(1));
        Path doc = UPGRADE_DOCS.resolve(spec.skillId() + ".md");
        assertThat(doc).as("upgrade page for %s", spec.skillId()).exists();
        String markdown = Files.readString(doc);
        Pattern heading = Pattern.compile("(?m)^###\\s+.+?\\s+\\(`" + Pattern.quote(spec.id()) + "`\\)\\s*$");
        Matcher headings = heading.matcher(markdown);
        assertThat(headings.find()).as("heading for %s in %s", spec.id(), doc).isTrue();
        int start = headings.start();
        assertThat(headings.find()).as("second heading for %s", spec.id()).isFalse();
        int end = markdown.indexOf("\n### ", start + 4);
        String section = markdown.substring(start, end < 0 ? markdown.length() : end);
        List<Integer> costs = spec.upgrade().price().costs();
        assertThat(section).as("price line of %s", spec.id())
            .contains(costs.size() + " levels · " + costs.get(0) + ", " + costs.get(1) + " and " + costs.get(2) + " knowledge");
        assertThat(keys(TABLE_KEY, section)).as("config table of %s", spec.id())
            .containsExactlyInAnyOrderElementsOf(keys(CONFIG_FIELD, source));
      }
    }
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
