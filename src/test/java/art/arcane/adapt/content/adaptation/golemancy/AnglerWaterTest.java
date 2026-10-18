package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
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
    assertThat(AnglerWater.center(new NodeKey(WORLD, 0, 60, 0, BlockFace.UP))).containsExactly(0, 62, 0);
    assertThat(AnglerWater.center(new NodeKey(WORLD, 0, 66, 0, BlockFace.DOWN))).containsExactly(0, 63, 0);
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
    assertThat(AnglerWater.cell(Material.WATER, levelled(0))).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.WATER, levelled(3))).isEqualTo(AnglerWater.Cell.OTHER);
    assertThat(AnglerWater.cell(Material.SEAGRASS, null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.TALL_SEAGRASS, null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.KELP, null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.KELP_PLANT, null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.BUBBLE_COLUMN, null)).isEqualTo(AnglerWater.Cell.WATER);
    assertThat(AnglerWater.cell(Material.OAK_SLAB, waterlogged())).isEqualTo(AnglerWater.Cell.OTHER);
    assertThat(AnglerWater.cell(Material.AIR, null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Material.CAVE_AIR, null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Material.LILY_PAD, null)).isEqualTo(AnglerWater.Cell.AIR);
    assertThat(AnglerWater.cell(Material.STONE, null)).isEqualTo(AnglerWater.Cell.OTHER);
  }

  @Test
  void lilyPadsCountAsTheAirAboveTheWater() {
    pond(63, 64);
    cells.put("0,65,-3", AnglerWater.cell(Material.LILY_PAD, null));

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

  static Levelled levelled(int level) {
    Levelled data = mock(Levelled.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.getLevel()).thenReturn(level);
    return data;
  }

  private static BlockData waterlogged() {
    Waterlogged data = mock(Waterlogged.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.isWaterlogged()).thenReturn(true);
    return data;
  }
}
