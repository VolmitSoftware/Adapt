package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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
      PROJECT_ROOT.resolve("../core/src/main/java/art/arcane/adapt/content/adaptation").normalize();
  private static final Path BLOCK_DOCS = PROJECT_ROOT.resolve("../../docs/adapt/mutation-blocks").normalize();
  private static final Pattern BLOCK_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+\\w+\\s+extends\\s+(?:Trigger|Strike|Boon|Fuel|State)Block<");
  private static final Pattern BLOCK_ID = Pattern.compile("\\bsuper\\(\"(block-[a-z0-9-]+)\"\\)");
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.util\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*=");
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");

  @Test
  void everyBlockIsDocumentedOnceInItsSkillPageWithItsConfigTable() throws IOException {
    Assumptions.assumeTrue(Files.isDirectory(BLOCK_DOCS), "docs/adapt/mutation-blocks does not exist yet");
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
      for (Path path : files.filter((Path file) -> file.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(path);
        if (!BLOCK_CLASS.matcher(source).find()) {
          continue;
        }
        Matcher id = BLOCK_ID.matcher(source);
        assertThat(id.find()).as("block id in %s", path).isTrue();
        BlockRef ref = MutationBlocks.parse(id.group(1));
        assertThat(ref).as("block id %s", id.group(1)).isNotNull();
        Path doc = BLOCK_DOCS.resolve(ref.skillId() + ".md");
        assertThat(doc).as("block page for %s", ref.skillId()).exists();
        String markdown = Files.readString(doc);
        Pattern heading = Pattern.compile("(?m)^###\\s+.+?\\s+\\(`" + Pattern.quote(id.group(1)) + "`\\)\\s*$");
        Matcher headings = heading.matcher(markdown);
        assertThat(headings.find()).as("heading for %s in %s", id.group(1), doc).isTrue();
        int start = headings.start();
        assertThat(headings.find()).as("second heading for %s", id.group(1)).isFalse();
        int end = markdown.indexOf("\n### ", start + 4);
        String section = markdown.substring(start, end < 0 ? markdown.length() : end);
        assertThat(keys(TABLE_KEY, section)).as("config table of %s", id.group(1))
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
