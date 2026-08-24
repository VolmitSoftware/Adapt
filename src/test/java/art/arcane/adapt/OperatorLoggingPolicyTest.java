package art.arcane.adapt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorLoggingPolicyTest {
  private static final Path SOURCE_ROOT = Path.of("src/main/java");
  private static final Path ADAPT_SOURCE_ROOT = SOURCE_ROOT.resolve("art/arcane/adapt");

  @Test
  void firstPartyRuntimeOutputUsesTheAdaptLogger() throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(file);
        for (int index = 0; index < lines.size(); index++) {
          String line = lines.get(index);
          if (line.contains("System.out")
              || line.contains("System.err")
              || line.contains(".printStackTrace();")
              || line.contains("Throwable::printStackTrace")
              || line.contains("getConsoleSender().sendMessage")) {
            violations.add(SOURCE_ROOT.relativize(file) + ":" + (index + 1) + " " + line.trim());
          }
        }
      }
    }

    assertThat(violations).isEmpty();
  }

  @Test
  void routineProfileActivationIsVerboseOnly() throws IOException {
    String source = Files.readString(ADAPT_SOURCE_ROOT.resolve("api/world/AdaptServer.java"));

    assertThat(source).contains("Adapt.verbose(() -> profileReadyMessage(");
    assertThat(source).doesNotContain("Adapt.info(profileReadyMessage(");
  }

  @Test
  void everydayGameplayDiagnosticsStayOutOfNormalInfoLogs() throws IOException {
    String dirtyString = Files.readString(ADAPT_SOURCE_ROOT.resolve("util/common/misc/DirtyString.java"));
    String brewing = Files.readString(ADAPT_SOURCE_ROOT.resolve("api/potion/BrewingManager.java"));
    String notifier = Files.readString(ADAPT_SOURCE_ROOT.resolve("api/notification/Notifier.java"));

    assertThat(dirtyString).doesNotContain("Adapt.info(", "Not has in");
    assertThat(brewing).doesNotContain("Brewing click", "Brewing Stand Ingredient Clicked");
    assertThat(notifier).doesNotContain("Playing Notification");
  }
}
