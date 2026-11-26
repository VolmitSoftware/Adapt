package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Crafting leading Architect: a template packs with its blocks into a crate that unfolds the build, paid only from itself. */
class PrefabCrateTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK = Id.minecraft("oak_planks");
  private static final Id KEY = Id.adapt(PrefabCrate.TOOL_ID);

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillCrafting.class, SkillArchitect.class));
    bench.world.set(10, 61, 10, "stone").set(11, 61, 10, "stone").set(12, 61, 10, "oak_planks");
    bench.player.inventory().setItem(20, new FakeItem(STONE, 5));
    bench.player.inventory().setItem(21, new FakeItem(OAK, 5));
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void aTemplatePacksItsBlocksAndTheCrateUnfoldsTheBuildFromThem() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    CraftItemEvent craft = craft();
    ItemRef crate = craft.currentItem();
    assertThat(craft.isCancelled()).isFalse();
    assertThat(bench.count(STONE)).isEqualTo(3);
    assertThat(bench.count(OAK)).isEqualTo(4);

    hold(crate);
    click(InteractAction.RIGHT_CLICK_BLOCK);
    click(InteractAction.RIGHT_CLICK_BLOCK);
    bench.advance(10);

    assertThat(bench.placed(STONE, row())).isEqualTo(2);
    assertThat(bench.placed(OAK, row())).isEqualTo(1);
    assertThat(bench.count(STONE)).isEqualTo(3);
    assertThat(bench.count(OAK)).isEqualTo(4);
    assertThat(bench.player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(templates()).isEqualTo(1);
  }

  @Test
  void theReverseOrderCannotPackACrate() {
    bench.signature("architect", "crafting", 3);
    bench.upgrade("architect", Skyhook.class, 1);

    CraftItemEvent craft = craft();

    assertThat(craft.isCancelled()).isTrue();
    assertThat(bench.count(STONE)).isEqualTo(5);
  }

  @Test
  void nothingIsPackedWithoutTheLearnedSignature() {
    bench.kit.mutate("crafting", "architect");
    bench.upgrade("architect", Skyhook.class, 1);

    CraftItemEvent craft = craft();

    assertThat(craft.isCancelled()).isTrue();
    assertThat(bench.count(STONE)).isEqualTo(5);
  }

  @Test
  void aCopyLargerThanTheLevelAllowsOrShortOfBlocksPacksNothing() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);
    PrefabCrate crate = bench.handler("crafting", "architect", PrefabCrate.class);
    crate.getConfig().blocksAtFirstLevel = 2;

    PrepareItemCraftEvent tooLarge = prepare();
    CraftItemEvent refused = craft();
    crate.getConfig().blocksAtFirstLevel = 216;
    bench.player.inventory().setItem(21, FakeItem.empty());
    CraftItemEvent shortOfOak = craft();

    assertThat(tooLarge.result().isEmpty()).isTrue();
    assertThat(refused.isCancelled()).isTrue();
    assertThat(shortOfOak.isCancelled()).isTrue();
    assertThat(bench.count(STONE)).isEqualTo(5);
    assertThat(crate.blocks(3)).isEqualTo(2744);
  }

  @Test
  void aCrateUnfoldsOnceAndPaysNoBlockTwice() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);
    ItemRef crate = craft().currentItem();
    hold(crate.copyWithAmount(1));

    click(InteractAction.RIGHT_CLICK_BLOCK);
    click(InteractAction.RIGHT_CLICK_BLOCK);
    bench.advance(10);
    for (BlockRef block : row()) {
      block.setType(Id.minecraft("air"), false);
    }
    click(InteractAction.RIGHT_CLICK_BLOCK);
    click(InteractAction.RIGHT_CLICK_BLOCK);
    bench.advance(10);

    assertThat(bench.placed(STONE, row()) + bench.placed(OAK, row())).isZero();
    assertThat(bench.count(STONE)).isEqualTo(3);
    assertThat(bench.count(OAK)).isEqualTo(4);
  }

  @Test
  void theResultSlotShowsAnEmptyCrateSoATableWithoutTheCraftHandlerHandsOutNoBlocks() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);
    PrefabCrate prefab = bench.handler("crafting", "architect", PrefabCrate.class);

    PrepareItemCraftEvent shown = prepare();
    PrefabCrate.Crate.Data data = prefab.crate().read(shown.result());

    assertThat(data.template.hasPattern()).isTrue();
    assertThat(data.contents).isEmpty();
    assertThat(bench.count(STONE)).isEqualTo(5);
  }

  @Test
  void aClickThatCraftsNothingTakesNoBlocks() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    CraftItemEvent nothing = craft(InventoryAction.NOTHING, -1, FakeItem.empty());
    CraftItemEvent heldCursor = craft(InventoryAction.PICKUP_ALL, -1, FakeItem.of("dirt"));
    bench.player.inventory().setItem(3, FakeItem.of("dirt"));
    CraftItemEvent occupiedKey = craft(InventoryAction.HOTBAR_SWAP, 3, FakeItem.empty());
    for (int slot = 0; slot < 36; slot++) {
      if (bench.player.inventory().item(slot).isEmpty()) {
        bench.player.inventory().setItem(slot, new FakeItem(Id.minecraft("dirt"), 64));
      }
    }
    CraftItemEvent shiftIntoFull = craft(InventoryAction.MOVE_TO_OTHER_INVENTORY, -1, FakeItem.empty());

    assertThat(nothing.isCancelled()).isTrue();
    assertThat(heldCursor.isCancelled()).isTrue();
    assertThat(occupiedKey.isCancelled()).isTrue();
    assertThat(shiftIntoFull.isCancelled()).isTrue();
    assertThat(bench.count(STONE)).isEqualTo(5);
    assertThat(bench.count(OAK)).isEqualTo(5);
  }

  @Test
  void aNumberKeyOntoAnEmptySlotCrafts() {
    bench.signature("crafting", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    CraftItemEvent key = craft(InventoryAction.HOTBAR_SWAP, 4, FakeItem.empty());

    assertThat(key.isCancelled()).isFalse();
    assertThat(bench.count(STONE)).isEqualTo(3);
  }

  private void hold(ItemRef crate) {
    bench.player.inventory().setHeldSlot(8);
    bench.player.inventory().setItem(8, crate);
  }

  private ItemRef template() {
    MasonsTemplate tool = MasonsTemplate.find();
    MasonsTemplate.Data data = new MasonsTemplate.Data();
    data.pattern = ArchitectPattern.copy(bench.world, 10, 61, 10, 12, 61, 10);
    return tool.create(data);
  }

  private List<ItemRef> matrix() {
    return List.of(template(), FakeItem.of("chest"));
  }

  private PrepareItemCraftEvent prepare() {
    return bench.adapt.post(new PrepareItemCraftEvent(bench.player, matrix(), KEY, CraftItemEvent.RecipeKind.SHAPELESS,
        false, FakeItem.of("chest")));
  }

  private CraftItemEvent craft() {
    return craft(InventoryAction.PICKUP_ALL, -1, FakeItem.empty());
  }

  private CraftItemEvent craft(InventoryAction action, int hotbarButton, ItemRef cursor) {
    prepare();
    return bench.adapt.post(new CraftItemEvent(bench.player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        action, hotbarButton, FakeItem.of("chest"), cursor, KEY, CraftItemEvent.RecipeKind.SHAPELESS, matrix()));
  }

  private void click(InteractAction action) {
    BlockRef ground = bench.world.blockAt(0, 60, 5);
    bench.adapt.post(new PlayerInteractEvent(bench.player, action, Hand.MAIN_HAND, bench.player.inventory().mainHand(),
        ground, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private List<BlockRef> row() {
    return List.of(bench.world.blockAt(-1, 61, 5), bench.world.blockAt(0, 61, 5), bench.world.blockAt(1, 61, 5));
  }

  private int templates() {
    int found = 0;
    for (ItemRef item : bench.player.inventory().contents()) {
      if (MasonsTemplate.find().is(item)) {
        found++;
      }
    }
    return found;
  }
}
