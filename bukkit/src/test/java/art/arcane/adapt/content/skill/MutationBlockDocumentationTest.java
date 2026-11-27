package art.arcane.adapt.content.skill;

import java.util.List;

import java.util.ArrayList;

import art.arcane.adapt.platform.value.Id;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each mutation block is documented once in {@code docs/adapt/mutation-blocks/<skill>.md}, under a heading that ends
 * with its id in backticks and with a config table listing exactly its documented fields. One file per skill keeps
 * parallel skill work from editing the same page. Skipped until the folder exists in the docs repo.
 */
class MutationBlockDocumentationTest {
  private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
  private static final Path CORE_ADAPTATION_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/adaptation").normalize();
  private static final Path BLOCK_DOCS = PROJECT_ROOT.resolve("../../docs/adapt/mutation-blocks").normalize();
  private static final Path SKILL_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/skill").normalize();
  private static final Pattern BLOCK_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+\\w+\\s+extends\\s+(Trigger|Strike|Boon|Fuel|State)Block<");
  private static final Pattern BLOCK_SUPER = Pattern.compile("\\bsuper\\((Skill\\w+)\\.ID(?:, StrikeForm\\.(FIGHT|WORK))?\\);");
  private static final Pattern SKILL_ID = Pattern.compile("\\bId\\s+ID\\s*=\\s*Id\\.adapt\\(\"([a-z]+)\"\\);");
  private static final Pattern BLOCK_HEADING = Pattern.compile("(?m)^###\\s+.+?\\s+\\(`(block-[a-z0-9-]+)`\\)\\s*$");
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.api\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*=");
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");

  @Test
  void everyBlockIsDocumentedOnceInItsSkillPageWithItsConfigTable() throws IOException {
    Assumptions.assumeTrue(Files.isDirectory(BLOCK_DOCS), "docs/adapt/mutation-blocks does not exist yet");
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
      for (Path path : files.filter((Path file) -> file.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(path);
        Matcher blockClass = BLOCK_CLASS.matcher(source);
        if (!blockClass.find()) {
          continue;
        }
        Matcher constructor = BLOCK_SUPER.matcher(source);
        assertThat(constructor.find()).as("block skill in %s", path).isTrue();
        String skill = skillPath(constructor.group(1));
        String slot = switch (blockClass.group(1)) {
          case "Strike" -> "FIGHT".equals(constructor.group(2)) ? "strike_fight" : "strike_work";
          default -> blockClass.group(1).toLowerCase(Locale.ROOT);
        };
        Id blockId = Id.adapt(skill + "/block/" + slot);
        Path doc = BLOCK_DOCS.resolve(skill + ".md");
        assertThat(doc).as("block page for %s", skill).exists();
        String markdown = Files.readString(doc);
        List<Integer> starts = new ArrayList<>();
        Matcher headings = BLOCK_HEADING.matcher(markdown);
        while (headings.find()) {
          if (documentedBlock(headings.group(1)).equals(blockId)) {
            starts.add(headings.start());
          }
        }
        assertThat(starts).as("heading for %s in %s", blockId, doc).hasSize(1);
        int start = starts.get(0);
        int end = markdown.indexOf("\n### ", start + 4);
        String section = markdown.substring(start, end < 0 ? markdown.length() : end);
        assertThat(keys(TABLE_KEY, section)).as("config table of %s", blockId)
            .containsExactlyInAnyOrderElementsOf(keys(CONFIG_FIELD, source));
      }
    }
  }

  /** The id path of the built-in skill class {@code skillClass}, read from its {@code ID} constant. */
  private static String skillPath(String skillClass) throws IOException {
    Matcher id = SKILL_ID.matcher(Files.readString(SKILL_ROOT.resolve(skillClass + ".java")));
    assertThat(id.find()).as("id of %s", skillClass).isTrue();
    return id.group(1);
  }

  /** The docs name a block {@code block-<skill>-<slot>}; that is the block {@code adapt:<skill>/block/<slot>}. */
  private static Id documentedBlock(String flat) {
    String rest = flat.substring("block-".length());
    int split = rest.indexOf('-');
    return Id.adapt(rest.substring(0, split) + "/block/" + rest.substring(split + 1).replace('-', '_'));
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
