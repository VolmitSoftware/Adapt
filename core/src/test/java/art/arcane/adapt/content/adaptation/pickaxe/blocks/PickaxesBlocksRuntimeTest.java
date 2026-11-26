package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
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
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Pickaxes with Swords and Herbalism: each of the six blocks through the recipes that use it. */
class PickaxesBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id GRASS = Id.minecraft("short_grass");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;
  private RecordingDisplays displays;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Delver", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillPickaxes.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    displays = new RecordingDisplays();
    AdaptPorts.install(ViewerDisplayPort.class, displays);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcWhenAnOreIsMinedAsLeadWithBothWorkCues() {
    kit.mutate("pickaxe", "swords");
    kit.learn("pickaxe", "swords", Recipe.GRAFT, 1);
    platform.world().set(3, 61, 1, "short_grass");

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "stone").blockAt(3, 60, 0), true, 0));
    assertThat(platform.world().blockAt(3, 61, 1).type()).isEqualTo(GRASS);

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "iron_ore").blockAt(3, 60, 0), true, 0));
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(3, 61, 1).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("pickaxe").work(),
        SoundPalettes.of("swords").work());
  }

  @Test
  void echoCutsTheArcWhenAnOreIsMinedAsPartner() {
    kit.mutate("swords", "pickaxe");
    kit.learn("swords", "pickaxe", Recipe.ECHO, 1);
    platform.world().set(3, 61, 1, "short_grass");

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "deepslate_gold_ore").blockAt(3, 60, 0),
        true, 0));

    assertThat(platform.world().blockAt(3, 61, 1).type().isAir()).isTrue();
  }

  @Test
  void aPlacedOreDoesNotPayOut() {
    kit.mutate("pickaxe", "swords");
    kit.learn("pickaxe", "swords", Recipe.GRAFT, 1);
    platform.world().set(3, 61, 1, "short_grass");
    BlockRef ore = platform.world().set(3, 60, 0, "iron_ore").blockAt(3, 60, 0);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, ore, true, 0));
    }

    assertThat(platform.world().blockAt(3, 61, 1).type()).isEqualTo(GRASS);
  }

  @Test
  void echoStunsTheTargetOfASwordCrit() {
    kit.mutate("pickaxe", "swords");
    kit.learn("pickaxe", "swords", Recipe.ECHO, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
  }

  @Test
  void graftLightsNearbyOresWhenACropIsHarvested() {
    kit.mutate("herbalism", "pickaxe");
    kit.learn("herbalism", "pickaxe", Recipe.GRAFT, 1);
    platform.world().set(2, 61, 2, "diamond_ore").set(0, 61, 9, "coal_ore").set(1, 60, 2, "stone");

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(displays.shown).containsExactly("ore-2:61:2");
  }

  @Test
  void barterSpendsSwordDurabilityForMiningSpeed() {
    kit.mutate("pickaxe", "swords");
    kit.learn("pickaxe", "swords", Recipe.BARTER, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = swap();

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isCloseTo(1.4D, within(1.0E-9D));
  }

  @Test
  void barterSpendsPickaxeDurabilityAndRefusesWhenThePickaxeIsWorn() {
    kit.mutate("swords", "pickaxe");
    kit.learn("swords", "pickaxe", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    FakeItem worn = FakeItem.tool("iron_pickaxe", 250);
    worn.setDamage(247);
    player.holding(worn);
    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(247);
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAfterMining() {
    kit.mutate("pickaxe", "swords");
    kit.learn("pickaxe", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "stone").blockAt(3, 60, 0), true, 0));
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceGrowsAndLightsOresOnlyRightAfterMining() {
    kit.mutate("herbalism", "pickaxe");
    kit.learn("herbalism", "pickaxe", Recipe.CONVERGENCE, 1);
    BlockRef young = crop(0, 61, 4, 3);
    platform.world().set(2, 61, 2, "iron_ore");

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    assertThat(young.state().age()).isEqualTo(3);
    assertThat(displays.shown).isEmpty();

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "stone").blockAt(3, 60, 0), true, 0));
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));

    assertThat(young.state().age()).isEqualTo(4);
    assertThat(displays.shown).contains("ore-2:61:2");
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(3, 61, 1, "short_grass");

    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "iron_ore").blockAt(3, 60, 0), true, 0));
    assertThat(platform.world().blockAt(3, 61, 1).type()).isEqualTo(GRASS);

    kit.mutate("pickaxe", "swords");
    adapt.post(new BlockBreakEvent(player, platform.world().set(3, 60, 0, "iron_ore").blockAt(3, 60, 0), true, 0));
    player.setSneaking(true);
    swap();

    assertThat(platform.world().blockAt(3, 61, 1).type()).isEqualTo(GRASS);
    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isEqualTo(1D);
    assertThat(player.inventory().mainHand().damage()).isZero();
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

  private static final class RecordingDisplays implements ViewerDisplayPort {
    private final List<String> shown = new ArrayList<>();

    @Override
    public boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                             Rgb glowColor, int durationTicks) {
      shown.add(key);
      return true;
    }

    @Override
    public boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                            Rgb glowColor, double thickness, int durationTicks) {
      return true;
    }

    @Override
    public boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size,
                              BlockStateRef block, Rgb glowColor, boolean inside, int durationTicks) {
      return true;
    }

    @Override
    public boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location,
                                       BlockStateRef block, Rgb glowColor) {
      return true;
    }

    @Override
    public boolean isShowing(String channel, String key, UUID viewerId, Loc location) {
      return shown.contains(key);
    }

    @Override
    public void clearViewer(String channel, UUID viewerId) {
    }

    @Override
    public void clearViewerKey(String channel, String key, UUID viewerId) {
    }

    @Override
    public void clearChannel(String channel) {
    }
  }
}
