package art.arcane.adapt.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPermissionContractTest {
  @Test
  void destructiveConfigCommandsUseTheDeclaredConfiguratorPermission() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/command/CommandDefault.java"));

    assertThat(source).contains("BukkitDirectorContext.hasPermission(\"adapt.configurator\")");
    assertThat(source).doesNotContain("sender().isOp()");
  }

  @Test
  void playerDataResetUsesTheDeclaredClearPermission() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/command/CommandReset.java"));

    assertThat(source)
        .contains("BukkitDirectorContext.hasPermission(\"adapt.clear\")")
        .contains("requires_adapt_clear_run_twice_to_confirm")
        .doesNotContain("requires_op_run_twice_to_confirm")
        .doesNotContain("sender().isOp()");
  }
}
