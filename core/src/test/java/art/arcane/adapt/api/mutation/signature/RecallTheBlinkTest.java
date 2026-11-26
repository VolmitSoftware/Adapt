package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Chronos with Rift: shortly after a rift teleport the clock gesture goes back to where it started. */
class RecallTheBlinkTest {
  private static final Id CLOCK = Id.minecraft("clock");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private Loc start;
  private Loc landing;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Blinker", 0.5D, 61D, 0.5D);
    player.holding(new FakeItem(CLOCK, 1));
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    player.setHealth(20D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillRift.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    start = new Loc(platform.world(), 0.5D, 61D, 0.5D);
    landing = new Loc(platform.world(), 20.5D, 61D, 0.5D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theGestureReturnsToTheDepartureWithTheHealthHadThere() {
    kit.mutate("chronos", "rift");
    kit.learn("chronos", "rift", Recipe.CONVERGENCE, 3);
    pearl(TeleportCause.ENDER_PEARL);
    player.setHealth(6D);

    assertThat(swap().isCancelled()).isTrue();
    platform.scheduler().advance(2);

    assertThat(player.location().distanceSquared(start)).isLessThan(0.01D);
    assertThat(player.health()).isEqualTo(20D);
  }

  @Test
  void riftLeadingWorksTheSameWay() {
    kit.mutate("rift", "chronos");
    kit.learn("rift", "chronos", Recipe.CONVERGENCE, 1);
    pearl(TeleportCause.CHORUS_FRUIT);

    swap();
    platform.scheduler().advance(2);

    assertThat(player.location().distanceSquared(start)).isLessThan(0.01D);
  }

  @Test
  void aTeleportThatIsNotARiftOneCannotBeRecalled() {
    kit.mutate("chronos", "rift");
    kit.learn("chronos", "rift", Recipe.CONVERGENCE, 3);
    pearl(TeleportCause.COMMAND);

    assertThat(swap().isCancelled()).isFalse();

    assertThat(player.location().distanceSquared(landing)).isLessThan(0.01D);
  }

  @Test
  void nothingHappensWithoutTheMutationOrAfterTheWindow() {
    pearl(TeleportCause.ENDER_PEARL);
    assertThat(swap().isCancelled()).isFalse();

    kit.mutate("chronos", "rift");
    kit.learn("chronos", "rift", Recipe.CONVERGENCE, 3);
    signature().getConfig().windowSecondsAtTopLevel = -1D;
    player.moveTo(start);
    pearl(TeleportCause.ENDER_PEARL);

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.location().distanceSquared(landing)).isLessThan(0.01D);
  }

  @Test
  void oneRecallPerBlinkAndThenTheCooldown() {
    kit.mutate("chronos", "rift");
    kit.learn("chronos", "rift", Recipe.CONVERGENCE, 3);
    pearl(TeleportCause.ENDER_PEARL);
    swap();
    platform.scheduler().advance(2);

    pearl(TeleportCause.ENDER_PEARL);
    assertThat(swap().isCancelled()).isFalse();

    assertThat(player.location().distanceSquared(landing)).isLessThan(0.01D);
  }

  @Test
  void neverAcrossWorlds() {
    kit.mutate("chronos", "rift");
    kit.learn("chronos", "rift", Recipe.CONVERGENCE, 3);
    pearl(TeleportCause.ENDER_PEARL);
    Loc elsewhere = new Loc(platform.host().world("world_nether", true), 0.5D, 61D, 0.5D);
    player.moveTo(elsewhere);

    assertThat(swap().isCancelled()).isTrue();
    platform.scheduler().advance(2);

    assertThat(player.location()).isEqualTo(elsewhere);
  }

  private RecallTheBlink signature() {
    return kit.registry().getSkill("chronos").getSignatures().stream().filter(RecallTheBlink.class::isInstance)
        .map(RecallTheBlink.class::cast).findFirst().orElseThrow();
  }

  private void pearl(TeleportCause cause) {
    adapt.post(new PlayerTeleportEvent(player, player.location(), landing, cause));
    player.moveTo(landing);
  }

  private PlayerSwapHandItemsEvent swap() {
    player.setSneaking(true);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }
}
