package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Harvest Moon lends Compost Cascade its level again; scraps of forced crops never roll a valuable. */
class HerbalismCompostCascadeLentTest {
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
  void aMutatedPlayerWithoutHarvestMoonCannotCompost() {
    mutate();
    BlockPos composter = composter("0");
    player.inventory().setItem(9, new FakeItem(Id.minecraft("wheat_seeds"), 32));
    player.setSneaking(true);

    click(composter);

    assertThat(player.inventory().item(9).amount()).isEqualTo(32);
  }

  @Test
  void harvestMoonRunsCompostCascadeFromASneakClickOnAComposter() {
    holder(1);
    BlockPos composter = composter("0");
    player.inventory().setItem(9, new FakeItem(Id.minecraft("wheat_seeds"), 32));
    player.setSneaking(true);

    click(composter);

    assertThat(player.inventory().item(9).amount()).isLessThan(32);
    assertThat(Integer.parseInt(platform.world().blockAt(1, 61, 1).state().property("level"))).isPositive();
  }

  @Test
  void compostFromForcedCropsRollsNoValuables() {
    holder(3);
    HerbalismCompostCascade cascade = compost();
    cascade.getConfig().valuableChanceBase = 1;
    cascade.getConfig().valuableChanceFactor = 0;
    cascade.getConfig().maxValuableChance = 1;
    cascade.getConfig().fillChanceBase = 1;
    cascade.getConfig().maxFillChance = 1;
    BlockPos composter = composter("7");
    crop(2, 2, 7);
    player.setSneaking(true);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      click(composter);
    }

    assertThat(platform.world().blockAt(1, 61, 1).state().property("level")).isEqualTo("8");
    assertThat(platform.world().items()).extracting((FakeItemEntity item) -> item.item().type())
        .doesNotContain(Id.minecraft("honeycomb"), Id.minecraft("glow_berries"), Id.minecraft("amethyst_shard"),
            Id.minecraft("emerald"), Id.minecraft("diamond"));
  }

  @Test
  void lentCompostFromTheInventoryOrDropsRollsNoValuables() {
    holder(3);
    HerbalismCompostCascade cascade = compost();
    cascade.getConfig().valuableChanceBase = 1;
    cascade.getConfig().valuableChanceFactor = 0;
    cascade.getConfig().maxValuableChance = 1;
    cascade.getConfig().fillChanceBase = 1;
    cascade.getConfig().maxFillChance = 1;
    BlockPos composter = composter("7");
    player.inventory().setItem(9, new FakeItem(Id.minecraft("wheat_seeds"), 32));
    platform.world().add(new FakeItemEntity(new Loc(platform.world(), 2.5D, 61D, 2.5D), new FakeItem(Id.minecraft("wheat_seeds"), 16)));
    player.setSneaking(true);

    click(composter);

    assertThat(platform.world().blockAt(1, 61, 1).state().property("level")).isEqualTo("8");
    assertThat(platform.world().items()).extracting((FakeItemEntity item) -> item.item().type())
        .doesNotContain(Id.minecraft("honeycomb"), Id.minecraft("glow_berries"), Id.minecraft("amethyst_shard"),
            Id.minecraft("emerald"), Id.minecraft("diamond"));
  }

  @Test
  void compostFromNaturalCropsStillRollsValuables() {
    holder(3);
    HerbalismCompostCascade cascade = compost();
    cascade.getConfig().valuableChanceBase = 1;
    cascade.getConfig().valuableChanceFactor = 0;
    cascade.getConfig().maxValuableChance = 1;
    cascade.getConfig().fillChanceBase = 1;
    cascade.getConfig().maxFillChance = 1;
    BlockPos composter = composter("7");
    crop(2, 2, 7);
    player.setSneaking(true);

    click(composter);

    assertThat(platform.world().items()).extracting((FakeItemEntity item) -> item.item().type())
        .containsAnyOf(Id.minecraft("honeycomb"), Id.minecraft("glow_berries"), Id.minecraft("amethyst_shard"),
            Id.minecraft("emerald"), Id.minecraft("diamond"));
  }

  private void holder(int level) {
    mutate();
    HarvestMoon moon = registry.getSkill("herbalism").getUpgrades().stream().filter(HarvestMoon.class::isInstance)
        .map(HarvestMoon.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("herbalism").setAdaptation(moon, level);
  }

  private HerbalismCompostCascade compost() {
    for (Adaptation<?> adaptation : registry.getSkill("herbalism").getAdaptations()) {
      if (adaptation instanceof HerbalismCompostCascade cascade) {
        return cascade;
      }
    }
    throw new AssertionError("no compost cascade");
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("herbalism", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("herbalism"), registry.getSkill("stealth"),
        "Warden")).isEqualTo(SkillMutations.Result.MUTATED);
  }

  private BlockPos composter(String level) {
    BlockPos at = new BlockPos(1, 61, 1);
    platform.world().set(at, FakeBlockState.of("composter").with("level", level));
    return at;
  }

  private void crop(int x, int z, int age) {
    platform.world().set(new BlockPos(x, 61, z), FakeBlockState.of("wheat").with("age", Integer.toString(age)));
  }

  private void click(BlockPos at) {
    BlockRef block = platform.world().blockAt(at.x(), at.y(), at.z());
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), block, BlockFace.UP, block.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));
  }
}
