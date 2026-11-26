package art.arcane.adapt.api.mutation.upgrade;

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
 * Upgrade sources sit where their spec says, under {@code content/adaptation/<package>/upgraded/}, and each is
 * registered once through its own skill's {@code registerUpgrades} call.
 */
class SkillUpgradeSourcesTest {
  private static final Path SOURCE_ROOT = Path.of(System.getProperty("user.dir")).resolve("src/main/java");
  private static final Path CONTENT = SOURCE_ROOT.resolve("art/arcane/adapt/content");
  private static final Pattern UPGRADE_CLASS = Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+SkillUpgrade<");
  private static final Pattern UPGRADE_ID = Pattern.compile("\\bsuper\\(\"([a-z0-9-]+)\"\\)");
  private static final Pattern SKILL_ID = Pattern.compile("\\bsuper\\(\"([a-z]+)\"\\s*,");
  private static final Pattern REGISTER_UPGRADES = Pattern.compile("registerUpgrades\\(([^;]*)\\);");

  @Test
  void everyUpgradeSourceMatchesItsSpecAndIsRegisteredByItsSkill() throws IOException {
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
      assertThat(id.find()).as("literal super(\"upgraded-...\") id in %s", path).isTrue();
      UpgradeSpec spec = SkillUpgrades.require(id.group(1));
      String className = SOURCE_ROOT.relativize(path).toString().replace('/', '.').replace(".java", "");
      assertThat(className).as("class of %s", spec.id()).isEqualTo(spec.className());

      String simpleName = upgradeClass.group(1);
      List<String> registeredBy = new ArrayList<>();
      for (String skillSource : skillSources) {
        Matcher register = REGISTER_UPGRADES.matcher(skillSource);
        Matcher skillId = SKILL_ID.matcher(skillSource);
        if (register.find() && skillId.find() && register.group(1).matches(".*\\bnew " + simpleName + "\\(\\).*")) {
          registeredBy.add(skillId.group(1));
        }
      }
      assertThat(registeredBy).as("skills registering %s", spec.id()).containsExactly(spec.skillId());
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
