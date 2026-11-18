package art.arcane.adapt.api.value;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialNamesTest {
  @TempDir
  Path dataDirectory;

  @Test
  void matchReadsNamesLikeBukkitMatchMaterial() {
    assertThat(MaterialNames.match("GRASS_BLOCK")).isEqualTo(Id.minecraft("grass_block"));
    assertThat(MaterialNames.match("grass_block")).isEqualTo(Id.minecraft("grass_block"));
    assertThat(MaterialNames.match("minecraft:Sweet_Berry_Bush")).isEqualTo(Id.minecraft("sweet_berry_bush"));
    assertThat(MaterialNames.match("Stone Pressure Plate")).isEqualTo(Id.minecraft("stone_pressure_plate"));
    assertThat(MaterialNames.match("powder-snow")).isEqualTo(Id.minecraft("powdersnow"));
    assertThat(MaterialNames.match("MINECRAFT:STONE")).isEqualTo(Id.minecraft("minecraftstone"));
    assertThat(MaterialNames.match("")).isNull();
    assertThat(MaterialNames.match(null)).isNull();
  }

  @Test
  void matchTakesARegisteredModdedIdAsWrittenAndOtherwiseFiltersItLikeBukkit() {
    TestPlatform platform = TestPlatform.builder(dataDirectory).build();
    platform.host().removeRegistryId(RegistryKind.ITEM, Id.parse("sto:ne"));
    platform.host().removeRegistryId(RegistryKind.BLOCK, Id.parse("sto:ne"));
    try (TestAdapt ignored = TestAdapt.start(platform, List.of())) {
      assertThat(MaterialNames.match("othermod:peat_bog")).isEqualTo(Id.parse("othermod:peat_bog"));
      assertThat(MaterialNames.match("sto:ne")).isEqualTo(Id.minecraft("stone"));
    }
    assertThat(MaterialNames.vanilla("othermod:peat_bog")).isEqualTo(Id.minecraft("othermodpeat_bog"));
    assertThat(MaterialNames.modded("othermod:peat_bog")).isEqualTo(Id.parse("othermod:peat_bog"));
    assertThat(MaterialNames.modded("minecraft:stone")).isNull();
    assertThat(MaterialNames.modded("stone")).isNull();
  }

  @Test
  void matchUpperCasesBeforeDroppingPunctuationAsBukkitDoes() {
    assertThat(MaterialNames.match("minecraft:STONE")).isEqualTo(Id.minecraft("stone"));
    assertThat(MaterialNames.match("  Stone\tBricks ")).isEqualTo(Id.minecraft("_stone_bricks_"));
    assertThat(MaterialNames.match("\u00dfand")).isEqualTo(Id.minecraft("ssand"));
  }

  @Test
  void exactAcceptsOnlyTheUpperCaseConstantName() {
    assertThat(MaterialNames.exact("ROTTEN_FLESH")).isEqualTo(Id.minecraft("rotten_flesh"));
    assertThat(MaterialNames.exact("rotten_flesh")).isNull();
    assertThat(MaterialNames.exact("minecraft:ROTTEN_FLESH")).isNull();
    assertThat(MaterialNames.exact("")).isNull();
  }
}
