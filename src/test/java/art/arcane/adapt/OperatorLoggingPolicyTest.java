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
}
