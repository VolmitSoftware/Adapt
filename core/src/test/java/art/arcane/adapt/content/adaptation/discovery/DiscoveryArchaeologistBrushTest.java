package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBrushEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
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

class DiscoveryArchaeologistBrushTest {
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
    player = platform.player("Digger", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void finishedBrushResolvesThePendingBrushWithoutPolling() {
    DiscoveryArchaeologist archaeologist = learn();
    BlockRef block = world.set(2, 60, 0, "suspicious_sand").blockAt(2, 60, 0);
    FakeItem brush = FakeItem.of("brush");
    player.holding(brush);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, brush, block, null, null,
        UseResult.DEFAULT, UseResult.DEFAULT));
    assertThat(archaeologist.hasTickDemand()).isTrue();

    adapt.post(new BlockBrushEvent(player, block, FakeBlockState.of("sand")));

    assertThat(archaeologist.hasTickDemand()).isFalse();
  }

  private DiscoveryArchaeologist learn() {
    Skill<?> skill = SkillRegistry.skills.get("discovery");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof DiscoveryArchaeologist archaeologist) {
        profile.getData().getSkillLine("discovery").setAdaptation(adaptation, 1);
        return archaeologist;
      }
    }
    throw new AssertionError("No archaeologist");
  }
}
