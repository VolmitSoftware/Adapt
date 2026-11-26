package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class HerbalismUpgradesRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Warden", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHerbalism.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillOffersAllThreeUpgrades() {
    mutate();
    MutatedSkill mutated = registry.getMutatedSkill("mutated-herbalism-stealth");

    assertThat(mutated.getAdaptations()).extracting(Adaptation::getName).startsWith(
        "upgraded-herbalism-wildgrowth", "upgraded-herbalism-harvest-moon", "upgraded-herbalism-iron-stomach",
        "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
  }

  @Test
  void ironStomachDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("herbalism").setAdaptation(upgrade(IronStomach.class), 3);
    player.setFoodLevel(20);
    player.setSaturation(10F);

    assertThat(damage(DamageCause.MAGIC, 10D).damage()).isEqualTo(10D);
  }

  @Test
  void ironStomachPaysAnyDamageTypeFromHunger() {
    mutate();
    IronStomach stomach = learn(IronStomach.class, 1);
    player.setFoodLevel(20);
    player.setSaturation(10F);

    double magic = damage(DamageCause.MAGIC, 10D).damage();
    double explosion = damage(DamageCause.BLOCK_EXPLOSION, 10D).damage();

    assertThat(magic).isEqualTo(10D * (1D - stomach.getEffectiveness(1)));
    assertThat(explosion).isEqualTo(10D * (1D - stomach.getEffectiveness(1)));
    assertThat(player.foodLevel() + player.saturation()).isLessThan(30F);
  }

  @Test
  void ironStomachAddsEachMealAgain() {
    mutate();
    learn(IronStomach.class, 1);
    player.setFoodLevel(6);

    adapt.post(new PlayerItemConsumeEvent(player, FakeItem.of("bread"), Hand.MAIN_HAND, null));
    player.setFoodLevel(11);
    platform.scheduler().advance(1);

    assertThat(player.foodLevel()).isEqualTo(16);
  }

  @Test
  void harvestMoonHarvestsTheWholeFieldFromOneClick() {
    mutate();
    HarvestMoon moon = learn(HarvestMoon.class, 1);
    player.holding(FakeItem.tool("iron_hoe", 250));
    for (int x = 0; x < 5; x++) {
      crop(x, 3, 7);
    }
    crop(0, 4, 3);
    crop(20, 3, 7);

    click(platform.world().blockAt(0, 61, 3));

    for (int x = 0; x < 5; x++) {
      assertThat(platform.world().blockAt(x, 61, 3).type().isAir()).isTrue();
    }
    assertThat(platform.world().blockAt(0, 61, 4).type()).isEqualTo(WHEAT);
    assertThat(platform.world().blockAt(20, 61, 3).type()).isEqualTo(WHEAT);
    assertThat(moon.getFieldRadius(1)).isLessThan(20D);
    assertThat(player.inventory().contains(WHEAT, 1)).isTrue();
  }

  @Test
  void harvestMoonDropsLuckyExtras() {
    mutate();
    HarvestMoon moon = learn(HarvestMoon.class, 1);
    moon.getConfig().bonusChanceBase = 100;
    player.holding(FakeItem.tool("iron_hoe", 250));
    crop(1, 1, 7);

    click(platform.world().blockAt(1, 61, 1));

    assertThat(platform.world().items()).extracting((item) -> item.item().type())
        .anyMatch((Id type) -> !type.equals(WHEAT));
  }

  @Test
  void harvestMoonRollsNoLuckOnBoneMealedCrops() {
    mutate();
    HarvestMoon moon = learn(HarvestMoon.class, 1);
    moon.getConfig().bonusChanceBase = 100;
    player.holding(FakeItem.tool("iron_hoe", 250));
    crop(1, 1, 7);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      click(platform.world().blockAt(1, 61, 1));
    }

    assertThat(platform.world().items()).extracting((item) -> item.item().type())
        .doesNotContain(Id.minecraft("potato"), Id.minecraft("carrot"), Id.minecraft("beetroot"), Id.minecraft("apple"));
  }

  @Test
  void harvestMoonPaysNoCropXpForBoneMealedCrops() {
    mutate();
    HarvestMoon moon = learn(HarvestMoon.class, 1);
    player.holding(FakeItem.tool("iron_hoe", 250));
    crop(1, 1, 7);
    double before = profile.getData().getSkillLine("herbalism").getXp();

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      click(platform.world().blockAt(1, 61, 1));
    }

    assertThat(platform.world().blockAt(1, 61, 1).type().isAir()).isTrue();
    assertThat(profile.getData().getSkillLine("herbalism").getXp()).isEqualTo(before);
    assertThat(XpPaid.by(profile, moon)).isZero();
  }

  @Test
  void harvestMoonPaysHarvestXpForNaturalCrops() {
    mutate();
    HarvestMoon moon = learn(HarvestMoon.class, 1);
    player.holding(FakeItem.tool("iron_hoe", 250));
    crop(1, 1, 7);

    click(platform.world().blockAt(1, 61, 1));

    assertThat(XpPaid.by(profile, moon)).isPositive();
  }

  @Test
  void wildgrowthMarksTheCropsItGrowsAsBoneMealed() throws InterruptedException {
    mutate();
    Wildgrowth wildgrowth = learn(Wildgrowth.class, 1);
    wildgrowth.getConfig().radiusBase = 0;
    wildgrowth.getConfig().radiusPerLevel = 0;
    wildgrowth.getConfig().surfaceOnly = false;
    player.setFoodLevel(20);
    platform.world().set(0, 60, 0, "farmland");
    crop(0, 0, 2);
    BlockRef crop = platform.world().blockAt(0, 61, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      long deadline = System.currentTimeMillis() + 5_000L;
      while (crop.state().age() == 2 && System.currentTimeMillis() < deadline) {
        wildgrowth.onTick();
        platform.scheduler().advance(1);
        Thread.sleep(10L);
      }

      provenance.verify(() -> XpProvenance.recordBonemeal(argThat((BlockRef grown) -> grown.pos().equals(crop.pos()))));
    }
    assertThat(crop.state().age()).isGreaterThan(2);
  }

  @Test
  void wildgrowthPlantsHotbarSeedsOnEmptyFarmland() {
    mutate();
    learn(Wildgrowth.class, 1);
    player.inventory().setItem(0, new FakeItem(Id.minecraft("wheat_seeds"), 16));
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        platform.world().set(x, 60, z, "farmland");
      }
    }

    Wildgrowth wildgrowth = upgrade(Wildgrowth.class);
    for (int tick = 0; tick < 4; tick++) {
      wildgrowth.onTick();
      platform.scheduler().advance(1);
    }

    assertThat(platform.world().blockAt(0, 61, 0).type()).isEqualTo(WHEAT);
    assertThat(player.inventory().item(0).amount()).isLessThan(16);
  }

  @Test
  void wildgrowthGrowsOneMoreStagePerLevel() {
    Wildgrowth wildgrowth = upgrade(Wildgrowth.class);

    for (int level = 1; level <= wildgrowth.getMaxLevel(); level++) {
      assertThat((int) wildgrowth.getStrength(level)).isEqualTo(level);
    }
  }

  @Test
  void wildgrowthSkipsSeedsWithNothingToPlantOn() {
    mutate();
    learn(Wildgrowth.class, 1);
    player.inventory().setItem(0, new FakeItem(Id.minecraft("nether_wart"), 16));
    player.inventory().setItem(1, new FakeItem(Id.minecraft("wheat_seeds"), 16));
    platform.world().set(1, 60, 0, "farmland");

    Wildgrowth wildgrowth = upgrade(Wildgrowth.class);
    for (int tick = 0; tick < 4; tick++) {
      wildgrowth.onTick();
      platform.scheduler().advance(1);
    }

    assertThat(platform.world().blockAt(1, 61, 0).type()).isEqualTo(WHEAT);
    assertThat(player.inventory().item(0).amount()).isEqualTo(16);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("herbalism", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("herbalism"), registry.getSkill("stealth"), "Warden"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("herbalism").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("herbalism").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private EntityDamageEvent damage(DamageCause cause, double amount) {
    return adapt.post(new EntityDamageEvent(player, cause, MAGIC, amount, FinalDamage.IDENTITY));
  }

  private void crop(int x, int z, int age) {
    platform.world().set(new BlockPos(x, 61, z), FakeBlockState.of("wheat").with("age", Integer.toString(age)));
  }

  private void click(BlockRef block) {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), block, BlockFace.UP, block.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));
  }
}
