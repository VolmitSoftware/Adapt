package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectPlacementChargeTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final int FLOOR = 44;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private ArchitectPlacement wand;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class));
    AdaptPlayer profile = adapt.join(player);
    wand = adapt.core().getAdaptServer().getSkillRegistry().getSkill("architect").getAdaptations().stream()
        .filter(ArchitectPlacement.class::isInstance).map(ArchitectPlacement.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("architect").setAdaptation(wand, 1);
    player.setSneaking(true);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        world.set(x, FLOOR, z, "stone");
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theFaceFillChargesEveryBlockItPlaces() {
    place(32);

    assertThat(filled()).isEqualTo(9);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(32 - 9);
  }

  @Test
  void aHandTooSmallForTheFaceFillsOnlyThePlacedBlock() {
    place(8);

    assertThat(filled()).isEqualTo(1);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(7);
  }

  /**
   * Places the center block as the game does: the held stack keeps its count through the place event, and the game
   * takes the placed block afterwards only when no handler changed the count.
   */
  private void place(int held) {
    BlockRef source = world.blockAt(0, FLOOR, 0);
    wand.runPlayerViewport(BlockFace.UP, source, STONE, player);
    BlockRef placed = world.set(0, FLOOR + 1, 0, "stone").blockAt(0, FLOOR + 1, 0);
    player.holding(new FakeItem(STONE, held));
    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
        Hand.MAIN_HAND));
    if (player.inventory().mainHand().amount() == held) {
      player.holding(new FakeItem(STONE, held - 1));
    }
    platform.scheduler().advance(3);
  }

  private int filled() {
    int filled = 0;
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        filled += world.blockAt(x, FLOOR + 1, z).type().equals(STONE) ? 1 : 0;
      }
    }
    return filled;
  }
}
