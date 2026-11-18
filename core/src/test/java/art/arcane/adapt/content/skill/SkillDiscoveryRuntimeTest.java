package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.world.BlockRef;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillDiscoveryRuntimeTest {
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
    player = platform.player("Explorer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void clickingABlockRecordsItsStateAndItemOnce() {
    world.set(2, 60, 0, "stone");
    BlockRef block = world.blockAt(2, 60, 0);

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, FakeItem.empty(),
        block, null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(profile.getData().getSeenBlocks().isNewDiscovery(block.state().asString())).isFalse();
    assertThat(profile.getData().getSeenItems().isNewDiscovery("STONE")).isFalse();
    assertThat(profile.getData().getStat("discovery.blocks")).isEqualTo(1D);
    assertThat(profile.getData().getStat("discovery.items")).isEqualTo(1D);
  }

  @Test
  void aKeylessEntityTypeIsRecordedAsUnknownAndAKeylessBiomeIsSkipped() {
    SkillDiscovery discovery = (SkillDiscovery) adapt.core().getAdaptServer().getSkillRegistry().getSkill("discovery");
    EntityRef keyless = mock(EntityRef.class);
    when(keyless.type()).thenReturn(null);

    discovery.seeEntity(player, keyless);

    assertThat(profile.getData().getSeenMobs().isNewDiscovery("UNKNOWN")).isFalse();
    assertThat(profile.getData().getStat("discovery.mobs")).isEqualTo(1D);
    assertThat(discovery.seeBiome(player, null)).isFalse();
    assertThat(profile.getData().getStat("discovery.biomes")).isZero();
  }

  @Test
  void eatingRecordsTheFoodUnderItsBukkitName() {
    adapt.post(new PlayerItemConsumeEvent(player, FakeItem.of("apple"), Hand.MAIN_HAND, FakeItem.empty()));

    assertThat(profile.getData().getSeenFoods().isNewDiscovery("APPLE")).isFalse();
    assertThat(profile.getData().getStat("discovery.foods")).isEqualTo(1D);
  }
}
