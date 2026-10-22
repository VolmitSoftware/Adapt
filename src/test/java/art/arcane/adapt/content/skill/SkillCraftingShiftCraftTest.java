package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.xp.CraftingXpTransaction;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillCraftingShiftCraftTest {
  @BeforeAll
  static void initializeBlockRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<BlockType> blocks = Registry.BLOCK;
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
      doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
    }
  }

  @Test
  void shiftCraftCountsOnlyTheResultsActuallyPickedUp() {
    CraftItemEvent event = craft(Material.STICK, stack(Material.OAK_PLANKS, 32));
    CraftingXpTransaction transaction = CraftingXpTransaction.open(event);
    assertThat(transaction).isNotNull();
    assertThat(transaction.committedItems()).isZero();
    transaction.accept(stack(Material.STICK, 32));
    transaction.accept(stack(Material.STICK, 32));
    assertThat(transaction.committedItems()).isEqualTo(64);
  }

  @Test
  void noOpAndCancelledClicksCannotOpenAReward() {
    CraftItemEvent event = craft(Material.STICK, stack(Material.OAK_PLANKS, 32));
    when(event.getAction()).thenReturn(InventoryAction.NOTHING);
    assertThat(CraftingXpTransaction.open(event)).isNull();
    when(event.getAction()).thenReturn(InventoryAction.CLONE_STACK);
    assertThat(CraftingXpTransaction.open(event)).isNull();
    when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
    when(event.isCancelled()).thenReturn(true);
    assertThat(CraftingXpTransaction.open(event)).isNull();
  }

  @Test
  void missingOrMismatchedCommittedResultsNeverPay() {
    CraftItemEvent event = craft(Material.STICK, stack(Material.OAK_PLANKS, 32));
    CraftingXpTransaction transaction = CraftingXpTransaction.open(event);
    transaction.accept(null);
    transaction.accept(stack(Material.STICK, 0));
    transaction.accept(stack(Material.DIAMOND, 64));
    assertThat(transaction.committedItems()).isZero();
    transaction.accept(stack(Material.STICK, 4));
    when(event.isCancelled()).thenReturn(true);
    assertThat(transaction.committedItems()).isZero();
  }

  @Test
  void resourceNeutralConversionsDoNotRewardEitherDirection() {
    Material[][] families = {
        {Material.IRON_NUGGET, Material.IRON_INGOT, Material.IRON_BLOCK},
        {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK},
        {Material.COPPER_INGOT, Material.COPPER_BLOCK},
        {Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK},
        {Material.RAW_IRON, Material.RAW_IRON_BLOCK},
        {Material.RAW_GOLD, Material.RAW_GOLD_BLOCK},
        {Material.RAW_COPPER, Material.RAW_COPPER_BLOCK},
        {Material.DIAMOND, Material.DIAMOND_BLOCK},
        {Material.EMERALD, Material.EMERALD_BLOCK},
        {Material.LAPIS_LAZULI, Material.LAPIS_BLOCK},
        {Material.REDSTONE, Material.REDSTONE_BLOCK},
        {Material.COAL, Material.COAL_BLOCK},
        {Material.WHEAT, Material.HAY_BLOCK},
        {Material.DRIED_KELP, Material.DRIED_KELP_BLOCK},
        {Material.SLIME_BALL, Material.SLIME_BLOCK},
        {Material.BONE_MEAL, Material.BONE_BLOCK},
        {Material.HONEY_BOTTLE, Material.HONEY_BLOCK}
    };
    for (Material[] family : families) {
      for (Material input : family) {
        for (Material output : family) {
          assertThat(CraftingXpTransaction.open(craft(output, stack(input, 9)))).isNull();
        }
      }
    }
    assertThat(CraftingXpTransaction.open(craft(Material.IRON_PICKAXE,
        stack(Material.IRON_INGOT, 3), stack(Material.STICK, 2)))).isNotNull();
    assertThat(CraftingXpTransaction.open(craft(Material.QUARTZ_BLOCK, stack(Material.QUARTZ, 4)))).isNotNull();
  }

  @Test
  void cancelledFurnacesCannotCreateSpatialRewards() {
    SkillCrafting skill = mock(SkillCrafting.class, CALLS_REAL_METHODS);
    FurnaceSmeltEvent event = mock(FurnaceSmeltEvent.class);
    Block block = mock(Block.class);
    when(event.isCancelled()).thenReturn(true);
    when(event.getBlock()).thenReturn(block);
    skill.on(event);
    verifyNoInteractions(block);
  }

  private static CraftItemEvent craft(Material output, ItemStack... matrix) {
    CraftingInventory inventory = mock(CraftingInventory.class);
    ItemStack result = stack(output, 1);
    when(inventory.getMatrix()).thenReturn(matrix);
    when(inventory.getResult()).thenReturn(result);
    CraftItemEvent event = mock(CraftItemEvent.class);
    when(event.getInventory()).thenReturn(inventory);
    when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
    return event;
  }

  private static ItemStack stack(Material material, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    when(stack.getAmount()).thenReturn(amount);
    when(stack.clone()).thenReturn(stack);
    when(stack.isSimilar(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack other = invocation.getArgument(0);
      return other.getType() == material;
    });
    return stack;
  }
}
