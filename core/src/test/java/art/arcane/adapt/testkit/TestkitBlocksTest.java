package art.arcane.adapt.testkit;

import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fake containers hold items like real ones, and fake breaks drop only what the drop event leaves. */
class TestkitBlocksTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Miner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class));
    adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void aChestIsAContainerThatKeepsItsItemsAndStoneIsNot() {
    BlockRef chest = world.set(2, 61, 0, "chest").blockAt(2, 61, 0);
    BlockRef shulker = world.set(3, 61, 0, "red_shulker_box").blockAt(3, 61, 0);
    BlockRef stone = world.set(4, 61, 0, "stone").blockAt(4, 61, 0);

    InventoryRef inventory = chest.container();
    inventory.addItem(new FakeItem(Id.minecraft("diamond"), 3));

    assertThat(chest.container().contains(Id.minecraft("diamond"), 3)).isTrue();
    assertThat(chest.container().size()).isEqualTo(27);
    assertThat(chest.containerHalves()).containsExactly(chest);
    assertThat(shulker.container()).isNotNull();
    assertThat(stone.container()).isNull();
    assertThat(stone.containerHalves()).isEmpty();
  }

  @Test
  void aContainerReplacedByAnotherBlockLosesItsItems() {
    BlockRef chest = world.set(2, 61, 0, "chest").blockAt(2, 61, 0);
    chest.container().addItem(new FakeItem(Id.minecraft("diamond"), 3));

    world.set(2, 61, 0, "stone");
    world.set(2, 61, 0, "chest");

    assertThat(chest.container().contains(Id.minecraft("diamond"), 1)).isFalse();
  }

  @Test
  void aBreakDropsOnlyWhatTheDropHandlersLeave() {
    BlockRef ore = world.set(2, 61, 0, "coal_ore").blockAt(2, 61, 0);
    BlockRef stone = world.set(3, 61, 0, "stone").blockAt(3, 61, 0);
    adapt.bus().register(this, BlockDropItemEvent.class, EventPriority.NORMAL, (BlockDropItemEvent e) -> {
      if (e.brokenState().type().equals(Id.minecraft("stone"))) {
        e.items().clear();
      }
    });

    assertThat(platform.protection().attemptBreak(player, ore)).isTrue();
    assertThat(platform.protection().attemptBreak(player, stone)).isTrue();

    assertThat(world.items()).extracting((FakeItemEntity item) -> item.item().type())
        .containsExactly(Id.minecraft("coal_ore"));
  }

  @Test
  void aCancelledDropEventDropsNothing() {
    BlockRef ore = world.set(2, 61, 0, "coal_ore").blockAt(2, 61, 0);
    adapt.bus().register(this, BlockDropItemEvent.class, EventPriority.NORMAL, (BlockDropItemEvent e) -> e.setCancelled(true));

    platform.protection().attemptBreak(player, ore);

    assertThat(ore.isAir()).isTrue();
    assertThat(world.items()).isEmpty();
  }

  @Test
  void aDropEntityRemovedByAHandlerNeverSpawns() {
    BlockRef ore = world.set(2, 61, 0, "coal_ore").blockAt(2, 61, 0);
    adapt.bus().register(this, BlockDropItemEvent.class, EventPriority.NORMAL, (BlockDropItemEvent e) -> {
      for (ItemEntityRef item : e.items()) {
        item.remove();
      }
    });

    platform.protection().attemptBreak(player, ore);

    assertThat(world.items()).isEmpty();
  }
}
