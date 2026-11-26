package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Nether leading Brewing replaces Graft with Brimstone Flask: splash potions leave a ring that doses again. */
class BrimstoneFlaskTest {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id SAND = Id.minecraft("sand");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private FakeLiving zombie;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().effect(SPEED, EffectCategory.BENEFICIAL, false);
    platform.host().effect(POISON, EffectCategory.HARMFUL, false);
    player = platform.player("Infernal", 5D, 61D, 5D);
    adapt = TestAdapt.start(platform, List.of(SkillNether.class, SkillBrewing.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    zombie = MutationKit.zombie(platform.world(), 5.5D, 61D, 5.5D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theRingDosesEveryoneInsideOnceMoreAndSparesTheThrower() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.GRAFT, 5);
    assertThat(kit.adaptation("nether", "brewing", Recipe.GRAFT).getSignature()).isInstanceOf(BrimstoneFlask.class);

    splash(5D, 5D);
    platform.scheduler().advance(2);

    assertThat(zombie.effect(POISON).durationTicks()).isEqualTo(200);
    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(600);
    assertThat(player.hasEffect(POISON)).isFalse();
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void standingInTheRingDosesOnlyOnce() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.GRAFT, 1);
    splash(5D, 5D);
    platform.scheduler().advance(2);
    assertThat(zombie.effect(POISON).durationTicks()).isEqualTo(100);
    zombie.removeEffect(POISON);

    platform.scheduler().advance(80);

    assertThat(zombie.hasEffect(POISON)).isFalse();
  }

  @Test
  void theRingBurnsOutAfterFourSeconds() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.GRAFT, 1);
    splash(20D, 20D);
    platform.scheduler().advance(85);
    FakeLiving late = MutationKit.zombie(platform.world(), 20D, 61D, 20D);

    platform.scheduler().advance(40);

    assertThat(late.hasEffect(POISON)).isFalse();
  }

  @Test
  void theNextRingWaitsForTheCooldown() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.GRAFT, 5);
    splash(20D, 20D);
    platform.scheduler().advance(2);

    splash(5D, 5D);
    platform.scheduler().advance(20);

    assertThat(zombie.hasEffect(POISON)).isFalse();
  }

  @Test
  void brewingLeadingNetherOrNoMutationLeavesNoRing() {
    splash(5D, 5D);
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.GRAFT, 5);
    splash(5D, 5D);
    platform.scheduler().advance(20);

    assertThat(zombie.hasEffect(POISON)).isFalse();
  }

  @Test
  void theEchoPrimesSmeltingOnASplashTheRingDidNotTake() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.ECHO, 1);

    splash(5D, 5D);

    assertThat(brokenSandDrops()).isEqualTo(Id.minecraft("glass"));
  }

  @Test
  void aSplashTheRingTookDoesNotAlsoSetOffTheEcho() {
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.GRAFT, 1);
    kit.learn("nether", "brewing", Recipe.ECHO, 1);

    splash(5D, 5D);

    assertThat(brokenSandDrops()).isEqualTo(SAND);
  }

  private Id brokenSandDrops() {
    BlockRef block = platform.world().blockAt(5, 60, 5);
    FakeItemEntity drop = new FakeItemEntity(block.center(), new FakeItem(SAND, 1));
    BlockDropItemEvent dropped = adapt.post(new BlockDropItemEvent(player, block, FakeBlockState.of("sand"),
        new ArrayList<>(List.of(drop)), null));
    return dropped.items().getFirst().item().type();
  }

  private void splash(double x, double z) {
    FakeProjectile potion = platform.world().add(new FakeProjectile(Id.minecraft("splash_potion"), new Loc(platform.world(), x, 61D, z)));
    potion.setShooter(player);
    Map<LivingRef, Double> affected = Map.of();
    FakeItem item = new FakeItem(Id.minecraft("splash_potion"), 1);
    item.setPotionContents(new PotionContents(Id.minecraft("poison"), null, List.of(), null));
    adapt.post(new PotionSplashEvent(potion, item,
        List.of(EffectInstance.of(SPEED, 600, 0), EffectInstance.of(POISON, 200, 0)), affected));
  }
}
