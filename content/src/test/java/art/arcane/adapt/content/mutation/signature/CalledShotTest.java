package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.engine.fx.ViewerGlowPort;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.hunter.upgraded.ApexPredator;
import art.arcane.adapt.content.adaptation.hunter.upgraded.TrackersCompass;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Hunter and Ranged in either order: the compass's quarry glows far away and a drawn bow locks onto it. */
class CalledShotTest {
  private static final Id COW = Id.minecraft("cow");
  private static final Id BOW = Id.minecraft("bow");

  @TempDir
  Path data;

  private TestPlatform platform;
  private ViewerHost viewers;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    viewers = mock(ViewerHost.class);
    platform = TestPlatform.builder(data).viewers(viewers).build();
    player = platform.player("Marksman", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillRanged.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    player.inventory().setOffHand(compass(COW));
    player.holding(new FakeItem(BOW, 1));
  }

  @AfterEach
  void stop() {
    ViewerGlowPort.current().discardViewer(player.uuid());
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theQuarryGlowsForYouPastTheCompassRange() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 1);
    PairMob cow = PairMob.animal(platform, COW, 0.5D, 80.5D);

    mark("hunter", "ranged");

    verify(viewers).setGlow(player, cow, GlowColor.GOLD);
  }

  @Test
  void rangedAsLeadMarksTheQuarryToo() {
    kit.mutate("ranged", "hunter");
    kit.learn("ranged", "hunter", Recipe.CONVERGENCE, 1);
    PairMob cow = PairMob.animal(platform, COW, 0.5D, 20.5D);

    mark("ranged", "hunter");

    verify(viewers).setGlow(player, cow, GlowColor.GOLD);
  }

  @Test
  void onlyNaturalCreaturesGlow() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 3);
    PairMob bred = PairMob.animal(platform, COW, 0.5D, 20.5D);
    MobOrigin.record(bred, CreatureSpawnEvent.Reason.BREEDING);
    PairMob named = PairMob.animal(platform, COW, 0.5D, 22.5D);
    named.setCustomName("Bessie", true);

    mark("hunter", "ranged");

    verify(viewers, never()).setGlow(eq(player), any(), any(GlowColor.class));
  }

  @Test
  void findingNoQuarryIsKeptForTheRescanPulsesBeforeSearchingAgain() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 1);
    mark("hunter", "ranged");
    PairMob cow = PairMob.animal(platform, COW, 0.5D, 20.5D);

    for (int pulse = 0; pulse < 6; pulse++) {
      mark("hunter", "ranged");
    }
    verify(viewers, never()).setGlow(eq(player), any(), any(GlowColor.class));

    mark("hunter", "ranged");
    verify(viewers).setGlow(player, cow, GlowColor.GOLD);
  }

  @Test
  void theTopLevelGlowReachStopsAt128Blocks() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 3);
    PairMob beyond = PairMob.animal(platform, COW, 0.5D, 140.5D);
    mark("hunter", "ranged");
    verify(viewers, never()).setGlow(eq(player), any(), any(GlowColor.class));

    beyond.moveTo(new Loc(platform.world(), 0.5D, 61D, 120.5D));
    for (int pulse = 0; pulse < 7; pulse++) {
      mark("hunter", "ranged");
    }
    verify(viewers).setGlow(player, beyond, GlowColor.GOLD);
  }

  @Test
  void aBowDrawnAtTheQuarryLocksOnAndTheArrowTurnsToIt() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 1);
    PairMob.animal(platform, COW, 0.5D, 30.5D);
    mark("hunter", "ranged");

    FakeProjectile arrow = drawAndLoose();
    fly(arrow);

    assertThat(arrow.velocity().z()).isGreaterThan(0.5D);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void theLockRangeGrowsWithTheLevel() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 1);
    PairMob.animal(platform, COW, 0.5D, 55.5D);
    mark("hunter", "ranged");

    FakeProjectile missed = drawAndLoose();
    fly(missed);
    assertThat(missed.velocity().z()).isZero();

    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 3);
    FakeProjectile called = drawAndLoose();
    fly(called);
    assertThat(called.velocity().z()).isGreaterThan(0.5D);
  }

  @Test
  void aBowAimedAwayFromTheQuarryDoesNotLock() {
    kit.mutate("hunter", "ranged");
    kit.learn("hunter", "ranged", Recipe.CONVERGENCE, 3);
    PairMob.animal(platform, COW, 0.5D, -30.5D);
    mark("hunter", "ranged");

    FakeProjectile arrow = drawAndLoose();
    fly(arrow);

    assertThat(arrow.velocity().z()).isZero();
  }

  @Test
  void nothingGlowsOrLocksWithoutTheLearnedAdaptation() {
    kit.mutate("hunter", "ranged");
    PairMob.animal(platform, COW, 0.5D, 30.5D);
    mark("hunter", "ranged");
    FakeProjectile arrow = drawAndLoose();
    fly(arrow);

    verify(viewers, never()).setGlow(eq(player), any(), any(GlowColor.class));
    assertThat(arrow.velocity().z()).isZero();
  }

  private FakeProjectile drawAndLoose() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 0.5D, 62.6D, 0.5D)));
    arrow.setShooter(player);
    arrow.setOnGround(false);
    arrow.setVelocity(new Vec3(3D, 0D, 0D));
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), new FakeItem(Id.minecraft("arrow"), 1), arrow,
        Hand.MAIN_HAND, 1F, true));
    return arrow;
  }

  private void fly(FakeProjectile arrow) {
    for (int tick = 0; tick < 12; tick++) {
      platform.scheduler().advance(1);
      arrow.moveTo(arrow.location().add(arrow.velocity()));
    }
  }

  private void mark(String lead, String partner) {
    ((CalledShot) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature()).onTick();
    platform.scheduler().advance(2);
  }

  private ItemRef compass(Id kind) {
    TrackersCompass tool = (TrackersCompass) PairParts.owned(Id.adapt("hunter/upgrade/apex_predator"), ApexPredator.class).getTools().getFirst();
    ItemRef item = tool.create(new TrackersCompass.Data());
    item.data().set(DataKey.of(TrackersCompass.ID, DataCodecs.STRING),
        "{\"kinds\":[\"" + kind + "\"],\"selected\":0,\"killed\":[]}");
    return item;
  }
}
