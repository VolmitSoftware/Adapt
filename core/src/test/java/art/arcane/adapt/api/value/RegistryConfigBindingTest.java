package art.arcane.adapt.api.value;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryConfigBindingTest {
  @TempDir
  Path dataDirectory;

  @Test
  void blockNamesReadLikeMatchMaterialAndKeepOnlyBlocks() {
    TestPlatform platform = TestPlatform.builder(dataDirectory).build();
    platform.host().removeRegistryId(RegistryKind.BLOCK, Id.minecraft("diamond"));
    platform.host().removeRegistryId(RegistryKind.BLOCK, Id.parse("othermod:gizmo"));
    platform.host().removeRegistryId(RegistryKind.ITEM, Id.parse("othermod:gizmo"));
    RegistryConfigBinding binding = new RegistryConfigBinding(platform.host().registries());

    assertThat(binding.blockName("cave vines")).isEqualTo("CAVE_VINES");
    assertThat(binding.blockName("minecraft:Sweet_Berry_Bush")).isEqualTo("SWEET_BERRY_BUSH");
    assertThat(binding.blockName("othermod:peat_bog")).isEqualTo("othermod:peat_bog");
    assertThat(binding.blockName("DIAMOND")).isNull();
    assertThat(binding.blockName("othermod:gizmo")).isEqualTo("OTHERMODGIZMO");
    assertThat(binding.blockName("")).isNull();
  }
}
