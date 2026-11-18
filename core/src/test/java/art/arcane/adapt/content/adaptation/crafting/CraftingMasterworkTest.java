package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
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
    Id recipeKey = Id.adapt("masterwork-test");
    ItemRef firstRoll = mock(ItemRef.class);
    ItemRef baseTemplate = mock(ItemRef.class);
    ItemRef freshBaseResult = mock(ItemRef.class);
    ItemRef storedRoll = mock(ItemRef.class);
    ItemRef returnedRoll = mock(ItemRef.class);
    when(firstRoll.type()).thenReturn(Id.minecraft("iron_leggings"));
    when(firstRoll.copy()).thenReturn(baseTemplate, storedRoll);
    when(baseTemplate.copy()).thenReturn(freshBaseResult);
    when(storedRoll.copy()).thenReturn(returnedRoll);
    CraftingMasterwork.ShiftBatch batch = new CraftingMasterwork.ShiftBatch(
        new CraftingMasterwork.ShiftBatchSpec(Id.minecraft("iron_leggings"), recipeKey, 5),
        firstRoll
    );

    assertThat(batch.matches(CraftItemEvent.RecipeKind.SHAPED, recipeKey, firstRoll)).isTrue();
    assertThat(batch.isAwaitingNextResult()).isFalse();
    assertThat(batch.createBaseResult(1)).isSameAs(freshBaseResult);
    verify(freshBaseResult).setAmount(1);

    batch.awaitNextResult();
    assertThat(batch.isAwaitingNextResult()).isTrue();
    batch.cachePreparedResult(firstRoll, true);

    ItemRef cached = batch.getPreparedResult();
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
    Id recipeKey = Id.adapt("masterwork-test");
    ItemRef baseResult = mock(ItemRef.class);
    when(baseResult.copy()).thenReturn(mock(ItemRef.class));
    CraftingMasterwork.ShiftBatch batch = new CraftingMasterwork.ShiftBatch(
        new CraftingMasterwork.ShiftBatchSpec(Id.minecraft("iron_leggings"), recipeKey, 5),
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

    assertThat(craftHandler.getAnnotation(Handle.class).priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(prepareHandler.getAnnotation(Handle.class).priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(craftedHandler.getAnnotation(Handle.class).priority()).isEqualTo(EventPriority.MONITOR);
  }
}
