package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRegistryProtectorSelectionTest {
  @Test
  void activatorUsesOnlyProtectorsEnabledByDefault() throws Exception {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/api/skill/SkillRegistry.java"));

    assertThat(source).contains(
        "getProtectorRegistry().getDefaultProtectors()",
        "protector.canInteract(player, targetLocation, null)"
    );
    assertThat(source).doesNotContain("getProtectorRegistry().getAllProtectors()");
  }
}
