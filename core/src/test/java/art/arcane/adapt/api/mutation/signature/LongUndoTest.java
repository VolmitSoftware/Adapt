package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.adaptation.architect.upgraded.WreckingHand;
import art.arcane.adapt.content.adaptation.chronos.upgraded.TimeLord;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Chronos with Architect: the clock gesture undoes the newest Architect build, returning what each block cost. */
class LongUndoTest {
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK = Id.minecraft("oak_planks");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillArchitect.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theGestureUndoesTheNewestBuildAndReturnsWhatItCost() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    build(6);

    assertThat(swap().isCancelled()).isTrue();
    platform.scheduler().advance(2);

    for (BlockRef block : row(6)) {
      assertThat(block.isAir()).isTrue();
    }
    assertThat(count(STONE)).isEqualTo(6);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void architectLeadingWorksTheSameWay() {
    kit.mutate("architect", "chronos");
    kit.learn("architect", "chronos", Recipe.CONVERGENCE, 3);
    build(3);

    swap();
    platform.scheduler().advance(2);

    assertThat(count(STONE)).isEqualTo(3);
  }

  @Test
  void nothingIsUndoneWithoutTheMutationOrPastTheWindow() {
    build(4);
    assertThat(swap().isCancelled()).isFalse();
    platform.scheduler().advance(2);
    assertThat(count(STONE)).isZero();

    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    signature().getConfig().windowMinutesLevel1 = -1D;
    assertThat(swap().isCancelled()).isTrue();
    platform.scheduler().advance(2);

    assertThat(count(STONE)).isZero();
  }

  @Test
  void aBlockBrokenOrSwappedBeforeTheUndoReturnsNothingAndNothingComesBackTwice() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 3);
    build(4);
    List<BlockRef> blocks = row(4);
    platform.world().set(blocks.get(0).pos(), AdaptCore.platform().host().items().blockState(OAK));
    platform.world().set(blocks.get(1).pos(), AdaptCore.platform().host().items().blockState(Id.minecraft("air")));

    swap();
    platform.scheduler().advance(2);
    assertThat(count(STONE)).isEqualTo(2);

    swap();
    platform.scheduler().advance(2);
    assertThat(BuildBatches.get().undo(player, Set.of(MasterBuilder.ID), 600_000L, 8,
        (BuildResult ignored) -> {
        })).isFalse();
    assertThat(count(STONE)).isEqualTo(2);
    assertThat(blocks.get(0).type()).isEqualTo(OAK);
  }

  @Test
  void timeLordsRestorationPlacesTheUndoneBuildBackPaidAgainOnce() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    learnTimeLord();
    build(4);
    swap();
    platform.scheduler().advance(2);
    assertThat(count(STONE)).isEqualTo(4);

    restoreAt(platform.world().blockAt(1, 60, 3));
    platform.scheduler().advance(4);

    for (BlockRef block : row(4)) {
      assertThat(block.type()).isEqualTo(STONE);
    }
    assertThat(count(STONE)).isZero();

    platform.world().set(row(4).get(0).pos(), AdaptCore.platform().host().items().blockState(Id.minecraft("air")));
    player.inventory().addItem(new FakeItem(STONE, 1));
    restoreAt(platform.world().blockAt(1, 60, 3));
    platform.scheduler().advance(4);

    assertThat(row(4).get(0).isAir()).isTrue();
    assertThat(count(STONE)).isEqualTo(1);
  }

  @Test
  void aRedoWithoutTheItemsPlacesNothing() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    learnTimeLord();
    build(3);
    swap();
    platform.scheduler().advance(2);
    for (int slot = 0; slot < player.inventory().size(); slot++) {
      ItemRef item = player.inventory().item(slot);
      if (item != null && STONE.equals(item.type())) {
        player.inventory().setItem(slot, FakeItem.empty());
      }
    }

    restoreAt(platform.world().blockAt(1, 60, 3));
    platform.scheduler().advance(4);

    for (BlockRef block : row(3)) {
      assertThat(block.isAir()).isTrue();
    }
  }

  @Test
  void farFromTheUndoneBuildTheRestorationLeavesItUndone() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    learnTimeLord();
    build(3);
    swap();
    platform.scheduler().advance(2);

    restoreAt(platform.world().blockAt(60, 60, 60));
    platform.scheduler().advance(4);

    assertThat(count(STONE)).isEqualTo(3);
  }

  @Test
  void wreckingHandsBrushForgetsTheSpotsLongUndoCleared() {
    kit.mutate("chronos", "architect");
    kit.learn("chronos", "architect", Recipe.CONVERGENCE, 1);
    WreckingHand wreckingHand = kit.registry().getSkill("architect").getUpgrades().stream()
        .filter(WreckingHand.class::isInstance).map(WreckingHand.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("architect").setAdaptation(wreckingHand, 1);
    Skyhook skyhook = kit.registry().getSkill("architect").getUpgrades().stream().filter(Skyhook.class::isInstance)
        .map(Skyhook.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("architect").setAdaptation(skyhook, 1);
    build(4);
    WreckingHand.site(player, row(4));
    swap();
    platform.scheduler().advance(2);
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    for (BlockRef block : row(4)) {
      platform.world().set(block.pos(), stone);
    }
    MasonsTemplate.Data brush = new MasonsTemplate.Data();
    brush.replace = true;
    player.inventory().setItem(3, MasonsTemplate.find().create(brush));
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    player.inventory().setOffHand(new FakeItem(Id.minecraft("bricks"), 8));
    player.setSneaking(true);

    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), row(4).get(1), BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    platform.scheduler().advance(10);

    assertThat(platform.protection().broken()).isEmpty();
    for (BlockRef block : row(4)) {
      assertThat(block.type()).isEqualTo(STONE);
    }
  }

  private void learnTimeLord() {
    TimeLord timeLord = kit.registry().getSkill("chronos").getUpgrades().stream().filter(TimeLord.class::isInstance)
        .map(TimeLord.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("chronos").setAdaptation(timeLord, 1);
  }

  private void restoreAt(BlockRef clicked) {
    player.setSneaking(true);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), clicked, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    player.setSneaking(false);
  }

  private LongUndo signature() {
    return kit.registry().getSkill("chronos").getSignatures().stream().filter(LongUndo.class::isInstance)
        .map(LongUndo.class::cast).findFirst().orElseThrow();
  }

  private void build(int length) {
    player.holding(new FakeItem(STONE, length));
    Adaptation<?> owner = kit.registry().getAdaptation(MasterBuilder.ID);
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    BuildBatch.Builder batch = BuildBatch.of(owner, player).pace(8).undoable(1);
    for (BlockRef block : row(length)) {
      batch.place(block, stone);
    }
    AtomicReference<BuildResult> result = new AtomicReference<>();
    assertThat(BuildBatches.get().start(batch.onDone(result::set).build())).isTrue();
    platform.scheduler().advance(2);
    assertThat(result.get().placed()).isEqualTo(length);
    player.holding(new FakeItem(CLOCK, 1));
  }

  private List<BlockRef> row(int length) {
    List<BlockRef> blocks = new ArrayList<>();
    for (int x = 1; x <= length; x++) {
      blocks.add(platform.world().blockAt(x, 61, 3));
    }
    return blocks;
  }

  private int count(Id type) {
    int total = 0;
    for (int slot = 0; slot < player.inventory().size(); slot++) {
      ItemRef item = player.inventory().item(slot);
      if (item != null && type.equals(item.type())) {
        total += item.amount();
      }
    }
    return total;
  }

  private PlayerSwapHandItemsEvent swap() {
    player.setSneaking(true);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }
}
