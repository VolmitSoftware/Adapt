package art.arcane.adapt.content.adaptation.crafting;

import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraftingMasterworkTest {
  @Test
  void successfulPiecesRollWithinTheConfiguredDurabilityRange() {
    double maximum = 0.5D;

    assertThat(CraftingMasterwork.rolledBonusPercent(maximum, 0.5D, 0.0D))
        .isCloseTo(0.25D, offset(1.0E-9D));
    assertThat(CraftingMasterwork.rolledBonusPercent(maximum, 0.5D, 0.4D))
        .isCloseTo(0.35D, offset(1.0E-9D));
    assertThat(CraftingMasterwork.rolledBonusPercent(maximum, 0.5D, 1.0D))
        .isCloseTo(0.5D, offset(1.0E-9D));
  }

  @Test
  void durabilityRollInputsAreClamped() {
    assertThat(CraftingMasterwork.rolledBonusPercent(0.5D, -1.0D, -1.0D)).isZero();
    assertThat(CraftingMasterwork.rolledBonusPercent(0.5D, 2.0D, 2.0D)).isEqualTo(0.5D);
    assertThat(CraftingMasterwork.rolledBonusPercent(-1.0D, 0.5D, 0.5D)).isZero();
    assertThat(CraftingMasterwork.clampChance(-0.1D)).isZero();
    assertThat(CraftingMasterwork.clampChance(1.1D)).isEqualTo(1.0D);
  }

  @Test
  void defaultsKeepEnchantsMinorAndDurabilityRollsInTheUpperHalf() {
    CraftingMasterwork.Config config = new CraftingMasterwork.Config();

    assertThat(config.bonusRollMinimumFraction).isEqualTo(0.5D);
    assertThat(config.enchantmentChance).isEqualTo(0.1D);
    assertThat(config.attributeChance).isEqualTo(0.15D);
  }

  @Test
  void shiftBatchReusesOnePreparedRollUntilTheNextItemIsCrafted() {
    NamespacedKey recipeKey = new NamespacedKey("adapt", "masterwork-test");
    ItemStack firstRoll = mock(ItemStack.class);
    ItemStack baseTemplate = mock(ItemStack.class);
    ItemStack freshBaseResult = mock(ItemStack.class);
    ItemStack storedRoll = mock(ItemStack.class);
    ItemStack returnedRoll = mock(ItemStack.class);
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(firstRoll.getType()).thenReturn(Material.IRON_LEGGINGS);
    when(firstRoll.clone()).thenReturn(baseTemplate, storedRoll);
    when(baseTemplate.clone()).thenReturn(freshBaseResult);
    when(storedRoll.clone()).thenReturn(returnedRoll);
    when(recipe.getKey()).thenReturn(recipeKey);
    CraftingMasterwork.ShiftBatch batch = new CraftingMasterwork.ShiftBatch(
        new CraftingMasterwork.ShiftBatchSpec(Material.IRON_LEGGINGS, recipeKey, 5),
        firstRoll
    );

    assertThat(batch.matches(recipe, firstRoll)).isTrue();
    assertThat(batch.isAwaitingNextResult()).isFalse();
    assertThat(batch.createBaseResult(1)).isSameAs(freshBaseResult);
    verify(freshBaseResult).setAmount(1);

    batch.awaitNextResult();
    assertThat(batch.isAwaitingNextResult()).isTrue();
    batch.cachePreparedResult(firstRoll, true);

    ItemStack cached = batch.getPreparedResult();
    assertThat(cached).isSameAs(returnedRoll);
    assertThat(batch.isAwaitingNextResult()).isFalse();
    assertThat(batch.consumeExpectedMasterwork()).isTrue();
    assertThat(batch.consumeExpectedMasterwork()).isFalse();

    batch.awaitNextResult();
    assertThat(batch.getPreparedResult()).isNull();
    assertThat(batch.isAwaitingNextResult()).isTrue();
  }

  @Test
  void shiftBatchClaimsOnlyOneEffect() {
    NamespacedKey recipeKey = new NamespacedKey("adapt", "masterwork-test");
    ItemStack baseResult = mock(ItemStack.class);
    when(baseResult.clone()).thenReturn(mock(ItemStack.class));
    CraftingMasterwork.ShiftBatch batch = new CraftingMasterwork.ShiftBatch(
        new CraftingMasterwork.ShiftBatchSpec(Material.IRON_LEGGINGS, recipeKey, 5),
        baseResult
    );

    assertThat(batch.claimEffect()).isTrue();
    assertThat(batch.claimEffect()).isFalse();
  }

  @Test
  void eventPipelineSupportsIndependentShiftCraftOutputs() throws ReflectiveOperationException {
    Method craftHandler = CraftingMasterwork.class.getDeclaredMethod("on", CraftItemEvent.class);
    Method prepareHandler = CraftingMasterwork.class.getDeclaredMethod("on", PrepareItemCraftEvent.class);
    Method craftedHandler = CraftingMasterwork.class.getDeclaredMethod("on", ItemCraftedEvent.class);

    assertThat(craftHandler.getAnnotation(EventHandler.class).priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(prepareHandler.getAnnotation(EventHandler.class).priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(craftedHandler.getAnnotation(EventHandler.class).priority()).isEqualTo(EventPriority.MONITOR);
  }
}
