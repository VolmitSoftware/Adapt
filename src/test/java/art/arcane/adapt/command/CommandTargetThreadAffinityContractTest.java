package art.arcane.adapt.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTargetThreadAffinityContractTest {
  @Test
  void everyTargetMutatingAdminCommandUsesTheEntityOwnerExecutor() throws IOException {
    assertThat(dispatchCount("CommandAdapt.java")).isEqualTo(7L);
    assertThat(dispatchCount("CommandClear.java")).isEqualTo(6L);
    assertThat(dispatchCount("CommandDebug.java")).isEqualTo(1L);
    assertThat(dispatchCount("CommandReset.java")).isEqualTo(1L);
  }

  @Test
  void scheduledCommandBodiesDoNotDependOnDirectorThreadLocalContext() throws IOException {
    String source = Files.readString(commandSource("CommandTargetExecutor.java"));

    assertThat(source)
        .contains("J.runEntity(target, task)")
        .doesNotContain("BukkitDirectorContext");
  }

  private long dispatchCount(String fileName) throws IOException {
    String source = Files.readString(commandSource(fileName));
    return source.lines().filter(line -> line.contains("CommandTargetExecutor.run(")).count();
  }

  private Path commandSource(String fileName) {
    return Path.of("src/main/java/art/arcane/adapt/command", fileName);
  }
}
