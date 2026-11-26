package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/** Enchanting with Swords in both orders. */
class EnchantingBlocksRuntimeTest {
  private static final Id GLOWING = Id.minecraft("glowing");
  private static final Id LUCK = Id.minecraft("luck");
  private static final Id FORTUNE = Id.minecraft("fortune");
  private static final Id DIAMOND = Id.minecraft("diamond");

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
    player = platform.player("Runesmith", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillEnchanting.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcWhenAnItemIsEnchantedButNotForASmallPickup() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.GRAFT, 1);
    platform.world().set(3, 61, 4, "short_grass");

    adapt.post(new PlayerExpChangeEvent(player, 9));
    assertThat(platform.world().blockAt(3, 61, 4).type().isAir()).isFalse();

    platform.world().set(3, 61, 3, "enchanting_table");
    adapt.post(new EnchantItemEvent(player, platform.world().blockAt(3, 61, 3), FakeItem.of("book"), 0,
        Map.of(Id.minecraft("unbreaking"), 1), 1));
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(3, 61, 4).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("enchanting").work(),
        SoundPalettes.of("swords").work());
  }

  @Test
  void graftAlsoFiresOnTenXpAtOnce() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");

    adapt.post(new PlayerExpChangeEvent(player, 10));

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
  }

  @Test
  void xpPickedUpNearAFarmedMobsDeathDoesNotPayOut() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");
    FakeLiving spawned = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);

    adapt.post(death(spawned, 12));
    adapt.post(new PlayerExpChangeEvent(player, 12));

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
  }

  @Test
  void bigXpPickupsStartTheStrikeAtMostOncePerCooldown() throws Exception {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.GRAFT, 1);
    Field cooldown = AdaptConfig.Mutations.class.getDeclaredField("triggerCooldownMillisAtFirstLevel");
    cooldown.setAccessible(true);
    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    long previous = cooldown.getLong(config);
    cooldown.setLong(config, 0L);
    try {
      platform.world().set(0, 61, 2, "short_grass");

      adapt.post(new PlayerExpChangeEvent(player, 10));
      assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
      platform.world().set(0, 61, 2, "short_grass");
      adapt.post(new PlayerExpChangeEvent(player, 10));

      assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    } finally {
      cooldown.setLong(config, previous);
    }
  }

  @Test
  void echoMakesTheTargetGlowAndTheNextNaturalKillDropsHalfAgainAsMuchXp() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving spawned = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.hasEffect(GLOWING)).isTrue();
    assertThat(adapt.post(death(spawned, 10)).droppedExp()).isEqualTo(10);
    assertThat(adapt.post(death(zombie, 10)).droppedExp()).isEqualTo(15);
  }

  @Test
  void aSpawnerTargetIsNeitherMarkedNorPaid() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.hasEffect(GLOWING)).isFalse();
    FakeLiving natural = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);
    assertThat(adapt.post(death(natural, 10)).droppedExp()).isEqualTo(10);
  }

  @Test
  void barterSpendsSwordDurabilityForLuck() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effect(LUCK).durationTicks()).isEqualTo(160);
  }

  @Test
  void attunementNeedsTwentyLevels() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.ATTUNEMENT, 1);
    player.setLevel(19);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setLevel(20);
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergencePrimesFortuneOneLevelHigherForTheNextNaturalBreak() {
    kit.mutate("enchanting", "swords");
    kit.learn("enchanting", "swords", Recipe.CONVERGENCE, 1);
    platform.world().set(0, 61, 2, "short_grass");
    kit.hit(player, MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D), 1D, false);
    player.holding(FakeItem.tool("iron_pickaxe", 250).enchant("fortune", 2));

    adapt.post(new PlayerExpChangeEvent(player, 12));
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();

    List<ItemEntityRef> drops = breakOre(5, 61, 5);

    assertThat(drops).hasSize(1);
    assertThat(drops.get(0).item().amount()).isEqualTo(4);
    assertThat(breakOre(6, 61, 5).get(0).item().amount()).isEqualTo(3);
  }

  @Test
  void swordsLeadBarterSpendsALevelAndRefusesWithout() {
    kit.mutate("swords", "enchanting");
    kit.learn("swords", "enchanting", Recipe.BARTER, 1);
    player.setLevel(1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.level()).isZero();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    assertThat(swap().isCancelled()).isFalse();
  }

  @Test
  void swordsLeadAttunementGrantsLuckWhileFighting() {
    kit.mutate("swords", "enchanting");
    kit.learn("swords", "enchanting", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.hasEffect(LUCK)).isFalse();

    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 1D, false);
    tickEngine();

    assertThat(player.hasEffect(LUCK)).isTrue();
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(0, 61, 2, "short_grass");
    adapt.post(new PlayerExpChangeEvent(player, 50));
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    MutationAdaptation graft = kit.adaptation("enchanting", "swords", Recipe.GRAFT);
    profile.getData().getSkillLine("enchanting").setKnowledge(100);
    assertThat(AdaptationLearningTransaction.learn(graft, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);

    kit.mutate("enchanting", "swords");
    adapt.post(new PlayerExpChangeEvent(player, 50));

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    assertThat(graft.getLevel(player)).isZero();
  }

  /** Breaks a diamond ore whose drops grow with the tool's Fortune level, and returns what it dropped. */
  private List<ItemEntityRef> breakOre(int x, int y, int z) {
    platform.world().set(x, y, z, "diamond_ore");
    BlockRef ore = spy(platform.world().blockAt(x, y, z));
    doAnswer(invocation -> {
      ItemRef tool = invocation.getArgument(0);
      return List.of(new FakeItem(DIAMOND, 1 + tool.enchantmentLevel(FORTUNE)));
    }).when(ore).drops(any(), any());
    ItemRef held = player.inventory().mainHand();
    List<ItemEntityRef> vanilla = new ArrayList<>(List.of(new FakeItemEntity(ore.center(),
        new FakeItem(DIAMOND, 1 + held.enchantmentLevel(FORTUNE)))));
    adapt.post(new BlockBreakEvent(player, ore, true, 0));
    BlockDropItemEvent drop = adapt.post(new BlockDropItemEvent(player, ore, ore.state(), vanilla));
    return drop.items();
  }

  private EntityDeathEvent death(FakeLiving victim, int experience) {
    return new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), experience);
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
