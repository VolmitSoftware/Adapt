package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectChalkLineGuideSpotsTest {
  private static final int Y = 61;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private ArchitectChalkLine chalk;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Mason", 0.5D, Y, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class));
    adapt.join(player);
    chalk = (ArchitectChalkLine) adapt.core().getAdaptServer().getSkillRegistry().getAdaptation(ArchitectChalkLine.ID);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aWandWithoutAGuideIsSkippedForTheNextWandThatHasOne() {
    player.inventory().setOffHand(ChalkWandItem.create(Tool.STRAIGHTEDGE));
    player.inventory().setItem(0, ChalkWandItem.create(Tool.STRAIGHTEDGE));
    ItemRef guided = ChalkWandItem.create(Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(guided, new Plan(platform.world().uid(), List.of(new Point(2, Y, 3), new Point(6, Y, 3)), Plane.XZ));
    player.inventory().setItem(4, guided);

    List<BlockRef> open = chalk.openGuideSpots(player);

    assertThat(open).hasSize(5);
    assertThat(open).allMatch((BlockRef block) -> block.y() == Y && block.z() == 3);
  }
}
