package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** The Unarmed blocks paired with Swords, and with Herbalism for the work Strike. */
class UnarmedBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id ABSORPTION = Id.minecraft("absorption");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private MutationKit kit;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Brawler", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class, SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftBleedsOnTheThirdBareHandHitInARowWithBothCues() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 1D, false);
    kit.hit(player, zombie, 1D, false);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    kit.hit(player, zombie, 1D, false);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("unarmed").fight(),
        SoundPalettes.of("swords").fight());
  }

  @Test
  void aHitWithAnItemStartsTheCountOver() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 1D, false);
    kit.hit(player, zombie, 1D, false);
    player.holding(FakeItem.of("stick"));
    kit.hit(player, zombie, 1D, false);
    player.holding(FakeItem.empty());
    kit.hit(player, zombie, 1D, false);
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
  }

  @Test
  void aSwordCritGraftKnocksBackAndWeakensTheTarget() {
    kit.mutate("swords", "unarmed");
    kit.learn("swords", "unarmed", Recipe.GRAFT, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    EffectInstance weakness = zombie.effect(WEAKNESS);
    assertThat(weakness).isNotNull();
    assertThat(weakness.durationTicks()).isEqualTo(60);
    assertThat(zombie.velocity().x()).isGreaterThan(0D);
  }

  @Test
  void echoFiresFromTheThirdBareHandHitWhenUnarmedIsThePartner() {
    kit.mutate("swords", "unarmed");
    kit.learn("swords", "unarmed", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 1D, false);
    kit.hit(player, zombie, 1D, false);
    kit.hit(player, zombie, 1D, false);
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aHarvestReadiesAMeditationThatGivesAbsorptionAfterAStillSneak() {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, 1);

    sitDown();
    assertThat(player.effect(ABSORPTION)).isNull();
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    sitDown();

    EffectInstance absorption = player.effect(ABSORPTION);
    assertThat(absorption).isNotNull();
    assertThat(absorption.amplifier()).isZero();
  }

  @Test
  void theKeptMeditationStandsDownWhileTheStrikeHasOneReady() {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, 1);

    sitDown();
    platform.scheduler().advance(40);
    assertThat(player.absorption()).as("kept Meditation runs on its own").isPositive();
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));
    player.setAbsorption(0D);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    sitDown();

    assertThat(player.effect(ABSORPTION)).isNotNull();
    assertThat(player.absorption()).isZero();
  }

  @Test
  void movingOrHoldingSomethingBreaksTheMeditation() {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, 1);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));

    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    player.moveTo(new Loc(platform.world(), 1.5D, 61D, 0.5D));
    platform.scheduler().advance(61);
    assertThat(player.effect(ABSORPTION)).isNull();

    player.holding(FakeItem.of("stick"));
    sitDown();
    assertThat(player.effect(ABSORPTION)).isNull();
  }

  @Test
  void oneMeditationPerCooldownHoweverOftenTheStrikeFires() {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, 1);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    sitDown();
    assertThat(player.effect(ABSORPTION)).isNotNull();
    player.removeEffect(ABSORPTION);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    platform.scheduler().advance(200);
    adapt.post(new BlockBreakEvent(player, crop(-2, 61, 2), true, 0));
    sitDown();

    assertThat(player.effect(ABSORPTION)).isNull();
  }

  @Test
  void aBonemealedHarvestReadiesNoMeditation() {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, 1);
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    }
    sitDown();

    assertThat(player.effect(ABSORPTION)).isNull();
  }

  @Test
  void afterTheBoonBareHandKillsOfNaturalMobsRestoreFood() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, 1);
    withoutHeritageSecondWind();
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);
    assertThat(swap().isCancelled()).isTrue();
    player.holding(FakeItem.empty());
    player.setSneaking(false);
    player.setFoodLevel(10);

    FakeLiving farmed = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    MobOrigin.record(farmed, CreatureSpawnEvent.Reason.SPAWNER);
    adapt.post(kill(farmed));
    platform.scheduler().advance(1);
    assertThat(player.foodLevel()).isEqualTo(10);

    adapt.post(kill(MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void theSecondBarterLevelRestoresThreeFoodPerKill() {
    assertThat(foodFromABoonKillAt(2)).isEqualTo(3);
  }

  @Test
  void theTopBarterLevelRestoresFourFoodPerKill() {
    assertThat(foodFromABoonKillAt(3)).isEqualTo(4);
  }

  @Test
  void aKillTheBoonPaysForDoesNotAlsoPayTheKeptSecondWind() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, 1);
    castBoon();
    player.setFoodLevel(10);

    adapt.post(kill(MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);

    assertThat(player.foodLevel()).isEqualTo(12);
    assertThat(profile.getData().getStat("unarmed.second-wind.second-winds")).isZero();
  }

  @Test
  void theKeptSecondWindStillPaysAKillWithoutTheBoon() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, 1);
    player.setFoodLevel(10);

    adapt.post(kill(MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);

    assertThat(player.foodLevel()).isGreaterThan(10);
    assertThat(profile.getData().getStat("unarmed.second-wind.second-winds")).isEqualTo(1D);
  }

  @Test
  void theSecondGraftLevelHoldsTheAbsorptionLongerThanTheFirst() {
    EffectInstance absorption = absorptionAt(2);

    assertThat(absorption.amplifier()).isEqualTo(1);
    assertThat(absorption.durationTicks()).isEqualTo(1500);
  }

  @Test
  void theTopGraftLevelGivesSixHeartsForTwoMinutes() {
    EffectInstance absorption = absorptionAt(5);

    assertThat(absorption.amplifier()).isEqualTo(2);
    assertThat(absorption.durationTicks()).isEqualTo(2400);
  }

  @Test
  void withoutTheBoonABareHandKillRestoresNothing() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, 1);
    withoutHeritageSecondWind();
    player.setFoodLevel(10);

    adapt.post(kill(MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);

    assertThat(player.foodLevel()).isEqualTo(10);
  }

  @Test
  void barterSpendsSwordDurabilityForRegeneration() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    EffectInstance regeneration = player.effect(REGENERATION);
    assertThat(regeneration).isNotNull();
    assertThat(regeneration.durationTicks()).isEqualTo(60);
    assertThat(regeneration.amplifier()).isZero();
  }

  @Test
  void hungerPaysForBarterAndAnEmptyBarRefuses() {
    kit.mutate("swords", "unarmed");
    kit.learn("swords", "unarmed", Recipe.BARTER, 1);
    player.setSneaking(true);
    player.setFoodLevel(0);

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setFoodLevel(10);
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(9);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementHoldsOnlyWithEmptyHandsInCombat() {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.ATTUNEMENT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    kit.hit(player, zombie, 1D, false);
    player.inventory().setOffHand(FakeItem.of("torch"));
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.inventory().setOffHand(FakeItem.empty());
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceAsksForABareHandHitInTheLastFourSeconds() {
    kit.mutate("swords", "unarmed");
    kit.learn("swords", "unarmed", Recipe.CONVERGENCE, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.effect(WEAKNESS)).isNull();

    player.holding(FakeItem.empty());
    kit.hit(player, zombie, 1D, false);
    player.holding(FakeItem.tool("iron_sword", 250));
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.effect(WEAKNESS)).isNotNull();
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    for (int i = 0; i < 3; i++) {
      kit.hit(player, zombie, 1D, false);
    }
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
    sitDown();
    assertThat(player.effect(ABSORPTION)).isNull();
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private int foodFromABoonKillAt(int level) {
    kit.mutate("unarmed", "swords");
    kit.learn("unarmed", "swords", Recipe.BARTER, level);
    withoutHeritageSecondWind();
    castBoon();
    player.setFoodLevel(10);

    adapt.post(kill(MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);
    return player.foodLevel() - 10;
  }

  private EffectInstance absorptionAt(int level) {
    kit.mutate("herbalism", "unarmed");
    kit.learn("herbalism", "unarmed", Recipe.GRAFT, level);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    sitDown();
    EffectInstance absorption = player.effect(ABSORPTION);
    assertThat(absorption).isNotNull();
    return absorption;
  }

  private void castBoon() {
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);
    assertThat(swap().isCancelled()).isTrue();
    player.holding(FakeItem.empty());
    player.setSneaking(false);
  }

  private void sitDown() {
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(61);
  }

  private EntityDeathEvent kill(FakeLiving victim) {
    return new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0);
  }

  private void withoutHeritageSecondWind() {
    profile.getData().getPreferences().set("unarmed-second-wind", "enabled", "OFF");
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
