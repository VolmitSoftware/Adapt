package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.item.ToolMatch;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.content.adaptation.axe.AxeDropToInventory;
import art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Pickaxes, Axes and Excavation recognising modded tools and blocks through the shared tool rules. */
class ToolDetectionRuntimeTest {
  private static final Id MARBLE = Id.parse("quarry:marble");
  private static final Id IRONWOOD = Id.parse("quarry:ironwood");
  private static final Id PEAT = Id.parse("quarry:peat");
  private static final Id OSMIUM_PICKAXE = Id.parse("mekanismtools:osmium_pickaxe");
  private static final Id BRONZE_PICKAXE = Id.parse("mekanismtools:bronze_pickaxe");
  private static final Id OSMIUM_AXE = Id.parse("mekanismtools:osmium_axe");
  private static final Id OSMIUM_SHOVEL = Id.parse("mekanismtools:osmium_shovel");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
  }

  @Test
  void aTaggedModdedToolEarnsEachSkillsXp() {
    start();

    assertThat(xpFor(tool(OSMIUM_PICKAXE), MARBLE, "pickaxe")).isPositive();
    assertThat(xpFor(tool(OSMIUM_AXE), IRONWOOD, "axes")).isPositive();
    assertThat(xpFor(tool(OSMIUM_SHOVEL), PEAT, "excavation")).isPositive();
    assertThat(Tools.match(tool(OSMIUM_PICKAXE), ToolKind.PICKAXE)).isEqualTo(ToolMatch.TAG);
  }

  @Test
  void aCapabilityOnlyPaxelFeedsTheSkillWhoseBlockItBreaks() {
    start();
    FakeItem paxel = tool(Id.parse("mekanismtools:osmium_paxel")).withToolActions(ToolKind.PICKAXE, ToolKind.AXE,
        ToolKind.SHOVEL);

    assertThat(Tools.match(paxel, ToolKind.AXE)).isEqualTo(ToolMatch.CAPABILITY);
    assertThat(xpFor(paxel, MARBLE, "pickaxe")).isPositive();
    assertThat(xp("excavation")).isZero();
    assertThat(xp("axes")).isZero();
    assertThat(xpFor(paxel, IRONWOOD, "axes")).isPositive();
    assertThat(xpFor(paxel, PEAT, "excavation")).isPositive();
  }

  @Test
  void aBehaviourOnlyToolCountsBecauseItMinesTheReferenceBlock() {
    start();
    FakeItem drill = tool(Id.parse("drills:drill")).correctFor(Id.minecraft("stone"), MARBLE);
    FakeItem chainsaw = tool(Id.parse("drills:chainsaw")).correctFor(Id.minecraft("oak_log"), IRONWOOD);
    FakeItem spade = tool(Id.parse("drills:spade")).correctFor(Id.minecraft("dirt"), PEAT);

    assertThat(Tools.match(drill, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BEHAVIOUR);
    assertThat(xpFor(drill, MARBLE, "pickaxe")).isPositive();
    assertThat(xpFor(chainsaw, IRONWOOD, "axes")).isPositive();
    assertThat(xpFor(spade, PEAT, "excavation")).isPositive();
  }

  @Test
  void operatorListedToolsCountAndExcludedOnesDoNot() throws IOException {
    config("pickaxe", "extraTools = [\"minecraft:bone\"]\nexcludedTools = [\"mekanismtools:bronze_pickaxe\"]\n");
    config("axes", "extraTools = [\"#quarry:cleavers\"]\n");
    config("excavation", "extraTools = [\"minecraft:blaze_rod\"]\nexcludedTools = [\"minecraft:iron_shovel\"]\n");
    start();
    platform.host().tagItems(Id.parse("quarry:cleavers"), Id.minecraft("stick"));
    Tools.invalidate();

    assertThat(xpFor(tool(Id.minecraft("bone")), MARBLE, "pickaxe")).isPositive();
    assertThat(xpFor(tool(Id.minecraft("stick")), IRONWOOD, "axes")).isPositive();
    assertThat(xpFor(tool(Id.minecraft("blaze_rod")), PEAT, "excavation")).isPositive();
    double pickaxe = xp("pickaxe");
    double excavation = xp("excavation");
    assertThat(xpFor(tool(BRONZE_PICKAXE), MARBLE, "pickaxe")).isEqualTo(pickaxe);
    assertThat(xpFor(FakeItem.tool("iron_shovel", 250), Id.minecraft("dirt"), "excavation")).isEqualTo(excavation);
    assertThat(ItemKinds.isPickaxe(tool(BRONZE_PICKAXE))).isFalse();
  }

  @Test
  void excludedBlocksAndListedBlockXpComeFromTheConfig() throws IOException {
    config("pickaxe", "excludedBlocks = [\"quarry:marble\"]\n[extraBlocks]\n\"quarry:slate\" = 40.0\n");
    start();
    Id slate = Id.parse("quarry:slate");

    assertThat(xpFor(tool(OSMIUM_PICKAXE), MARBLE, "pickaxe")).isZero();
    assertThat(xpFor(tool(OSMIUM_PICKAXE), slate, "pickaxe")).isEqualTo(40D);
  }

  @Test
  void anUnknownMineableBlockEarnsDerivedXpAndVanillaValuesStayAsTheyWere() {
    start();
    double marble = xpFor(tool(OSMIUM_PICKAXE), MARBLE, "pickaxe");
    SkillPickaxes pickaxes = (SkillPickaxes) skill("pickaxe");

    assertThat(marble).isPositive();
    assertThat(marble).isEqualTo(Math.round(pickaxes.blockValue(MARBLE)));
    double before = xp("pickaxe");
    assertThat(xpFor(FakeItem.tool("iron_pickaxe", 250), Id.minecraft("diamond_ore"), "pickaxe") - before)
        .isEqualTo(Math.round(pickaxes.blockValue(Id.minecraft("diamond_ore"))));
  }

  @Test
  void aBrokenModdedToolEarnsNothingAndFiresNoAdaptation() {
    start();
    learn("pickaxe", PickaxeDropToInventory.class);
    FakeItem broken = tool(OSMIUM_PICKAXE).withDamage(250);

    assertThat(Tools.match(broken, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BROKEN);
    assertThat(xpFor(broken, MARBLE, "pickaxe")).isZero();
    assertThat(caught(broken, MARBLE, "cobblestone")).isFalse();
    assertThat(xpFor(tool(OSMIUM_PICKAXE).withDamage(249), MARBLE, "pickaxe")).isPositive();
  }

  @Test
  void wrongToolsAndBareHandsEarnNothing() {
    start();

    assertThat(xpFor(FakeItem.of("stick"), MARBLE, "pickaxe")).isZero();
    assertThat(xpFor(FakeItem.empty(), MARBLE, "pickaxe")).isZero();
    assertThat(xpFor(tool(OSMIUM_PICKAXE), IRONWOOD, "axes")).isZero();
    assertThat(xpFor(FakeItem.of("stick"), PEAT, "excavation")).isZero();
  }

  @Test
  void placedBlocksStillPayNothingAndSilkTouchStaysFlat() {
    start();
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      assertThat(xpFor(tool(OSMIUM_PICKAXE), MARBLE, "pickaxe")).isZero();
      assertThat(xpFor(tool(OSMIUM_SHOVEL), PEAT, "excavation")).isZero();
    }
    assertThat(xpFor(tool(OSMIUM_PICKAXE).enchant("silk_touch", 1), MARBLE, "pickaxe")).isEqualTo(5D);
  }

  @Test
  void oneDropToInventoryAdaptationPerSkillFiresForModdedTools() {
    start();
    FakeItem paxel = tool(Id.parse("mekanismtools:osmium_paxel")).withToolActions(ToolKind.PICKAXE, ToolKind.AXE,
        ToolKind.SHOVEL);
    learn("pickaxe", PickaxeDropToInventory.class);
    learn("axes", AxeDropToInventory.class);
    learn("excavation", ExcavationDropToInventory.class);

    assertThat(caught(tool(OSMIUM_PICKAXE), MARBLE, "cobblestone")).as("tagged pickaxe").isTrue();
    assertThat(caught(tool(Id.parse("drills:chainsaw")).correctFor(Id.minecraft("oak_log")), IRONWOOD, "oak_log"))
        .as("behaviour-only axe").isTrue();
    assertThat(caught(paxel, PEAT, "dirt")).as("capability-only paxel").isTrue();
    assertThat(caught(FakeItem.of("stick"), MARBLE, "cobblestone")).as("stick").isFalse();
  }

  @Test
  void anExcludedToolFiresNoAdaptation() throws IOException {
    config("pickaxe", "excludedTools = [\"#forge:tools/pickaxes\"]\n");
    start();
    learn("pickaxe", PickaxeDropToInventory.class);

    assertThat(caught(tool(OSMIUM_PICKAXE), MARBLE, "cobblestone")).isFalse();
  }

  private void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().tagItems(Id.parse("forge:tools/pickaxes"), OSMIUM_PICKAXE, BRONZE_PICKAXE);
    platform.host().tagItems(Id.minecraft("axes"), OSMIUM_AXE);
    platform.host().tagItems(Id.parse("c:shovels"), OSMIUM_SHOVEL);
    platform.host().tagBlocks(Id.minecraft("mineable/pickaxe"), Id.minecraft("stone"), MARBLE,
        Id.minecraft("diamond_ore"));
    platform.host().tagBlocks(Id.minecraft("mineable/axe"), Id.minecraft("oak_log"), IRONWOOD);
    platform.host().tagBlocks(Id.minecraft("logs"), Id.minecraft("oak_log"), IRONWOOD);
    platform.host().tagBlocks(Id.minecraft("mineable/shovel"), Id.minecraft("dirt"), PEAT);
    platform.items().hardness(MARBLE, 1.5F, 6F).hardness(IRONWOOD, 2F, 2F).hardness(PEAT, 0.5F, 0.5F)
        .hardness("diamond_ore", 3F, 3F);
    player = platform.player("Miner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class, SkillAxes.class, SkillExcavation.class));
    profile = adapt.join(player);
  }

  private void config(String skill, String toml) throws IOException {
    Path file = data.resolve("skills").resolve("adapt").resolve(skill + ".toml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, toml);
  }

  private static FakeItem tool(Id type) {
    return FakeItem.tool(type, 250);
  }

  private double xpFor(ItemRef held, Id block, String skill) {
    player.holding(held);
    platform.world().set(new BlockPos(3, 60, 0), new FakeBlockState(block));
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(3, 60, 0), true, 0));
    return xp(skill);
  }

  /** XP the skill's sources asked to pay, before freshness and pooling. */
  private double xp(String skill) {
    String prefix = "adapt:" + skill + "/";
    double requested = 0D;
    for (XpEarnings.SourceSnapshot source : profile.getData().getXpEarnings().snapshot()) {
      if (source.source().startsWith(prefix)) {
        requested += source.requested();
      }
    }
    return requested;
  }

  private Skill<?> skill(String name) {
    return AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(name);
  }

  private void learn(String skillName, Class<? extends Adaptation<?>> type) {
    for (Adaptation<?> adaptation : skill(skillName).getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine(skillName).setAdaptation(adaptation, 1);
        return;
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private boolean caught(ItemRef held, Id block, String dropped) {
    player.holding(held);
    platform.world().set(new BlockPos(0, 60, 0), new FakeBlockState(block));
    BlockRef at = platform.world().blockAt(0, 60, 0);
    List<ItemEntityRef> entities = new ArrayList<>();
    entities.add(platform.world().createItemEntity(at.center().position(), FakeItem.of(dropped)));
    BlockDropItemEvent event = adapt.post(new BlockDropItemEvent(player, at, new FakeBlockState(block), entities));
    return event.items().isEmpty();
  }
}
