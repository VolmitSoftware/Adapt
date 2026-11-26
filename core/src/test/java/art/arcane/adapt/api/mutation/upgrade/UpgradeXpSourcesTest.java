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
 * Upgrades pay skill XP only through the sourced helpers of {@link SkillUpgrade} ({@code xpOn}, {@code xpOnHarvest},
 * {@code xpFromAttacker}, {@code xpOnMobs}, {@code xpOnBlocks}), and mutation blocks pay none at all.
 */
class UpgradeXpSourcesTest {
  private static final Path ADAPTATIONS = Path.of(System.getProperty("user.dir"))
      .resolve("src/main/java/art/arcane/adapt/content/adaptation");
  private static final Pattern PLAIN_XP = Pattern.compile("(?<![\\w.])(xp|xpSilent)\\s*\\(");

  @Test
  void upgradesPayXpOnlyThroughTheSourcedHelpers() throws IOException {
    assertThat(offenders("upgraded")).isEmpty();
  }

  @Test
  void blocksPayNoSkillXp() throws IOException {
    assertThat(offenders("blocks")).isEmpty();
  }

  private static List<String> offenders(String folder) throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(ADAPTATIONS)) {
      for (Path path : files.filter((Path p) -> p.toString().endsWith(".java")
          && p.getParent().getFileName().toString().equals(folder)).sorted().toList()) {
        String[] lines = Files.readString(path).split("\n");
        for (int i = 0; i < lines.length; i++) {
          Matcher matcher = PLAIN_XP.matcher(lines[i]);
          if (matcher.find()) {
            offenders.add(path.getFileName() + ":" + (i + 1) + " " + lines[i].trim());
          }
        }
      }
    }
    return offenders;
  }
}
