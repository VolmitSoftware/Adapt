package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.architect.ArchitectElevator;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.content.item.multiItems.MultiArmor;
import art.arcane.adapt.content.item.multiItems.OmniTool;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The items a player made before mutating keep working afterwards: Elevator, Multi-Armor, OMNI-TOOL and Time Bomb. */
class HeritageItemsRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private TestAdapt adapt;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillBlocking.class, SkillExcavation.class,
        SkillChronos.class));
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void elevatorsStillCarryAPlayerWhoMutatedArchitect() {
    FakePlayer player = mutated("Builder", "architect", "blocking");
    BlockRef lower = place(player, 55);
    BlockRef upper = place(player, 60);
    player.moveTo(player.location().withY(61D));

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(lower.data().has(DataKey.adapt("elevator", DataCodecs.INT))).isTrue();
    assertThat(upper.data().has(DataKey.adapt("elevator", DataCodecs.INT))).isTrue();
    assertThat(player.location().blockY()).isEqualTo(56);
  }

  @Test
  void elevatorsAreCraftableAfterMutating() {
    FakePlayer player = mutated("Builder", "architect", "blocking");

    assertThat(craft(player, Id.adapt("elevator")).isCancelled()).isFalse();
  }

  @Test
  void multiArmorStillMergesForAPlayerWhoMutatedBlocking() {
    FakePlayer player = mutated("Guard", "blocking", "architect");
    InventoryClickEvent click = new InventoryClickEvent(player, InventoryKind.CRAFTING, InventoryKind.PLAYER, 0, 38,
        SlotKind.ARMOR, ClickKind.LEFT, InventoryAction.SWAP_WITH_CURSOR, -1, FakeItem.of("diamond_chestplate"),
        FakeItem.of("elytra"));

    adapt.post(click);

    assertThat(click.isCancelled()).isTrue();
    assertThat(new MultiArmor().isMultiItem(click.currentItem())).isTrue();
  }

  @Test
  void omniToolsStillBreakBlocksForAPlayerWhoMutatedExcavation() {
    FakePlayer player = mutated("Digger", "excavation", "chronos");
    player.holding(new OmniTool().build(FakeItem.tool("iron_pickaxe", 250), FakeItem.tool("iron_shovel", 250)));
    BlockRef dirt = world.set(2, 60, 0, "dirt").blockAt(2, 60, 0);

    BlockBreakEvent broken = adapt.post(new BlockBreakEvent(player, dirt, true, 0));

    assertThat(broken.isCancelled()).isFalse();
  }

  @Test
  void timeBombsStillThrowAndCraftForAMutatedChronomancerWithFrozenMoment() {
    FakePlayer player = mutated("Clock", "chronos", "excavation");
    player.holding(ChronoTimeBombItem.withData());
    assertThat(throwBomb(player).isCancelled()).isTrue();

    adapt.core().getAdaptServer().getPlayer(player).getData().getSkillLine("chronos")
        .setAdaptation(registry.getAdaptation("upgraded-chronos-frozen-moment"), 1);

    assertThat(throwBomb(player).isCancelled()).isFalse();
    assertThat(craft(player, Id.adapt("chronos-time-bomb")).isCancelled()).isFalse();
  }

  private FakePlayer mutated(String name, String lead, String partner) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    new MutationKit(adapt, profile).mutate(lead, partner);
    platform.scheduler().advance(50);
    return player;
  }

  private BlockRef place(FakePlayer player, int y) {
    BlockRef block = world.set(0, y, 0, "note_block").blockAt(0, y, 0);
    ItemRef item = registry.getSkill("architect").getAdaptations().stream()
        .filter(ArchitectElevator.class::isInstance)
        .map(ArchitectElevator.class::cast)
        .findFirst().orElseThrow().getElevatorItem();
    BlockPlaceEvent placed = adapt.post(new BlockPlaceEvent(player, block, FakeBlockState.of("air"),
        block.relative(BlockFace.DOWN), item, Hand.MAIN_HAND));
    assertThat(placed.isCancelled()).as("placing the elevator at %s", y).isFalse();
    return block;
  }

  private PlayerInteractEvent throwBomb(FakePlayer player) {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private CraftItemEvent craft(FakePlayer player, Id recipe) {
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("stone"), FakeItem.empty(), recipe,
        CraftItemEvent.RecipeKind.SHAPED, List.of()));
  }
}
