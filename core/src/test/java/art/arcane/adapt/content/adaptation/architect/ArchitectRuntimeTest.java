package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.BlockFace;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectRuntimeTest {
  @TempDir
  Path data;

  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Builder", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.of("stone"));
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = SkillRegistry.skills.get("architect");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("architect").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  @Test
  void skillRegistersEveryAdaptation() {
    assertThat(SkillRegistry.skills.get("architect").getAdaptations()).hasSize(12);
  }

  @Test
  void skillCountsPlacedAndBrokenBlocks() {
    BlockRef placed = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);

    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), placed.relative(BlockFace.DOWN),
        FakeItem.of("stone"), Hand.MAIN_HAND));
    adapt.post(new BlockBreakEvent(player, placed, true, 0));

    assertThat(profile.getData().getStat("blocks.placed")).isEqualTo(1D);
    assertThat(profile.getData().getStat("blocks.broken")).isEqualTo(1D);
  }

  @Test
  void pistonsCannotMoveElevatorBlocks() {
    learn(ArchitectElevator.class, 1);
    BlockRef elevator = world.set(0, 60, 0, "note_block").blockAt(0, 60, 0);
    FakeItem item = FakeItem.of("note_block");
    item.data().set(DataKey.adapt("elevator", DataCodecs.BYTE), (byte) 0);

    adapt.post(new BlockPlaceEvent(player, elevator, FakeBlockState.of("air"), elevator.relative(BlockFace.DOWN),
        item, Hand.MAIN_HAND));
    BlockPistonEvent piston = adapt.post(new BlockPistonEvent(elevator.relative(BlockFace.WEST), BlockFace.EAST, true,
        List.of(elevator)));

    assertThat(elevator.hasData()).isTrue();
    assertThat(piston.isCancelled()).isTrue();
  }

  @Test
  void elevatorPistonCancelLandsAtMonitorAfterHighestHandlers() {
    learn(ArchitectElevator.class, 1);
    BlockRef elevator = world.set(0, 60, 0, "note_block").blockAt(0, 60, 0);
    FakeItem item = FakeItem.of("note_block");
    item.data().set(DataKey.adapt("elevator", DataCodecs.BYTE), (byte) 0);
    adapt.post(new BlockPlaceEvent(player, elevator, FakeBlockState.of("air"), elevator.relative(BlockFace.DOWN),
        item, Hand.MAIN_HAND));
    List<Boolean> highest = new ArrayList<>();
    adapt.bus().register(this, BlockPistonEvent.class, EventPriority.HIGHEST, false, event -> highest.add(event.isCancelled()));

    BlockPistonEvent piston = adapt.post(new BlockPistonEvent(elevator.relative(BlockFace.WEST), BlockFace.EAST, true,
        List.of(elevator)));

    assertThat(highest).containsExactly(false);
    assertThat(piston.isCancelled()).isTrue();
  }
}
