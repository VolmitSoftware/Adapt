package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.engine.platform.EngineRecipeView;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
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

/** Crafting with Swords in both orders, and with Herbalism for the work Strike. */
class CraftingBlocksRuntimeTest {
  private static final Id IRON_NUGGET = Id.minecraft("iron_nugget");
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
    player = platform.player("Artisan", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250).withDamage(20));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillCrafting.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcWhenGearIsCraftedWithTheWorkCues() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");

    craft("stick");
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();

    craft("iron_pickaxe");
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("crafting")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void echoSharpensTheNextHitThroughArmorOnce() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = armored(10D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(1);
    EntityDamageByEntityEvent sharpened = kit.hit(player, zombie, 4D, false);
    EntityDamageByEntityEvent plain = kit.hit(player, zombie, 4D, false);

    assertThat(sharpened.damage()).isCloseTo(4D * 0.76D / 0.68D, within(1.0E-9D));
    assertThat(plain.damage()).isEqualTo(4D);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(20);
  }

  @Test
  void theSharpenedEdgeNeverLandsOnTheHitThatSetItOff() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.ECHO, 1);

    EntityDamageByEntityEvent trigger = kit.hit(player, armored(10D), 4D, true);

    assertThat(trigger.damage()).isEqualTo(4D);
  }

  @Test
  void armorPierceFollowsTheVanillaArmorFormula() {
    assertThat(CraftingFightStrike.pierced(4D, 0D, 0D, 0.4D)).isEqualTo(1D);
    assertThat(CraftingFightStrike.pierced(4D, 20D, 0D, 0.4D)).isCloseTo((1D - 10D / 25D) / (1D - 18D / 25D), within(1.0E-9D));
    assertThat(CraftingFightStrike.pierced(10D, 30D, 8D, 1D)).isCloseTo(1D / (1D - 20D / 25D), within(1.0E-9D));
  }

  @Test
  void barterSpendsSwordDurabilityToRepairEveryArmorPiece() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.BARTER, 1);
    player.setEquipment(EquipmentSlot.HEAD, FakeItem.tool("iron_helmet", 165).withDamage(10));
    player.setEquipment(EquipmentSlot.FEET, FakeItem.tool("iron_boots", 195).withDamage(1));
    player.setSneaking(true);

    doubleSneak();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(25);
    assertThat(player.equipment(EquipmentSlot.HEAD).damage()).isEqualTo(4);
    assertThat(player.equipment(EquipmentSlot.FEET).damage()).isZero();
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAfterACraft() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    craft("stick");
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceSpeedsTheHeldToolAndCutsAfterASwordHit() {
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.CONVERGENCE, 1);
    platform.world().set(0, 61, 2, "short_grass");
    player.holding(FakeItem.tool("iron_pickaxe", 250));

    craft("iron_pickaxe");
    platform.scheduler().advance(1);
    assertThat(player.attributeValue(Attributes.MINING_EFFICIENCY)).isEqualTo(1D);

    player.holding(FakeItem.tool("iron_sword", 250));
    kit.hit(player, MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D), 1D, false);
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    craft("iron_pickaxe");
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.MINING_EFFICIENCY)).isEqualTo(3D);
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
  }

  @Test
  void swordsLeadBarterSpendsAnIronNuggetAndRefusesWithout() {
    kit.mutate("swords", "crafting");
    kit.learn("swords", "crafting", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(IRON_NUGGET, 1));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).as("sneak and swap hands is Unmaker's").isFalse();
    assertThat(player.inventory().contains(IRON_NUGGET, 1)).isTrue();

    doubleSneak();
    assertThat(player.inventory().contains(IRON_NUGGET, 1)).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    platform.scheduler().advance(1_200);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);
    doubleSneak();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);
  }

  @Test
  void swordsLeadAttunementRepairsTheHeldSwordWhileFighting() {
    kit.mutate("swords", "crafting");
    kit.learn("swords", "crafting", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(20);

    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 1D, false);
    tickEngine();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(14);
  }

  @Test
  void graftGivesTheHeldToolOneMoreEfficiencyLevelForAWhile() {
    player.holding(FakeItem.tool("iron_pickaxe", 250).enchant("efficiency", 2));
    kit.mutate("herbalism", "crafting");
    kit.learn("herbalism", "crafting", Recipe.GRAFT, 1);

    harvest();
    platform.scheduler().advance(1);
    assertThat(player.attributeValue(Attributes.MINING_EFFICIENCY)).isEqualTo(6D);
    assertThat(player.inventory().mainHand().enchantmentLevel(Id.minecraft("efficiency"))).isEqualTo(2);

    platform.scheduler().advance(400);
    assertThat(player.attributeValue(Attributes.MINING_EFFICIENCY)).isEqualTo(1D);
  }

  @Test
  void withoutTheMiningEfficiencyAttributeTheGraftSpeedsBreakingInstead() {
    player.withoutAttributes(Attributes.MINING_EFFICIENCY);
    player.holding(FakeItem.tool("iron_shovel", 250));
    kit.mutate("herbalism", "crafting");
    kit.learn("herbalism", "crafting", Recipe.GRAFT, 1);

    harvest();
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isCloseTo(1.25D, within(1.0E-9D));
  }

  @Test
  void anEmptyHandOrASwordGetsNoEfficiency() {
    player.inventory().setMainHand(FakeItem.empty());
    kit.mutate("herbalism", "crafting");
    kit.learn("herbalism", "crafting", Recipe.GRAFT, 1);

    harvest();
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.MINING_EFFICIENCY)).isEqualTo(1D);
  }

  @Test
  void gearCraftedFromPartsCraftedMomentsAgoDoesNotSetTheTriggerOff() {
    platform.recipes().serverRecipe(new EngineRecipeView(Id.minecraft("wooden_pickaxe"), RecipeView.Kind.SHAPED,
        List.of("PPP", " S ", " S "), Map.of('P', RecipeChoice.ofTypes(List.of(Id.minecraft("oak_planks"))),
        'S', RecipeChoice.ofTypes(List.of(Id.minecraft("stick")))), List.of(), FakeItem.of("wooden_pickaxe")));
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");

    craft("stick");
    craft("wooden_pickaxe");
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
  }

  @Test
  void partsMadeAwayFromATableAlsoCountAsFresh() {
    platform.recipes().serverRecipe(new EngineRecipeView(Id.minecraft("wooden_pickaxe"), RecipeView.Kind.SHAPED,
        List.of("PPP", " S ", " S "), Map.of('P', RecipeChoice.ofTypes(List.of(Id.minecraft("oak_planks"))),
        'S', RecipeChoice.ofTypes(List.of(Id.minecraft("stick")))), List.of(), FakeItem.of("wooden_pickaxe")));
    kit.mutate("crafting", "swords");
    kit.learn("crafting", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 2, "short_grass");
    CraftingTrigger trigger = SkillHost.of(adapt.core().getAdaptServer().getSkillRegistry().getSkill("crafting")).getBlocks().stream()
        .filter(CraftingTrigger.class::isInstance).map(CraftingTrigger.class::cast).findFirst().orElseThrow();

    trigger.crafted(player, Id.minecraft("stick"));
    craft("wooden_pickaxe");
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(0, 61, 2, "short_grass");
    craft("iron_pickaxe");
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    MutationAdaptation graft = kit.adaptation("crafting", "swords", Recipe.GRAFT);
    profile.getData().getSkillLine("crafting").setKnowledge(100);
    assertThat(AdaptationLearningTransaction.learn(graft, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);

    kit.mutate("crafting", "swords");
    craft("iron_pickaxe");

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isFalse();
    assertThat(graft.getLevel(player)).isZero();
  }

  private FakeLiving armored(double armor) {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    zombie.setAttributeBase(Attributes.ARMOR, armor);
    zombie.setAttributeBase(Attributes.ARMOR_TOUGHNESS, 0D);
    return zombie;
  }

  private void harvest() {
    platform.world().set(new BlockPos(-1, 61, 2), new FakeBlockState(WHEAT, Map.of("age", "7")));
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(-1, 61, 2), true, 0));
  }

  private void craft(String item) {
    adapt.post(new ItemCraftedEvent(player, FakeItem.of(item), Id.minecraft(item)));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void doubleSneak() {
    adapt.post(new PlayerToggleSneakEvent(player, false));
    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, false));
    adapt.post(new PlayerToggleSneakEvent(player, true));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
