package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Swords leading Herbalism ("Verdant Duelist"): all five mutated adaptations, end to end. */
class SwordsBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id MAGIC = Id.minecraft("magic");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Duelist", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theFiveFollowTheUpgradesPricedByRecipeAndTakeOnePowerEach() {
    kit.mutate("swords", "herbalism");
    MutatedSkill mutated = kit.mutated("swords", "herbalism");

    assertThat(mutated.getAdaptations()).extracting(Adaptation::getName).containsExactly(
        "upgraded-swords-witherblade", "upgraded-swords-tempest-flow", "upgraded-swords-duelists-verdict",
        "upgraded-herbalism-wildgrowth", "upgraded-herbalism-harvest-moon", "upgraded-herbalism-iron-stomach",
        "mutated-swords-herbalism-graft", "mutated-swords-herbalism-echo", "mutated-swords-herbalism-barter",
        "mutated-swords-herbalism-attunement", "mutated-swords-herbalism-convergence");
    assertThat(mutated.getAdaptationRowGroups()).last().isEqualTo(List.of(
        "mutated-swords-herbalism-graft", "mutated-swords-herbalism-echo", "mutated-swords-herbalism-barter",
        "mutated-swords-herbalism-attunement", "mutated-swords-herbalism-convergence"));
    MutationAdaptation graft = kit.adaptation("swords", "herbalism", Recipe.GRAFT);
    assertThat(graft.getDisplayName()).contains("Verdant Graft");
    assertThat(List.of(1, 2, 3, 4, 5).stream().map(graft::getCostFor).toList()).isEqualTo(Recipe.GRAFT.price().costs());
    assertThat(kit.adaptation("swords", "herbalism", Recipe.CONVERGENCE).getMaxLevel()).isEqualTo(3);

    profile.getData().getSkillLine("swords").setKnowledge(100);
    assertThat(AdaptationLearningTransaction.learn(graft, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(profile.getData().getSkillLine("swords").getKnowledge()).isEqualTo(100L - graft.getCostFor(1));
    assertThat(profile.getData().getUsedPower()).isEqualTo(1);
    assertThat(adapt.core().getAdaptServer().getSkillRegistry().getAdaptation(graft.getName())).isSameAs(graft);
  }

  @Test
  void graftRootsTheTargetOfASwordCritWithBothParentsCues() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, false);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
    assertThat(player.heardSounds()).contains(SoundPalettes.of("swords").fight());
    platform.scheduler().advance(4);
    assertThat(player.heardSounds()).contains(SoundPalettes.of("herbalism").fight());
  }

  @Test
  void echoCutsTheMatureArcIntoTheInventoryWhenACropIsHarvested() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.ECHO, 1);
    crop(0, 61, 2, 7);
    crop(1, 61, 2, 3);
    platform.world().set(0, 61, 3, "short_grass");
    BlockRef harvested = crop(-1, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, harvested, true, 0));

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(0, 61, 3).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(1, 61, 2).type()).isEqualTo(WHEAT);
    assertThat(player.inventory().contains(WHEAT, 1)).isTrue();
  }

  @Test
  void theArcCentresOnTheHarvestedCropAndWearsTheSword() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.ECHO, 1);
    platform.world().set(0, 61, 1, "short_grass");
    platform.world().set(6, 61, 9, "short_grass");
    BlockRef harvested = crop(6, 61, 8, 7);

    adapt.post(new BlockBreakEvent(player, harvested, true, 0));

    assertThat(platform.world().blockAt(6, 61, 9).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(0, 61, 1).type()).isEqualTo(Id.minecraft("short_grass"));
    assertThat(platform.protection().broken()).hasSize(1);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(1);
  }

  @Test
  void growingCaneAndCactusAreCutAboveTheirBase() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.ECHO, 1);
    for (int y = 61; y <= 62; y++) {
      platform.world().set(new BlockPos(0, y, 3), new FakeBlockState(Id.minecraft("sugar_cane"), Map.of("age", "3")));
      platform.world().set(new BlockPos(1, y, 3), new FakeBlockState(Id.minecraft("cactus"), Map.of("age", "0")));
    }
    BlockRef harvested = crop(-1, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, harvested, true, 0));

    assertThat(platform.world().blockAt(0, 62, 3).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(1, 62, 3).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(0, 61, 3).type()).isEqualTo(Id.minecraft("sugar_cane"));
    assertThat(platform.world().blockAt(1, 61, 3).type()).isEqualTo(Id.minecraft("cactus"));
    assertThat(player.inventory().contains(Id.minecraft("sugar_cane"), 1)).isTrue();
  }

  @Test
  void barterSpendsThreeSeedsOnSneakSwapForAttackSpeed() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(Id.minecraft("wheat_seeds"), 4));
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("wheat_seeds"), 2)).isFalse();
    assertThat(player.inventory().contains(Id.minecraft("wheat_seeds"), 1)).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    PlayerSwapHandItemsEvent broke = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    assertThat(broke.isCancelled()).isFalse();
  }

  @Test
  void attunementFeedsEveryTwentySecondsWhileFighting() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.ATTUNEMENT, 1);
    player.setFoodLevel(10);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(10);

    kit.hit(player, zombie, 1D, false);
    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(12);

    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void convergenceFiresBothFightStrikesOnlyInCropsAndThenWaits() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.CONVERGENCE, 1);
    FakeLiving first = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving second = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);

    kit.hit(player, first, 4D, true);
    platform.scheduler().advance(10);
    assertThat(first.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);

    platform.world().set(0, 61, 0, "short_grass");
    kit.hit(player, first, 4D, true);
    platform.scheduler().advance(10);
    assertThat(first.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
    assertThat(first.lastDamageType()).isEqualTo(MAGIC);

    kit.hit(player, second, 4D, true);
    platform.scheduler().advance(10);
    assertThat(second.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);
    MutationAdaptation graft = kit.adaptation("swords", "herbalism", Recipe.GRAFT);
    profile.getData().getSkillLine("swords").setKnowledge(100);
    assertThat(AdaptationLearningTransaction.learn(graft, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);
    assertThat(profile.getData().getSkillLineNullable("mutated-swords-herbalism")).isNull();

    kit.mutate("swords", "herbalism");
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);
    assertThat(graft.getLevel(player)).isZero();
  }

  @Test
  void spawnerMobsStillTakeStrikesThatPayNothing() {
    kit.mutate("swords", "herbalism");
    kit.learn("swords", "herbalism", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
