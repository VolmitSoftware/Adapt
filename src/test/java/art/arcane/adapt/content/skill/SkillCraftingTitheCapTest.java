package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.golem.item.GolemItems;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillCraftingTitheCapTest {
  private static final NamespacedKey TITHED = new NamespacedKey("adapt", "tithe-cap-test");

  @AfterEach
  void clearTithe() {
    GolemItems.recipeTithe().set(TITHED, 0);
  }

  @Test
  void shiftCraftXpStopsAtTheCraftsATitheLetsThePlayerAfford() {
    assertThat(SkillCrafting.tithedCraftCap(1, 1, 1, 64)).isEqualTo(1);
    assertThat(SkillCrafting.tithedCraftCap(7, 3, 4, 64)).isEqualTo(8);
    assertThat(SkillCrafting.tithedCraftCap(0, 2, 1, 64)).isZero();
    assertThat(SkillCrafting.tithedCraftCap(100, 1, 4, 12)).isEqualTo(12);
  }

  @Test
  void recipesWithoutATitheKeepTheFullShiftCraftAmount() {
    assertThat(SkillCrafting.tithedCraftCap(1, 0, 1, 64)).isEqualTo(64);
    assertThat(SkillCrafting.tithedCraftCap(0, 0, 4, 64)).isEqualTo(64);
    assertThat(SkillCrafting.tithedCraftCap(5, 1, 0, 64)).isEqualTo(64);
  }

  @Test
  void aShiftClickOnATithedRecipeCountsOnlyTheCraftsTheCraftersLevelsPayFor() throws ReflectiveOperationException {
    GolemItems.recipeTithe().set(TITHED, 2);

    assertThat(craftedItems(GameMode.SURVIVAL, 7)).containsExactly(6D);
    assertThat(craftedItems(GameMode.CREATIVE, 7)).containsExactly(64D);

    GolemItems.recipeTithe().set(TITHED, 0);

    assertThat(craftedItems(GameMode.SURVIVAL, 7)).containsExactly(64D);
  }

  private static List<Double> craftedItems(GameMode mode, int levels) throws ReflectiveOperationException {
    List<Double> crafted = new ArrayList<>();
    SkillCrafting.Config config = new SkillCrafting.Config();
    SkillCrafting skill = mock(SkillCrafting.class, invocation -> switch (invocation.getMethod().getName()) {
      case "shouldReturnForPlayer" -> {
        invocation.<Runnable>getArgument(2).run();
        yield null;
      }
      case "getConfig" -> config;
      case "getValue" -> 1D;
      case "addStat" -> {
        if ("crafted.items".equals(invocation.getArgument(1))) {
          crafted.add(invocation.getArgument(2));
        }
        yield null;
      }
      case "xp" -> throw new XpGranted();
      default -> invocation.callRealMethod();
    });
    Field cooldowns = SkillCrafting.class.getDeclaredField("cooldowns");
    cooldowns.setAccessible(true);
    cooldowns.set(skill, PlayerStateRegistry.newCooldowns());

    assertThatThrownBy(() -> skill.on(shiftCraft(mode, levels))).isInstanceOf(XpGranted.class);
    return crafted;
  }

  private static CraftItemEvent shiftCraft(GameMode mode, int levels) {
    ItemStack result = stack(2);
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(TITHED);
    when(recipe.getResult()).thenReturn(result);
    ItemStack[] matrix = {stack(32), stack(32)};
    CraftingInventory grid = mock(CraftingInventory.class);
    when(grid.getResult()).thenReturn(result);
    when(grid.getMatrix()).thenReturn(matrix);
    Inventory bottom = mock(Inventory.class);
    when(bottom.getContents()).thenReturn(new ItemStack[36]);
    InventoryView view = mock(InventoryView.class);
    when(view.getBottomInventory()).thenReturn(bottom);
    Player crafter = mock(Player.class);
    when(crafter.getUniqueId()).thenReturn(UUID.randomUUID());
    when(crafter.getGameMode()).thenReturn(mode);
    when(crafter.getLevel()).thenReturn(levels);
    CraftItemEvent event = mock(CraftItemEvent.class);
    when(event.getInventory()).thenReturn(grid);
    when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
    when(event.getRecipe()).thenReturn(recipe);
    when(event.getView()).thenReturn(view);
    when(event.getWhoClicked()).thenReturn(crafter);
    return event;
  }

  private static ItemStack stack(int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(Material.STONE);
    when(stack.getAmount()).thenReturn(amount);
    when(stack.getMaxStackSize()).thenReturn(64);
    return stack;
  }

  private static final class XpGranted extends RuntimeException {
  }
}
