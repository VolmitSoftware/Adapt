package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeEmulatorTest {
  private static final String ADAPTATION = "test-emulation";
  private static final Id HASTE = Id.minecraft("haste");
  private static final Id MINING_FATIGUE = Id.minecraft("mining_fatigue");
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id DOLPHINS_GRACE = Id.minecraft("dolphins_grace");
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");

  @TempDir
  Path dataFolder;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void breakSpeedBecomesHasteAndRemovingItTakesTheHasteAway() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "haste", Attributes.BLOCK_BREAK_SPEED, 0.4D, AttributeOperation.ADD_MULTIPLIED_TOTAL);

    assertThat(player.effect(HASTE)).isEqualTo(hidden(HASTE, 1));
    attributes().remove(player, ADAPTATION, "haste", Attributes.BLOCK_BREAK_SPEED);
    assertThat(player.hasEffect(HASTE)).isFalse();
  }

  @Test
  void slowerBreakingBecomesMiningFatigue() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "fatigue", Attributes.BLOCK_BREAK_SPEED, -0.7D, AttributeOperation.ADD_MULTIPLIED_TOTAL);

    assertThat(player.effect(MINING_FATIGUE)).isEqualTo(hidden(MINING_FATIGUE, 0));
    assertThat(player.hasEffect(HASTE)).isFalse();
  }

  @Test
  void submergedMiningSpeedStacksWithBreakSpeedIntoOneHaste() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "break", Attributes.BLOCK_BREAK_SPEED, 0.2D, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    attributes().apply(player, ADAPTATION, "submerged", Attributes.SUBMERGED_MINING_SPEED, 0.2D, AttributeOperation.ADD_VALUE);

    assertThat(player.effect(HASTE)).as("1.2 x 2.0 = 2.4 times faster").isEqualTo(hidden(HASTE, 6));
  }

  @Test
  void jumpStrengthBecomesJumpBoost() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "jump", Attributes.JUMP_STRENGTH, 0.2D, AttributeOperation.ADD_VALUE);

    assertThat(player.effect(JUMP_BOOST)).isEqualTo(hidden(JUMP_BOOST, 1));
  }

  @Test
  void safeFallAndFallMultiplierRescaleFallDamage() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());
    attributes().apply(player, ADAPTATION, "safe", Attributes.SAFE_FALL_DISTANCE, 5D, AttributeOperation.ADD_VALUE);
    attributes().apply(player, ADAPTATION, "soft", Attributes.FALL_DAMAGE_MULTIPLIER, -0.5D, AttributeOperation.ADD_MULTIPLIED_TOTAL);

    EntityDamageEvent hard = adapt.post(fall(player, 10D));
    EntityDamageEvent nearMiss = adapt.post(fall(player, 4D));
    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D, FinalDamage.IDENTITY));

    assertThat(hard.damage()).isEqualTo(2.5D);
    assertThat(hard.isCancelled()).isFalse();
    assertThat(nearMiss.isCancelled()).as("within the extra safe distance").isTrue();
    assertThat(hit.damage()).isEqualTo(4D);
  }

  @Test
  void oxygenBonusUndoesAirLossWithTheVanillaChance() {
    TestPlatform platform = platform(FakeGameContent.v1_20_1());
    List<Double> rolls = new ArrayList<>(List.of(0.4D, 0.6D));
    FakePlayer player = join(platform);
    AttributeEmulator emulator = new AttributeEmulator((Id attribute) -> false, () -> rolls.removeFirst());
    emulator.apply(player, Attributes.OXYGEN_BONUS, new AttributeMod(
        Id.of("adapt", "oxygen"), 1D, AttributeOperation.ADD_VALUE));

    platform.scheduler().advance(1);
    player.setRemainingAir(299);
    platform.scheduler().advance(1);
    assertThat(player.remainingAir()).as("roll 0.4 < 1/2 keeps the air").isEqualTo(300);
    player.setRemainingAir(299);
    platform.scheduler().advance(1);
    assertThat(player.remainingAir()).as("roll 0.6 loses it").isEqualTo(299);
  }

  @Test
  void sneakingSpeedAppliesSpeedOnlyWhileSneaking() {
    TestPlatform platform = platform(FakeGameContent.v1_20_1());
    FakePlayer player = join(platform);
    attributes().apply(player, ADAPTATION, "sneak", Attributes.SNEAKING_SPEED, 0.3D, AttributeOperation.ADD_VALUE);
    assertThat(player.hasEffect(SPEED)).isFalse();

    player.setSneaking(true);
    platform.scheduler().advance(2);
    assertThat(player.effect(SPEED)).as("0.6 / 0.3 = twice the sneak speed").isEqualTo(hidden(SPEED, 4));

    player.setSneaking(false);
    platform.scheduler().advance(2);
    assertThat(player.hasEffect(SPEED)).isFalse();
  }

  @Test
  void movementEfficiencyCancelsTheSoulSandSlowdownWithSpeed() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "soul", Attributes.MOVEMENT_EFFICIENCY, 1D, AttributeOperation.ADD_VALUE);

    assertThat(player.effect(SPEED)).as("2.5 times speed").isEqualTo(hidden(SPEED, 7));
  }

  @Test
  void waterMovementEfficiencyBecomesDolphinsGrace() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "swim", Attributes.WATER_MOVEMENT_EFFICIENCY, 0.5D, AttributeOperation.ADD_VALUE);

    assertThat(player.effect(DOLPHINS_GRACE)).isEqualTo(hidden(DOLPHINS_GRACE, 0));
  }

  @Test
  void reducedGravityBecomesSlowFallingAndIncreasedGravityHasNoStandIn() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "heavy", Attributes.GRAVITY, 0.5D, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    assertThat(player.hasEffect(SLOW_FALLING)).isFalse();
    attributes().apply(player, ADAPTATION, "heavy", Attributes.GRAVITY, -0.5D, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    assertThat(player.effect(SLOW_FALLING)).isEqualTo(hidden(SLOW_FALLING, 0));
  }

  @Test
  void maxAbsorptionIsTrackedForReadsWithoutAnEffect() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());

    attributes().apply(player, ADAPTATION, "bark", Attributes.MAX_ABSORPTION, 6D, AttributeOperation.ADD_VALUE);

    assertThat(attributes().value(player, Attributes.MAX_ABSORPTION)).isEqualTo(6D);
    assertThat(player.effects()).isEmpty();
  }

  @Test
  void anEffectThePlayerAlreadyHadIsNeitherReplacedNorRemoved() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());
    EffectInstance beacon = new EffectInstance(HASTE, 600, 2, true, true, true);
    player.addEffect(beacon);

    attributes().apply(player, ADAPTATION, "haste", Attributes.BLOCK_BREAK_SPEED, 0.2D, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    attributes().remove(player, ADAPTATION, "haste", Attributes.BLOCK_BREAK_SPEED);

    assertThat(player.effect(HASTE)).isEqualTo(beacon);
  }

  @Test
  void quittingRemovesEmulatedEffectsAndJoiningDropsLeftoversFromACrash() {
    FakePlayer player = startOn(FakeGameContent.v1_20_1());
    attributes().apply(player, ADAPTATION, "jump", Attributes.JUMP_STRENGTH, 0.1D, AttributeOperation.ADD_VALUE);
    adapt.post(new PlayerQuitEvent(player));
    assertThat(player.hasEffect(JUMP_BOOST)).isFalse();

    player.addEffect(hidden(HASTE, 3));
    adapt.post(new PlayerJoinEvent(player));
    assertThat(player.hasEffect(HASTE)).isFalse();
  }

  @Test
  void attributesTheServerHasAreNeverEmulated() {
    FakePlayer player = startOn(FakeGameContent.v26_3());
    player.addEffect(hidden(HASTE, 3));
    adapt.post(new PlayerJoinEvent(player));

    attributes().apply(player, ADAPTATION, "haste", Attributes.BLOCK_BREAK_SPEED, 0.4D, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    attributes().apply(player, ADAPTATION, "jump", Attributes.JUMP_STRENGTH, 0.2D, AttributeOperation.ADD_VALUE);

    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isEqualTo(1.4D);
    assertThat(player.hasEffect(JUMP_BOOST)).isFalse();
    assertThat(player.effect(HASTE)).as("join cleanup only runs where emulation is active").isEqualTo(hidden(HASTE, 3));
  }

  @Test
  void onlyPlayerAttributesThatHaveAStandInAreEmulated() {
    assertThat(AttributeEmulator.emulates(Attributes.SCALE)).isFalse();
    assertThat(AttributeEmulator.emulates(Attributes.STEP_HEIGHT)).isFalse();
    assertThat(AttributeEmulator.emulates(Attributes.ENTITY_INTERACTION_RANGE)).isFalse();
    assertThat(AttributeEmulator.emulates(Attributes.BLOCK_BREAK_SPEED)).isTrue();
  }

  private FakePlayer startOn(FakeGameContent game) {
    return join(platform(game));
  }

  private TestPlatform platform(FakeGameContent game) {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(game);
    adapt = TestAdapt.start(platform, List.of());
    return platform;
  }

  private FakePlayer join(TestPlatform platform) {
    FakeWorld world = platform.host().world("world", true);
    FakePlayer player = new FakePlayer("Tester", new Loc(world, 0, 64, 0));
    for (Id attribute : Attributes.ALL) {
      if (!platform.host().registries().exists(RegistryKind.ATTRIBUTE, attribute)) {
        player.withoutAttributes(attribute);
      }
    }
    platform.host().join(player);
    return player;
  }

  private static AdaptAttributeService attributes() {
    return AdaptAttributeService.get();
  }

  private static EntityDamageEvent fall(FakePlayer player, double damage) {
    return new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), damage, FinalDamage.IDENTITY);
  }

  private static EffectInstance hidden(Id type, int amplifier) {
    return new EffectInstance(type, EffectInstance.INFINITE, amplifier, true, false, false);
  }
}
