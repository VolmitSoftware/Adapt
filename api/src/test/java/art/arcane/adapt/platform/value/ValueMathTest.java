package art.arcane.adapt.platform.value;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueMathTest {
  @Test
  void blockPosPacksAndUnpacks() {
    Random random = new Random(7L);
    for (int i = 0; i < 2000; i++) {
      BlockPos pos = new BlockPos(random.nextInt(60_000_000) - 30_000_000, random.nextInt(4096) - 2048,
          random.nextInt(60_000_000) - 30_000_000);
      assertEquals(pos, BlockPos.fromLong(pos.asLong()));
    }
    assertEquals(new BlockPos(-1, -64, -1), BlockPos.fromLong(new BlockPos(-1, -64, -1).asLong()));
  }

  @Test
  void chunkKeyMatchesBukkitLayout() {
    long key = ChunkPos.key(-3, 5);
    assertEquals((-3L & 0xFFFFFFFFL) | ((5L & 0xFFFFFFFFL) << 32), key);
    assertEquals(new ChunkPos(-3, 5), ChunkPos.fromKey(key));
    assertEquals(new ChunkPos(-1, 0), ChunkPos.containing(-0.5D, 15.9D));
  }

  @Test
  void blockPosRelativeAndChunk() {
    BlockPos pos = new BlockPos(17, 64, -1);
    assertEquals(new BlockPos(17, 61, -1), pos.relative(BlockFace.DOWN, 3));
    assertEquals(new ChunkPos(1, -1), pos.chunk());
    assertEquals(BlockFace.UP, BlockFace.DOWN.opposite());
  }

  @Test
  void vecNormalizeHandlesZero() {
    assertEquals(Vec3.ZERO, Vec3.ZERO.normalize());
    Vec3 unit = new Vec3(3, 0, 4).normalize();
    assertEquals(1.0D, unit.length(), 1.0E-9D);
    assertEquals(new BlockPos(-1, 2, 0), new Vec3(-0.1D, 2.9D, 0.0D).toBlockPos());
  }

  @Test
  void rotationDirectionFollowsVanilla() {
    Vec3 south = Vec3.fromRotation(0.0F, 0.0F);
    assertEquals(0.0D, south.x(), 1.0E-9D);
    assertEquals(1.0D, south.z(), 1.0E-9D);
    Vec3 down = Vec3.fromRotation(0.0F, 90.0F);
    assertEquals(-1.0D, down.y(), 1.0E-9D);
  }

  @Test
  void boxNormalisesAndOverlaps() {
    Box box = new Box(1, 1, 1, 0, 0, 0);
    assertEquals(0.0D, box.minX());
    assertTrue(box.overlaps(new Box(0.5, 0.5, 0.5, 2, 2, 2)));
    assertFalse(box.overlaps(new Box(1, 0, 0, 2, 1, 1)));
    assertTrue(box.contains(new Vec3(0.5, 0.5, 0.5)));
  }

  @Test
  void rgbPacks() {
    Rgb rgb = Rgb.of(0xB00000);
    assertEquals(176, rgb.red());
    assertEquals(0xB00000, rgb.packed());
  }
}
