package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Brewing with Swords in both orders, including the hotbar potion behind a shield or clock. */
class BrewingBlocksRuntimeTest {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");
  private static final Id GLASS_BOTTLE = Id.minecraft("glass_bottle");
  private static final Set<Id> HELPFUL = Set.of(Id.minecraft("speed"), Id.minecraft("haste"),
      Id.minecraft("strength"), Id.minecraft("jump_boost"), Id.minecraft("regeneration"),
      Id.minecraft("resistance"), Id.minecraft("fire_resistance"), Id.minecraft("water_breathing"),
      Id.minecraft("night_vision"), Id.minecraft("absorption"));

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
    platform.host().potion(Id.minecraft("swiftness"), EffectInstance.of(SPEED, 3_600, 0));
    platform.host().potion(Id.minecraft("poison"), EffectInstance.of(POISON, 900, 0));
    platform.host().potion(Id.minecraft("night_vision"), EffectInstance.of(NIGHT_VISION, 3_600, 0));
    player = platform.player("Alchemist", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillBrewing.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcWhenAPotionIsDrunkButNotWater() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");

    drink("water");
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();

    drink("swiftness");
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("brewing")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void graftAlsoFiresWhenAThrownPotionBreaks() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");
    FakeProjectile thrown = new FakeProjectile(Id.minecraft("splash_potion"), new Loc(platform.world(), 0.5D, 61D, 1.5D));
    thrown.setShooter(player);

    adapt.post(new PotionSplashEvent(thrown, potion("splash_potion", "poison"), List.of(EffectInstance.of(POISON, 900, 0)),
        Map.<LivingRef, Double>of()));

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
  }

  @Test
  void echoSplashesAQuarterOfTheOffHandPotionOnTheTarget() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.ECHO, 1);
    player.inventory().setOffHand(potion("potion", "poison"));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.effect(POISON).durationTicks()).isEqualTo(900);
    assertThat(player.inventory().offHand().type()).isEqualTo(Id.minecraft("potion"));
    for (int quarter = 2; quarter <= 4; quarter++) {
      BrewingDoses.take(player, BrewingDoses.find(player));
    }
    assertThat(player.inventory().offHand().type()).isEqualTo(GLASS_BOTTLE);
    assertThat(BrewingDoses.find(player)).isNull();
  }

  @Test
  void aBottleWithADoseDrawnCannotStillBeDrunkOrThrownForItsEffects() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.ECHO, 1);
    player.inventory().setOffHand(potion("potion", "poison"));

    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 4D, true);

    PotionContents left = player.inventory().offHand().potionContents().orElseThrow();
    assertThat(BrewingDoses.effects(left)).isEmpty();
    assertThat(SkillBrewing.isBrewed(left)).isFalse();
    assertThat(BrewingDoses.find(player).effects()).containsExactly(EffectInstance.of(POISON, 900, 0));
  }

  @Test
  void theRestOfAStackKeepsItsPotionOnceTheOpenedBottleIsEmpty() {
    FakeItem stack = potion("splash_potion", "poison");
    stack.setAmount(2);
    player.inventory().setOffHand(stack);

    for (int quarter = 1; quarter <= 4; quarter++) {
      BrewingDoses.take(player, BrewingDoses.find(player));
    }

    assertThat(player.inventory().offHand().amount()).isEqualTo(1);
    assertThat(player.inventory().offHand().potionContents().orElseThrow().potion()).isEqualTo(POISON);
    BrewingDoses.take(player, BrewingDoses.find(player));
    assertThat(BrewingDoses.effects(player.inventory().offHand().potionContents().orElseThrow())).isEmpty();
  }

  @Test
  void aShieldOrClockInTheOffHandLeavesTheFirstHotbarPotionToSplash() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.ECHO, 1);
    player.inventory().setOffHand(FakeItem.tool("shield", 336));
    player.inventory().setItem(4, potion("potion", "poison"));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.hasEffect(POISON)).isTrue();
    assertThat(player.inventory().offHand().type()).isEqualTo(Id.minecraft("shield"));
    player.inventory().setOffHand(FakeItem.of("clock"));
    assertThat(BrewingDoses.find(player).bottle().isSimilar(player.inventory().item(4))).isTrue();
    player.inventory().setOffHand(FakeItem.empty());
    assertThat(BrewingDoses.find(player)).isNull();
  }

  @Test
  void barterSpendsSwordDurabilityForOneHelpfulEffect() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effects()).hasSize(1);
    assertThat(HELPFUL).contains(player.effects().iterator().next().type());
    assertThat(player.effects().iterator().next().durationTicks()).isEqualTo(120);
  }

  @Test
  void attunementNeedsTheDrunkPotionStillActive() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.ATTUNEMENT, 1);

    drink("swiftness");
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.addEffect(EffectInstance.of(SPEED, 3_600, 0));
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceAppliesTheOffHandPotionAtHalfDurationAndCutsAfterASwordHit() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.CONVERGENCE, 1);
    player.inventory().setOffHand(potion("potion", "night_vision"));
    platform.world().set(0, 61, 2, "short_grass");

    drink("swiftness");
    assertThat(player.hasEffect(NIGHT_VISION)).isFalse();

    kit.hit(player, MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D), 1D, false);
    drink("swiftness");

    assertThat(player.effect(NIGHT_VISION).durationTicks()).isEqualTo(1_800);
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
  }

  @Test
  void swordsLeadBarterSpendsAQuarterOfTheOffHandPotionAndRefusesWithout() {
    kit.mutate("swords", "brewing");
    kit.learn("swords", "brewing", Recipe.BARTER, 1);
    player.inventory().setOffHand(potion("potion", "swiftness"));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
    for (int quarter = 2; quarter <= 4; quarter++) {
      BrewingDoses.take(player, BrewingDoses.find(player));
    }
    assertThat(player.inventory().offHand().type()).isEqualTo(GLASS_BOTTLE);

    assertThat(swap().isCancelled()).isFalse();
  }

  @Test
  void swordsLeadAttunementGrantsAHelpfulEffectWhileFighting() {
    kit.mutate("swords", "brewing");
    kit.learn("swords", "brewing", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.effects()).isEmpty();

    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 1D, false);
    tickEngine();

    assertThat(player.effects()).hasSize(1);
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(0, 61, 2, "short_grass");
    drink("swiftness");
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    MutationAdaptation graft = kit.adaptation("brewing", "swords", Recipe.GRAFT);
    profile.getData().getSkillLine("brewing").setKnowledge(100);
    assertThat(AdaptationLearningTransaction.learn(graft, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);

    kit.mutate("brewing", "swords");
    drink("swiftness");

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    assertThat(graft.getLevel(player)).isZero();
  }

  private void drink(String base) {
    adapt.post(new PlayerItemConsumeEvent(player, potion("potion", base), Hand.MAIN_HAND, null));
  }

  private static FakeItem potion(String type, String base) {
    FakeItem item = new FakeItem(Id.minecraft(type), 1);
    item.setPotionContents(PotionContents.of(Id.minecraft(base)));
    return item;
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
