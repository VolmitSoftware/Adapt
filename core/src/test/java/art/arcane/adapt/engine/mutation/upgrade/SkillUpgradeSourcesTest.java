package art.arcane.adapt.engine.mutation.upgrade;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade sources sit under {@code content/adaptation/<package>/upgraded/}, name their id in their skill's namespace
 * and path, and each is declared once, with that id, by its own skill's mutation kit.
 */
class SkillUpgradeSourcesTest {
  private static final Path CONTENT = Path.of(System.getProperty("user.dir")).resolve("../content/src/main/java/art/arcane/adapt/content");
  private static final Pattern UPGRADE_CLASS = Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+SkillUpgrade<");
  private static final Pattern UPGRADE_ID = Pattern.compile("\\bId\\s+ID\\s*=\\s*Id\\.adapt\\(\"([a-z0-9_]+)/upgrade/([a-z0-9_]+)\"\\);");
  private static final Pattern SKILL_ID = Pattern.compile("\\bId\\s+ID\\s*=\\s*Id\\.adapt\\(\"([a-z]+)\"\\);");

  @Test
  void everyUpgradeSourceSitsWithItsSkillAndIsDeclaredByItsKit() throws IOException {
    List<Path> sources = javaFiles(CONTENT.resolve("adaptation"));
    List<String> skillSources = new ArrayList<>();
    for (Path skill : javaFiles(CONTENT.resolve("skill"))) {
      skillSources.add(Files.readString(skill));
    }
    int checked = 0;
    for (Path path : sources) {
      String source = Files.readString(path);
      Matcher upgradeClass = UPGRADE_CLASS.matcher(source);
      if (!upgradeClass.find()) {
        continue;
      }
      assertThat(path.getParent().getFileName().toString()).as("folder of %s", path).isEqualTo("upgraded");
      Matcher id = UPGRADE_ID.matcher(source);
      assertThat(id.find()).as("literal upgrade id in %s", path).isTrue();
      assertThat(source).as("%s passes its id", path).contains("super(ID)");

      String simpleName = upgradeClass.group(1);
      Pattern declared = Pattern.compile("\\.(?:upgrade|toolUpgrade)\\(" + simpleName + "\\.ID, " + simpleName + "::new,");
      List<String> declaredBy = new ArrayList<>();
      for (String skillSource : skillSources) {
        Matcher skillId = SKILL_ID.matcher(skillSource);
        if (declared.matcher(skillSource).find() && skillId.find()) {
          declaredBy.add(skillId.group(1));
        }
      }
      assertThat(declaredBy).as("skills declaring %s", simpleName).containsExactly(id.group(1));
      checked++;
    }
    assertThat(checked).isGreaterThanOrEqualTo(3);
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter((Path path) -> path.toString().endsWith(".java")).sorted().toList();
    }
  }
}
