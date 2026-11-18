package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.xp.CraftingXpTransaction;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillCraftingShiftCraftTest {
  @Test
  void shiftCraftCountsOnlyTheResultsActuallyPickedUp() {
    CraftItemEvent event = craft(Id.minecraft("stick"), stack(Id.minecraft("oak_planks"), 32));
    CraftingXpTransaction transaction = CraftingXpTransaction.open(event);
    assertThat(transaction).isNotNull();
    assertThat(transaction.committedItems()).isZero();
    transaction.accept(stack(Id.minecraft("stick"), 32));
    transaction.accept(stack(Id.minecraft("stick"), 32));
    assertThat(transaction.committedItems()).isEqualTo(64);
  }

  @Test
  void noOpAndCancelledClicksCannotOpenAReward() {
    assertThat(CraftingXpTransaction.open(craft(Id.minecraft("stick"), InventoryAction.NOTHING,
        stack(Id.minecraft("oak_planks"), 32)))).isNull();
    assertThat(CraftingXpTransaction.open(craft(Id.minecraft("stick"), InventoryAction.CLONE_STACK,
        stack(Id.minecraft("oak_planks"), 32)))).isNull();
    CraftItemEvent event = craft(Id.minecraft("stick"), InventoryAction.PICKUP_ALL, stack(Id.minecraft("oak_planks"), 32));
    event.setCancelled(true);
    assertThat(CraftingXpTransaction.open(event)).isNull();
  }

  @Test
  void missingOrMismatchedCommittedResultsNeverPay() {
    CraftItemEvent event = craft(Id.minecraft("stick"), stack(Id.minecraft("oak_planks"), 32));
    CraftingXpTransaction transaction = CraftingXpTransaction.open(event);
    transaction.accept(null);
    transaction.accept(stack(Id.minecraft("stick"), 0));
    transaction.accept(stack(Id.minecraft("diamond"), 64));
    assertThat(transaction.committedItems()).isZero();
    transaction.accept(stack(Id.minecraft("stick"), 4));
    event.setCancelled(true);
    assertThat(transaction.committedItems()).isZero();
  }

  @Test
  void resourceNeutralConversionsDoNotRewardEitherDirection() {
    Id[][] families = {
        {Id.minecraft("iron_nugget"), Id.minecraft("iron_ingot"), Id.minecraft("iron_block")},
        {Id.minecraft("gold_nugget"), Id.minecraft("gold_ingot"), Id.minecraft("gold_block")},
        {Id.minecraft("copper_ingot"), Id.minecraft("copper_block")},
        {Id.minecraft("netherite_ingot"), Id.minecraft("netherite_block")},
        {Id.minecraft("raw_iron"), Id.minecraft("raw_iron_block")},
        {Id.minecraft("raw_gold"), Id.minecraft("raw_gold_block")},
        {Id.minecraft("raw_copper"), Id.minecraft("raw_copper_block")},
        {Id.minecraft("diamond"), Id.minecraft("diamond_block")},
        {Id.minecraft("emerald"), Id.minecraft("emerald_block")},
        {Id.minecraft("lapis_lazuli"), Id.minecraft("lapis_block")},
        {Id.minecraft("redstone"), Id.minecraft("redstone_block")},
        {Id.minecraft("coal"), Id.minecraft("coal_block")},
        {Id.minecraft("wheat"), Id.minecraft("hay_block")},
        {Id.minecraft("dried_kelp"), Id.minecraft("dried_kelp_block")},
        {Id.minecraft("slime_ball"), Id.minecraft("slime_block")},
        {Id.minecraft("bone_meal"), Id.minecraft("bone_block")},
        {Id.minecraft("honey_bottle"), Id.minecraft("honey_block")}
    };
    for (Id[] family : families) {
      for (Id input : family) {
        for (Id output : family) {
          assertThat(CraftingXpTransaction.open(craft(output, stack(input, 9)))).isNull();
        }
      }
    }
    assertThat(CraftingXpTransaction.open(craft(Id.minecraft("iron_pickaxe"),
        stack(Id.minecraft("iron_ingot"), 3), stack(Id.minecraft("stick"), 2)))).isNotNull();
    assertThat(CraftingXpTransaction.open(craft(Id.minecraft("quartz_block"), stack(Id.minecraft("quartz"), 4)))).isNotNull();
  }

  @Test
  void cancelledFurnacesCannotCreateSpatialRewards() {
    SkillCrafting skill = mock(SkillCrafting.class, CALLS_REAL_METHODS);
    FurnaceSmeltEvent event = mock(FurnaceSmeltEvent.class);
    BlockRef block = mock(BlockRef.class);
    when(event.isCancelled()).thenReturn(true);
    when(event.block()).thenReturn(block);
    skill.on(event);
    verifyNoInteractions(block);
  }

  private static CraftItemEvent craft(Id output, ItemRef... matrix) {
    return craft(output, InventoryAction.PICKUP_ALL, matrix);
  }

  private static CraftItemEvent craft(Id output, InventoryAction action, ItemRef... matrix) {
    return new CraftItemEvent(mock(PlayerRef.class), InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT, action, -1,
        stack(output, 1), mock(ItemRef.class), null, CraftItemEvent.RecipeKind.SHAPED, List.of(matrix));
  }

  private static ItemRef stack(Id type, int amount) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(type);
    when(stack.amount()).thenReturn(amount);
    when(stack.copy()).thenReturn(stack);
    when(stack.isSimilar(any(ItemRef.class))).thenAnswer(invocation -> {
      ItemRef other = invocation.getArgument(0);
      return other.type().equals(type);
    });
    return stack;
  }
}
