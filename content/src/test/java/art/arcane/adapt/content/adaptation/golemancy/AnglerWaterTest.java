package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class AnglerWaterTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000027a1");
  private static final NodeKey NORTH = new NodeKey(WORLD, 0, 64, 0, BlockFace.NORTH);

  private final Map<String, AnglerWater.Cell> cells = new HashMap<>();
  private final AnglerWater.View view = (x, y, z) -> cells.getOrDefault(x + "," + y + "," + z, AnglerWater.Cell.AIR);

  @Test
  void theOpenWaterVolumeSitsInFrontOfTheNodeFace() {
    assertThat(AnglerWater.center(NORTH)).containsExactly(0, 64, -3);
    assertThat(AnglerWater.center(new NodeKey(WORLD, 0, 64, 0, BlockFace.EAST))).containsExactly(3, 64, 0);
  }

  @Test
  void nodesOnTopOrBottomFacesNeverHaveOpenWater() {
    cells.clear();
    for (int y = 61; y <= 70; y++) {
      for (int x = -6; x <= 6; x++) {
        for (int z = -6; z <= 6; z++) {
          cells.put(x + "," + y + "," + z, AnglerWater.Cell.WATER);
        }
      }
    }

    assertThat(AnglerWater.survey(view, new NodeKey(WORLD, 0, 64, 0, BlockFace.UP)).open()).isFalse();
    assertThat(AnglerWater.survey(view, new NodeKey(WORLD, 0, 64, 0, BlockFace.DOWN)).open()).isFalse();
    assertThat(AnglerWater.survey(view, new NodeKey(WORLD, 0, 64, 0, BlockFace.NORTH)).open()).isTrue();
  }

  @Test
  void seatsLineTheShoreBesideTheFaceAndBobbersSitAcrossThePool() {
    NodeKey east = new NodeKey(WORLD, 0, 64, 0, BlockFace.EAST);
    AnglerWater.Survey survey = new AnglerWater.Survey(true, 0, 64, -3, 64, false);

    assertThat(AnglerWater.seat(NORTH, 0)).containsExactly(0, 64, -1);
    assertThat(AnglerWater.seat(NORTH, 1)).containsExactly(1, 64, -1);
    assertThat(AnglerWater.seat(NORTH, 2)).containsExactly(-1, 64, -1);
    assertThat(AnglerWater.seat(NORTH, 4)).containsExactly(-2, 64, -1);
    assertThat(AnglerWater.seat(east, 1)).containsExactly(1, 64, 1);
    assertThat(AnglerWater.bobber(NORTH, survey, 1)).containsExactly(1, 64, -3);
    assertThat(AnglerWater.bobber(NORTH, survey, 2)).containsExactly(-1, 64, -3);
  }

  @Test
  void aPoolTwoDeepWithAirAboveIsOpenWater() {
    pond(63, 64);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isTrue();
    assertThat(AnglerWater.surface(view, 0, 64, -3)).isEqualTo(64);
  }

  @Test
  void anyMixedLayerClosesTheWater() {
    pond(63, 64);
    cells.put("2,65,-5", AnglerWater.Cell.OTHER);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();

    cells.put("2,65,-5", AnglerWater.Cell.AIR);
    cells.put("-2,63,-1", AnglerWater.Cell.OTHER);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();
  }

  @Test
  void aLayerMixingWaterAndAirClosesTheWater() {
    pond(63, 64);
    cells.put("-2,64,-5", AnglerWater.Cell.AIR);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();

    pond(63, 64);
    cells.put("-2,65,-5", AnglerWater.Cell.WATER);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();
  }

  @Test
  void theBottomLayerMustBeWaterAndWaterNeverSitsAboveAir() {
    pond(64, 64);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();

    pond(63, 64);
    layer(66, AnglerWater.Cell.WATER);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isFalse();
  }

  @Test
  void deepWaterIsOpenAndItsSurfaceIsFoundAboveTheVolume() {
    pond(63, 68);

    assertThat(AnglerWater.open(view, 0, 64, -3)).isTrue();
    assertThat(AnglerWater.surface(view, 0, 64, -3)).isEqualTo(68);
  }

  @Test
  void sourceWaterAndWaterPlantsAreWaterWhileFlowingAndWaterloggedBlocksAreNot() {
    assertThat(AnglerWater.cell(Id.minecraft("water"), levelled(0))).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("water"), levelled(3))).isEqualTo(AnglerWater.Cell.OTHER);
    assertThat(AnglerWater.cell(Id.minecraft("seagrass"), null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("tall_seagrass"), null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("kelp"), null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("kelp_plant"), null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("bubble_column"), null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Id.minecraft("oak_slab"), waterlogged())).isEqualTo(AnglerWater.Cell.OTHER);
    assertThat(AnglerWater.cell(Id.minecraft("air"), null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Id.minecraft("cave_air"), null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Id.minecraft("lily_pad"), null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Id.minecraft("stone"), null)).isEqualTo(AnglerWater.Cell.OTHER);
  }

  @Test
  void lilyPadsCountAsTheAirAboveTheWater() {
    pond(63, 64);
    cells.put("0,65,-3", AnglerWater.cell(Id.minecraft("lily_pad"), null));

    assertThat(AnglerWater.open(view, 0, 64, -3)).isTrue();
  }

  @Test
  void surveysReportOpenWaterWithItsSurfaceForTheNode() {
    pond(63, 64);

    AnglerWater.Survey open = AnglerWater.survey(view, NORTH);
    cells.put("0,63,-3", AnglerWater.Cell.OTHER);
    AnglerWater.Survey closed = AnglerWater.survey(view, NORTH);

    assertThat(open).isEqualTo(new AnglerWater.Survey(true, 0, 64, -3, 64, false));
    assertThat(open.raining(true).raining()).isTrue();
    assertThat(closed.open()).isFalse();
  }

  @Test
  void itRainsOnTheWaterOnlyInAStormUnderOpenSkyInARainyBiome() {
    assertThat(AnglerWater.raining(true, 64, 64, 0.8D)).isTrue();
    assertThat(AnglerWater.raining(false, 64, 64, 0.8D)).isFalse();
    assertThat(AnglerWater.raining(true, 70, 64, 0.8D)).isFalse();
    assertThat(AnglerWater.raining(true, 64, 64, 0.1D)).isFalse();
    assertThat(AnglerWater.raining(true, 64, 64, 2.0D)).isFalse();
  }

  private void pond(int bottom, int top) {
    cells.clear();
    for (int y = bottom; y <= top; y++) {
      layer(y, AnglerWater.Cell.WATER);
    }
  }

  private void layer(int y, AnglerWater.Cell cell) {
    for (int x = -2; x <= 2; x++) {
      for (int z = -5; z <= -1; z++) {
        cells.put(x + "," + y + "," + z, cell);
      }
    }
  }

  static BlockStateRef levelled(int level) {
    BlockStateRef data = mock(BlockStateRef.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.property("level")).thenReturn(String.valueOf(level));
    return data;
  }

  private static BlockStateRef waterlogged() {
    BlockStateRef data = mock(BlockStateRef.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.property("waterlogged")).thenReturn("true");
    return data;
  }
}
