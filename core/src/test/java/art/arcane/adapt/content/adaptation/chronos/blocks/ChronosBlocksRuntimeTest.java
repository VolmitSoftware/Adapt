package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.chronos.upgraded.SecondChance;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeBrewingStand;
import art.arcane.adapt.testkit.FakeFurnace;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Chronos with Herbalism in both orderings: the clock gesture, time skips, stretched potions and the clock cooldown. */
class ChronosBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id SPEED = Id.minecraft("speed");

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
    player = platform.player("Keeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftGrowsCropsOnlyWhenSneakSwappingWithAClockAndPlaysChronosFirst() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.GRAFT, 1);
    BlockRef young = crop(0, 61, 2, 3);

    swap(true);
    assertThat(young.state().age()).isEqualTo(3);

    player.holding(new FakeItem(CLOCK, 1));
    swap(false);
    assertThat(young.state().age()).isEqualTo(3);

    swap(true);
    platform.scheduler().advance(4);
    assertThat(young.state().age()).isEqualTo(4);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("chronos").work(),
        SoundPalettes.of("herbalism").work());
  }

  @Test
  void graftStillFiresWhenSecondChanceTakesTheSwap() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.GRAFT, 1);
    SecondChance secondChance = kit.registry().getSkill("chronos").getUpgrades().stream()
        .filter(SecondChance.class::isInstance).map(SecondChance.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("chronos").setAdaptation(secondChance, 1);
    BlockRef young = crop(0, 61, 2, 3);
    player.holding(new FakeItem(CLOCK, 1));

    PlayerSwapHandItemsEvent swap = swap(true);
    platform.scheduler().advance(4);

    assertThat(swap.isCancelled()).isTrue();
    assertThat(young.state().age()).isEqualTo(4);
  }

  @Test
  void aSwapAnotherAbilityTookDoesNotFireTheGraft() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.GRAFT, 1);
    BlockRef young = crop(0, 61, 2, 3);
    player.holding(new FakeItem(CLOCK, 1));
    player.setSneaking(true);
    PlayerSwapHandItemsEvent taken = new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand());
    taken.setCancelled(true);

    adapt.post(taken);
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(3);
  }

  @Test
  void aSwapSeasonTurnUsesDoesNotAlsoFireTheGraft() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.GRAFT, 1);
    kit.learn("chronos", "herbalism", Recipe.CONVERGENCE, 1);
    BlockRef young = crop(0, 61, 2, 3);
    player.holding(new FakeItem(CLOCK, 1));

    PlayerSwapHandItemsEvent swap = swap(true);
    platform.scheduler().advance(4);

    assertThat(swap.isCancelled()).isTrue();
    assertThat(young.state().age()).isEqualTo(4);
  }

  @Test
  void echoSkipsFurnacesAndBrewsAheadAndGrowsCropsWhenACropIsHarvested() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.ECHO, 1);
    FakeFurnace furnace = furnace(2, 61, 0, 400);
    FakeFurnace cold = furnace(-2, 61, 0, 400);
    cold.setBurnTime(0);
    platform.world().set(0, 61, -2, "brewing_stand");
    FakeBrewingStand stand = platform.world().brewingStand(new BlockPos(0, 61, -2));
    stand.setBrewingTime(300);
    BlockRef young = crop(1, 61, 1, 2);

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(furnace.cookTime()).isEqualTo(210);
    assertThat(cold.cookTime()).isEqualTo(10);
    assertThat(stand.brewingTime()).isEqualTo(100);
    assertThat(young.state().age()).isEqualTo(3);
  }

  @Test
  void cropsTheStrikeGrewCountAsBoneMealed() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.ECHO, 1);
    BlockRef young = crop(1, 61, 1, 2);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

      provenance.verify(() -> XpProvenance.recordBonemeal(
          argThat((BlockRef grown) -> grown.pos().equals(young.pos()))));
    }
    assertThat(young.state().age()).isEqualTo(3);
  }

  @Test
  void aBoneMealedHarvestSkipsTheTimeSkip() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.ECHO, 1);
    FakeFurnace furnace = furnace(2, 61, 0, 400);
    BlockRef harvested = crop(0, 61, 2, 7);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, harvested, true, 0));
    }

    assertThat(furnace.cookTime()).isEqualTo(10);
  }

  @Test
  void barterOnDoubleSneakStretchesActivePotionsButLeavesInfiniteOnes() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(Id.minecraft("wheat_seeds"), 3));
    player.addEffect(EffectInstance.of(SPEED, 200, 1));
    player.addEffect(EffectInstance.of(Id.minecraft("night_vision"), EffectInstance.INFINITE, 0));
    player.addEffect(EffectInstance.of(Id.minecraft("poison"), 200, 0));

    PlayerSwapHandItemsEvent swap = swap(true);
    assertThat(swap.isCancelled()).isFalse();
    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(200);

    doubleSneak();

    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(300);
    assertThat(player.effect(SPEED).amplifier()).isEqualTo(1);
    assertThat(player.effect(Id.minecraft("night_vision")).isInfinite()).isTrue();
    assertThat(player.effect(Id.minecraft("poison")).durationTicks()).isEqualTo(200);
    assertThat(player.inventory().contains(Id.minecraft("wheat_seeds"), 1)).isFalse();
  }

  @Test
  void repeatedBoonsExtendOneApplicationOnlyOnce() {
    kit.mutate("chronos", "herbalism");
    MutationAdaptation barter = kit.learn("chronos", "herbalism", Recipe.BARTER, 1);
    ChronosBoon boon = boon();
    BlockUse use = new BlockUse(player, barter, 1, null, null, "chronos");
    player.addEffect(EffectInstance.of(SPEED, 200, 1));

    assertThat(boon.grant(use)).isEqualTo(Outcome.INSTANT);
    for (int i = 0; i < 20; i++) {
      assertThat(boon.grant(use)).isEqualTo(Outcome.MISSED);
    }

    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(300);
  }

  @Test
  void aShortBuffIsAtMostDoubled() {
    kit.mutate("chronos", "herbalism");
    MutationAdaptation barter = kit.learn("chronos", "herbalism", Recipe.BARTER, 1);
    ChronosBoon boon = boon();
    BlockUse use = new BlockUse(player, barter, 1, null, null, "chronos");
    player.addEffect(EffectInstance.of(Id.minecraft("regeneration"), 40, 1));

    boon.grant(use);
    boon.grant(use);

    assertThat(player.effect(Id.minecraft("regeneration")).durationTicks()).isEqualTo(80);
  }

  @Test
  void aFreshPotionStartsANewBoonAllowance() {
    kit.mutate("chronos", "herbalism");
    MutationAdaptation barter = kit.learn("chronos", "herbalism", Recipe.BARTER, 1);
    ChronosBoon boon = boon();
    BlockUse use = new BlockUse(player, barter, 1, null, null, "chronos");
    player.addEffect(EffectInstance.of(SPEED, 200, 1));
    for (int i = 0; i < 20; i++) {
      boon.grant(use);
    }

    player.addEffect(EffectInstance.of(SPEED, 3600, 1));

    assertThat(boon.grant(use)).isEqualTo(Outcome.INSTANT);
    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(3700);
  }

  @Test
  void attunementFeedsOnlyWithAClockInTheOffHand() {
    kit.mutate("chronos", "herbalism");
    kit.learn("chronos", "herbalism", Recipe.ATTUNEMENT, 1);
    player.setFoodLevel(10);

    player.holding(new FakeItem(CLOCK, 1));
    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(10);

    player.inventory().setOffHand(new FakeItem(CLOCK, 1));
    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void echoAsPartnerRootsTheNearbyHostileOnTheClockGesture() {
    kit.mutate("herbalism", "chronos");
    kit.learn("herbalism", "chronos", Recipe.ECHO, 1);
    player.holding(new FakeItem(CLOCK, 1));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    swap(true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
  }

  @Test
  void barterAsPartnerPutsTheClockOnCooldownAndRefusesWhileItCools() {
    kit.mutate("herbalism", "chronos");
    kit.learn("herbalism", "chronos", Recipe.BARTER, 1);
    player.setFoodLevel(10);

    doubleSneak();
    assertThat(player.foodLevel()).isEqualTo(10);

    player.inventory().addItem(new FakeItem(CLOCK, 1));
    doubleSneak();
    assertThat(player.foodLevel()).isEqualTo(12);
    assertThat(player.cooldownTicks(CLOCK)).isEqualTo(400);

    player.setCooldown(CLOCK, 0);
    doubleSneak();
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    player.holding(new FakeItem(CLOCK, 1));
    BlockRef young = crop(0, 61, 2, 3);

    swap(true);
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(3);
  }

  private ChronosBoon boon() {
    return kit.registry().getSkill("chronos").getBlocks().stream().filter(ChronosBoon.class::isInstance)
        .map(ChronosBoon.class::cast).findFirst().orElseThrow();
  }

  private PlayerSwapHandItemsEvent swap(boolean sneaking) {
    player.setSneaking(sneaking);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }

  private void doubleSneak() {
    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, false));
    adapt.post(new PlayerToggleSneakEvent(player, true));
  }

  private FakeFurnace furnace(int x, int y, int z, int total) {
    platform.world().set(x, y, z, "furnace");
    FakeFurnace furnace = platform.world().furnace(new BlockPos(x, y, z));
    furnace.setBurnTime(800);
    furnace.setSmelting(new FakeItem(Id.minecraft("raw_iron"), 4));
    furnace.setCookTimeTotal(total);
    furnace.setCookTime(10);
    return furnace;
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
