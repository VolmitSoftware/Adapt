package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationBlocks;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.GeneratedStructure;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Discovery with Swords and Herbalism: each of the six blocks through the recipes that use it.
 */
class DiscoveryBlocksRuntimeTest {
  private static final Id GRASS = Id.minecraft("short_grass");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

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
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillDiscovery.class));
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
  void graftCutsTheArcOnANewFindAsLeadButNotOnANewChunkWithBothWorkCues() {
    kit.mutate("discovery", "swords");
    kit.learn("discovery", "swords", Recipe.GRAFT, 1);
    platform.world().set(16, 61, 1, "short_grass");

    enterChunk(1);
    assertThat(platform.world().blockAt(16, 61, 1).type()).isEqualTo(GRASS);

    discovery().seeBiome(player, Id.minecraft("plains"));
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(16, 61, 1).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("discovery")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void echoCutsTheArcOnANewFindAsPartnerButNotOnARepeat() {
    kit.mutate("swords", "discovery");
    kit.learn("swords", "discovery", Recipe.ECHO, 1);
    discovery().seeItem(player, Id.minecraft("flint"));
    platform.world().set(0, 61, 1, "short_grass");
    platform.scheduler().advance(200);

    discovery().seeItem(player, Id.minecraft("flint"));
    assertThat(platform.world().blockAt(0, 61, 1).type()).isEqualTo(GRASS);

    discovery().seeItem(player, Id.minecraft("amethyst_shard"));
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isTrue();
  }

  @Test
  void echoRevealsTheTargetOfASwordCritAndTheNextHitOnItDealsMore() {
    kit.mutate("discovery", "swords");
    kit.learn("discovery", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving other = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(player.actionBars()).isNotEmpty();
    assertThat(kit.hit(player, other, 5D, false).damage()).isEqualTo(5D);
    assertThat(kit.hit(player, zombie, 5D, false).damage()).isCloseTo(6D, within(1.0E-9D));
    assertThat(kit.hit(player, zombie, 5D, false).damage()).isEqualTo(5D);
  }

  @Test
  void graftPingsTheNearestChestWhenACropIsHarvested() {
    kit.mutate("herbalism", "discovery");
    kit.learn("herbalism", "discovery", Recipe.GRAFT, 1);
    platform.world().set(6, 61, 6, "chest");

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));

    assertThat(displays.shown).containsExactly("ping", "structure-direction");
  }

  @Test
  void graftPointsAtANearbyStructureWhenNoChestIsCloser() {
    kit.mutate("herbalism", "discovery");
    kit.learn("herbalism", "discovery", Recipe.GRAFT, 1);
    platform.world().structuresAt(new ChunkPos(1, 0), List.of(new GeneratedStructure(Id.minecraft("igloo"),
        Id.minecraft("igloo"), new Box(16D, 60D, 0D, 24D, 66D, 8D))));

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    assertThat(displays.shown).containsExactly("structure-direction");
  }

  @Test
  void barterSpendsSwordDurabilityForNightVisionAndGlowingChests() {
    kit.mutate("discovery", "swords");
    kit.learn("discovery", "swords", Recipe.BARTER, 1);
    platform.world().set(3, 61, 3, "chest");
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = swap();

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effect(NIGHT_VISION).durationTicks()).isEqualTo(160);
    assertThat(displays.shown).containsExactly("chest-3:61:3");
  }

  @Test
  void aWalledInChestNeverGlowsThroughTheWall() {
    kit.mutate("discovery", "swords");
    kit.learn("discovery", "swords", Recipe.BARTER, 1);
    platform.world().set(3, 61, 3, "chest");
    wallIn(3, 61, 3);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(displays.shown).doesNotContain("chest-3:61:3");
  }

  @Test
  void aWalledInChestIsNeverPinged() {
    kit.mutate("herbalism", "discovery");
    kit.learn("herbalism", "discovery", Recipe.GRAFT, 1);
    platform.world().set(6, 61, 6, "chest");
    wallIn(6, 61, 6);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));

    assertThat(displays.shown).doesNotContain("ping");
  }

  @Test
  void barterSpendsAPaperAndRefusesWithoutOne() {
    kit.mutate("swords", "discovery");
    kit.learn("swords", "discovery", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isFalse();

    player.inventory().addItem(new FakeItem(Id.minecraft("map"), 1));
    player.inventory().addItem(new FakeItem(Id.minecraft("paper"), 2));
    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
    assertThat(player.inventory().contains(Id.minecraft("paper"), 2)).isFalse();
    assertThat(player.inventory().contains(Id.minecraft("paper"), 1)).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("map"), 1)).isTrue();
  }

  @Test
  void attunementGrantsAttackSpeedOnlyInAChunkNeverEnteredBefore() {
    kit.mutate("discovery", "swords");
    kit.learn("discovery", "swords", Recipe.ATTUNEMENT, 1);
    StateBlock<?> state = (StateBlock<?>) MutationBlocks.find(kit.registry().getSkill("discovery"), BlockSlot.STATE);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    enterChunk(1);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    enterChunk(2);
    assertThat(state.holds(player)).isTrue();
    enterChunk(1);
    assertThat(state.holds(player)).isFalse();
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(0, 61, 1, "short_grass");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    discovery().seeBiome(player, Id.minecraft("plains"));
    assertThat(platform.world().blockAt(0, 61, 1).type()).isEqualTo(GRASS);

    kit.mutate("discovery", "swords");
    discovery().seeBiome(player, Id.minecraft("desert"));
    kit.hit(player, zombie, 4D, true);
    EntityDamageByEntityEvent next = kit.hit(player, zombie, 5D, false);
    player.setSneaking(true);
    swap();

    assertThat(platform.world().blockAt(0, 61, 1).type()).isEqualTo(GRASS);
    assertThat(next.damage()).isEqualTo(5D);
    assertThat(player.hasEffect(NIGHT_VISION)).isFalse();
  }

  private SkillDiscovery discovery() {
    return (SkillDiscovery) kit.registry().getSkill("discovery");
  }

  private void wallIn(int x, int y, int z) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
          if (dx != 0 || dy != 0 || dz != 0) {
            platform.world().set(x + dx, y + dy, z + dz, "stone");
          }
        }
      }
    }
  }

  private void enterChunk(int chunkX) {
    Loc from = player.location();
    Loc to = new Loc(platform.world(), (chunkX * 16) + 0.5D, 61D, 0.5D);
    player.moveTo(to);
    adapt.post(new PlayerMoveEvent(player, from, to));
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(Id.minecraft("wheat"), Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
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
      shown.add(key);
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
