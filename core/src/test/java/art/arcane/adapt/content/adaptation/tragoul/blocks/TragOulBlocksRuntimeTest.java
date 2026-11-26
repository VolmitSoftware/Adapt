package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.FoodLevelChangeEvent;
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

/** TragOul with Swords in both orders, and with Herbalism for the work Strike. */
class TragOulBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");

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
    player = platform.player("Bloodbinder", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillTragOul.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadBleedsTheAttackerOfAHeavyHitWithBothCues() {
    kit.mutate("tragoul", "swords");
    kit.learn("tragoul", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    struck(zombie, 3.5D);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    struck(zombie, 4D);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("tragoul").fight(),
        SoundPalettes.of("swords").fight());
  }

  @Test
  void echoAsPartnerBleedsTheAttackerOfAHeavyHit() {
    kit.mutate("swords", "tragoul");
    kit.learn("swords", "tragoul", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    struck(zombie, 6D);
    platform.scheduler().advance(10);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aSwordCritDrainsOneHeartIntoThePlayer() {
    kit.mutate("swords", "tragoul");
    kit.learn("swords", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);

    assertThat(zombie.health()).isEqualTo(18D);
    assertThat(player.health()).isEqualTo(12D);
  }

  @Test
  void aSpawnerMobIsNotDrained() {
    kit.mutate("swords", "tragoul");
    kit.learn("swords", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);

    assertThat(zombie.health()).isEqualTo(20D);
    assertThat(player.health()).isEqualTo(10D);
  }

  @Test
  void harvestingTurnsAHungerPointIntoHalfAHeart() {
    kit.mutate("herbalism", "tragoul");
    kit.learn("herbalism", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    player.setFoodLevel(10);

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    assertThat(player.foodLevel()).isEqualTo(9);
    assertThat(player.health()).isEqualTo(11D);
  }

  @Test
  void hungerBoughtWithHealthCannotBeTurnedBackIntoHealth() {
    kit.mutate("herbalism", "tragoul");
    kit.learn("herbalism", "tragoul", Recipe.BARTER, 1);
    kit.learn("herbalism", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    player.setFoodLevel(0);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.health()).isEqualTo(8D);
    assertThat(player.foodLevel()).isEqualTo(2);
    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    assertThat(player.foodLevel()).isEqualTo(2);
    assertThat(player.health()).isEqualTo(8D);
  }

  @Test
  void eatenFoodStillTurnsIntoHealthAfterBoonFoodIsSpent() {
    kit.mutate("herbalism", "tragoul");
    kit.learn("herbalism", "tragoul", Recipe.BARTER, 1);
    kit.learn("herbalism", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    player.setFoodLevel(0);
    player.setSneaking(true);
    swap();

    adapt.post(new FoodLevelChangeEvent(player, 0, null));
    player.setFoodLevel(6);
    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    assertThat(player.foodLevel()).isEqualTo(5);
    assertThat(player.health()).isEqualTo(9D);
  }

  @Test
  void barterHealsAHeartAndAHalfForSwordDurability() {
    kit.mutate("tragoul", "swords");
    kit.learn("tragoul", "swords", Recipe.BARTER, 1);
    player.setHealth(10D);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.health()).isEqualTo(13D);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
  }

  @Test
  void barterSpendsAHeartForAttackSpeedButNeverTheLastHalfHeart() {
    kit.mutate("swords", "tragoul");
    kit.learn("swords", "tragoul", Recipe.BARTER, 1);
    player.setHealth(10D);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.health()).isEqualTo(8D);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    player.setHealth(2.5D);
    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.health()).isEqualTo(2.5D);
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAtThreeHeartsOrLess() {
    kit.mutate("tragoul", "swords");
    kit.learn("tragoul", "swords", Recipe.ATTUNEMENT, 1);
    player.setHealth(7D);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setHealth(6D);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    struck(zombie, 8D);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    kit.mutate("tragoul", "swords");
    struck(zombie, 8D);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
  }

  private void struck(FakeLiving attacker, double damage) {
    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), damage, false, FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
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
