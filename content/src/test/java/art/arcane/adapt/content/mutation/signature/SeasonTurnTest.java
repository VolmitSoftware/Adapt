package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;

/** Chronos with Herbalism: once a day the clock gesture ages every crop around you one stage, as forced growth. */
class SeasonTurnTest {
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id WHEAT = Id.minecraft("wheat");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Farmer", 0.5D, 61D, 0.5D);
    player.holding(new FakeItem(CLOCK, 1));
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillHerbalism.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    platform.world().fullTime(1_000L);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void everyCropInTheFieldGrowsOneStageAndCountsAsForced() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 1);
    BlockRef near = crop(3, 61, 0, 2);
    BlockRef edge = crop(0, 61, 8, 6);
    BlockRef ripe = crop(-2, 61, 1, 7);
    BlockRef outside = crop(9, 61, 0, 2);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      assertThat(swap().isCancelled()).isTrue();
      platform.scheduler().advance(2);
      provenance.verify(() -> XpProvenance.recordBonemeal(any(BlockRef.class)), atLeastOnce());
    }

    assertThat(near.state().age()).isEqualTo(3);
    assertThat(edge.state().age()).isEqualTo(7);
    assertThat(ripe.state().age()).isEqualTo(7);
    assertThat(outside.state().age()).isEqualTo(2);
  }

  @Test
  void herbalismLeadingWorksTheSameWay() {
    kit.mutate("herbalism", "chronos");
    kit.learn("herbalism", "chronos", Recipe.CONVERGENCE, 3);
    BlockRef far = crop(15, 61, 0, 0);

    swap();
    platform.scheduler().advance(2);

    assertThat(far.state().age()).isEqualTo(1);
  }

  @Test
  void onlyOncePerInGameDay() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 3);
    BlockRef wheat = crop(2, 61, 0, 0);

    swap();
    platform.scheduler().advance(2);
    platform.world().fullTime(23_000L);
    assertThat(swap().isCancelled()).isFalse();
    platform.scheduler().advance(2);
    assertThat(wheat.state().age()).isEqualTo(1);

    platform.world().fullTime(25_000L);
    assertThat(swap().isCancelled()).isTrue();
    platform.scheduler().advance(2);
    assertThat(wheat.state().age()).isEqualTo(2);
  }

  @Test
  void nothingGrowsWithoutTheMutationOrAField() {
    BlockRef wheat = crop(2, 61, 0, 0);
    assertThat(swap().isCancelled()).isFalse();
    platform.scheduler().advance(2);
    assertThat(wheat.state().age()).isZero();

    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 1);
    platform.world().set(new BlockPos(2, 61, 0), new FakeBlockState(Id.minecraft("stone"), Map.of()));
    assertThat(swap().isCancelled()).isFalse();
  }

  @Test
  void aMissedSearchWaitsBeforeTheNextOne() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 1);
    swap();

    BlockRef wheat = crop(2, 61, 0, 0);
    assertThat(swap().isCancelled()).isFalse();
    platform.scheduler().advance(2);

    assertThat(wheat.state().age()).isZero();
  }

  @Test
  void protectedCropsDoNotGrow() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 1);
    BlockRef wheat = crop(2, 61, 0, 0);
    platform.protection().deny(true);

    swap();
    platform.scheduler().advance(2);

    assertThat(wheat.state().age()).isZero();
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private PlayerSwapHandItemsEvent swap() {
    player.setSneaking(true);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }
}
