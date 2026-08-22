package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectSmartShapeOrientationTest {
  private static final Set<BlockFace> ALL_SIX = EnumSet.of(
      BlockFace.NORTH,
      BlockFace.EAST,
      BlockFace.SOUTH,
      BlockFace.WEST,
      BlockFace.UP,
      BlockFace.DOWN
  );
  private static final Set<BlockFace> HORIZONTAL_ONLY = EnumSet.of(
      BlockFace.NORTH,
      BlockFace.EAST,
      BlockFace.SOUTH,
      BlockFace.WEST
  );

  @Test
  void sixWayBlocksCycleTheFourYawFacesAndNeverPointUpOrDown() {
    Directional barrel = directional(BlockFace.NORTH, ALL_SIX);
    List<BlockFace> visited = new ArrayList<>();

    for (int rotation = 0; rotation < 8; rotation++) {
      assertThat(ArchitectSmartShape.rotateData(barrel)).isEqualTo(4);
      visited.add(barrel.getFacing());
    }

    assertThat(visited).containsExactly(
        BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH,
        BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH
    );
    assertThat(visited).doesNotContain(BlockFace.UP, BlockFace.DOWN);
  }

  @Test
  void aBlockAlreadyFacingUpOrDownIsLeftAlone() {
    Directional facingUp = directional(BlockFace.UP, ALL_SIX);
    Directional facingDown = directional(BlockFace.DOWN, ALL_SIX);

    assertThat(ArchitectSmartShape.rotateData(facingUp)).isZero();
    assertThat(ArchitectSmartShape.rotateData(facingDown)).isZero();
    verify(facingUp, never()).setFacing(any(BlockFace.class));
    verify(facingDown, never()).setFacing(any(BlockFace.class));
  }

  @Test
  void verticalOnlyBlocksAreNeverFlipped() {
    Directional dripstone = directional(BlockFace.DOWN, EnumSet.of(BlockFace.UP, BlockFace.DOWN));

    assertThat(ArchitectSmartShape.rotateData(dripstone)).isZero();
    verify(dripstone, never()).setFacing(any(BlockFace.class));
  }

  @Test
  void horizontalBlocksKeepTheirFullFourStepCycle() {
    Directional stairs = directional(BlockFace.WEST, HORIZONTAL_ONLY);

    assertThat(ArchitectSmartShape.rotateData(stairs)).isEqualTo(4);
    assertThat(stairs.getFacing()).isEqualTo(BlockFace.NORTH);
  }

  @Test
  void yawFacesDropEveryNonCardinalOption() {
    assertThat(ArchitectSmartShape.yawFaces(ALL_SIX))
        .containsExactly(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    assertThat(ArchitectSmartShape.yawFaces(EnumSet.of(BlockFace.UP, BlockFace.DOWN))).isEmpty();
    assertThat(ArchitectSmartShape.yawFaces(Set.of())).isEmpty();
    assertThat(ArchitectSmartShape.yawFaces(null)).isEmpty();
    assertThat(ArchitectSmartShape.nextYawFace(BlockFace.NORTH, List.of(BlockFace.NORTH))).isNull();
    assertThat(ArchitectSmartShape.nextYawFace(BlockFace.UP, List.of(BlockFace.NORTH, BlockFace.SOUTH)))
        .isNull();
  }

  @Test
  void signsKeepTheVanillaSixteenStepYawCycle() {
    Rotatable sign = rotatable(BlockFace.NORTH);

    assertThat(ArchitectSmartShape.rotateData(sign)).isEqualTo(16);
    assertThat(sign.getRotation()).isEqualTo(BlockFace.NORTH_NORTH_EAST);
    assertThat(ArchitectSmartShape.nextRotation(BlockFace.NORTH_NORTH_WEST)).isEqualTo(BlockFace.NORTH);
    assertThat(ArchitectSmartShape.nextRotation(BlockFace.UP)).isEqualTo(BlockFace.NORTH);
  }

  @Test
  void pillarsCycleEverySupportedAxis() {
    Orientable log = orientable(Axis.X, EnumSet.of(Axis.X, Axis.Y, Axis.Z));

    assertThat(ArchitectSmartShape.rotateData(log)).isEqualTo(3);
    assertThat(log.getAxis()).isEqualTo(Axis.Y);
    assertThat(ArchitectSmartShape.rotateData(log)).isEqualTo(3);
    assertThat(log.getAxis()).isEqualTo(Axis.Z);
    assertThat(ArchitectSmartShape.rotateData(log)).isEqualTo(3);
    assertThat(log.getAxis()).isEqualTo(Axis.X);
  }

  @Test
  void pillarsSkipUnsupportedAxesInsteadOfRewritingTheirOwnState() {
    Orientable portal = orientable(Axis.X, EnumSet.of(Axis.X, Axis.Z));
    Orientable pinned = orientable(Axis.Y, EnumSet.of(Axis.Y));

    assertThat(ArchitectSmartShape.rotateData(portal)).isEqualTo(2);
    assertThat(portal.getAxis()).isEqualTo(Axis.Z);
    assertThat(ArchitectSmartShape.rotateData(pinned)).isZero();
    verify(pinned, never()).setAxis(any(Axis.class));
    assertThat(ArchitectSmartShape.nextAxis(Axis.X, null)).isNull();
  }

  private static Directional directional(BlockFace facing, Set<BlockFace> faces) {
    Directional data = mock(Directional.class);
    AtomicReference<BlockFace> state = new AtomicReference<>(facing);
    when(data.getFaces()).thenReturn(faces);
    when(data.getFacing()).thenAnswer(invocation -> state.get());
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setFacing(any(BlockFace.class));
    return data;
  }

  private static Rotatable rotatable(BlockFace rotation) {
    Rotatable data = mock(Rotatable.class);
    AtomicReference<BlockFace> state = new AtomicReference<>(rotation);
    when(data.getRotation()).thenAnswer(invocation -> state.get());
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setRotation(any(BlockFace.class));
    return data;
  }

  private static Orientable orientable(Axis axis, Set<Axis> axes) {
    Orientable data = mock(Orientable.class);
    AtomicReference<Axis> state = new AtomicReference<>(axis);
    when(data.getAxes()).thenReturn(axes);
    when(data.getAxis()).thenAnswer(invocation -> state.get());
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setAxis(any(Axis.class));
    return data;
  }
}
