package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.util.common.io.BukkitGson;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CraftingBackpacksTest extends AdaptTestBase {
  @BeforeEach
  void configurePluginIdentity() {
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  // ------------------------------------------------------------- mode cycle

  @Test
  void aLoneBackpackInTheGridIsWhatCyclesTheMode() {
    ItemStack backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{null, backpack, null})).isTrue();
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{backpack})).isTrue();
  }

  @Test
  void aBackpackNextToAnythingElseDoesNotCycle() {
    ItemStack backpack = backpack("a", BackpackItem.Mode.SLOTS, null);
    ItemStack leather = plain(Material.LEATHER, 1);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{backpack, leather})).isFalse();
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{leather})).isFalse();
  }

  @Test
  void twoBackpacksNeverCycle() {
    ItemStack first = backpack("a", BackpackItem.Mode.SLOTS, null);
    ItemStack second = backpack("b", BackpackItem.Mode.BUNDLE, null);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{first, second})).isFalse();
  }

  @Test
  void anEmptyGridNeverCycles() {
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{null, null})).isFalse();
    assertThat(CraftingBackpacks.isLoneBackpack(null)).isFalse();
  }

  @Test
  void aStackedBackpackNeverCyclesSoOneInputCanOnlyEverYieldOneOutput() {
    ItemStack stacked = backpack("a", BackpackItem.Mode.SLOTS, null);
    lenient().when(stacked.getAmount()).thenReturn(2);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemStack[]{stacked})).isFalse();
  }

  @Test
  void aBackpackIsRecognizedAnywhereInAnUnrelatedRecipeMatrix() {
    ItemStack backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.matrixHasBackpack(new ItemStack[]{null, plain(Material.LEATHER, 1), backpack}))
        .isTrue();
    assertThat(CraftingBackpacks.matrixHasBackpack(new ItemStack[]{plain(Material.LEATHER, 1)})).isFalse();
    assertThat(CraftingBackpacks.matrixHasBackpack(null)).isFalse();
  }

  @Test
  void theFirstBackpackInTheGridIsTheOneThatGetsCycled() {
    ItemStack backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.firstBackpack(new ItemStack[]{null, backpack})).isSameAs(backpack);
    assertThat(CraftingBackpacks.firstBackpack(new ItemStack[]{plain(Material.LEATHER, 1)})).isNull();
    assertThat(CraftingBackpacks.firstBackpack(null)).isNull();
  }

  @Test
  void onlyAnEmptyBackpackMayCycleItsMode() {
    ItemStack empty = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isEmptyBackpack(empty)).isTrue();
    assertThat(CraftingBackpacks.isEmptyBackpack(null)).isFalse();
    assertThat(CraftingBackpacks.isEmptyBackpack(plain(Material.STONE, 1))).isFalse();
  }

  @Test
  void anUnreadableBackpackIsTreatedAsPackedRatherThanEmptied() {
    ItemStack broken = backpack("a", BackpackItem.Mode.SLOTS, new byte[]{99, 1, 2, 3});

    assertThat(CraftingBackpacks.isEmptyBackpack(broken)).isFalse();
  }

  @Test
  void cyclingKeepsTheIdentityAndOnlyFlipsTheMode() {
    BackpackItem.Data slots = BackpackItem.Data.of("keep-me", BackpackItem.Mode.SLOTS, 9, 0);

    BackpackItem.Data toBundle = CraftingBackpacks.cycledData(slots, 27);
    BackpackItem.Data backToSlots = CraftingBackpacks.cycledData(toBundle, 27);

    assertThat(toBundle.getId()).isEqualTo("keep-me");
    assertThat(toBundle.getMode()).isEqualTo(BackpackItem.Mode.BUNDLE.name());
    assertThat(toBundle.getCapacity()).isEqualTo(27);
    assertThat(toBundle.getUsed()).isZero();
    assertThat(backToSlots.getId()).isEqualTo("keep-me");
    assertThat(backToSlots.getMode()).isEqualTo(BackpackItem.Mode.SLOTS.name());
    assertThat(CraftingBackpacks.cycledData(null, 27)).isNull();
  }

  @Test
  void cyclingAdoptsTheCurrentConfiguredCapacity() {
    BackpackItem.Data legacy = BackpackItem.Data.of("old", BackpackItem.Mode.BUNDLE, 54, 0);

    assertThat(CraftingBackpacks.cycledData(legacy, 9).getCapacity()).isEqualTo(9);
  }

  // ------------------------------------------------- vanilla bundle suppression

  @Test
  void aStackMeetingABackpackIsTheBundlingGestureAndIsSuppressed() {
    assertThat(CraftingBackpacks.suppressesVanillaBundling(false, true, false, false, false)).isTrue();
    assertThat(CraftingBackpacks.suppressesVanillaBundling(true, false, false, false, true)).isTrue();
  }

  @Test
  void rightClickingABackpackWithAnEmptyCursorIsSuppressed() {
    assertThat(CraftingBackpacks.suppressesVanillaBundling(false, true, true, false, true)).isTrue();
    assertThat(CraftingBackpacks.suppressesVanillaBundling(false, true, true, false, false)).isFalse();
  }

  @Test
  void ordinarilyMovingTheBackpackAroundIsNotSuppressed() {
    assertThat(CraftingBackpacks.suppressesVanillaBundling(false, true, true, false, false)).isFalse();
    assertThat(CraftingBackpacks.suppressesVanillaBundling(true, false, false, true, false)).isFalse();
    assertThat(CraftingBackpacks.suppressesVanillaBundling(false, false, false, false, true)).isFalse();
  }

  // -------------------------------------------------------------- identity

  @Test
  void theOpenBackpackIsRecognizedByItsStampedIdentity() {
    ItemStack backing = backpack("open", BackpackItem.Mode.SLOTS, null);
    ItemStack other = backpack("other", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isBacking(backing, "open")).isTrue();
    assertThat(CraftingBackpacks.isBacking(other, "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(plain(Material.STONE, 1), "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(null, "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(backing, null)).isFalse();
  }

  @Test
  void aClonedIdentityIsDetectedSoTheOpenedBackpackGetsARestamp() {
    ItemStack opened = backpack("same", BackpackItem.Mode.SLOTS, null);
    ItemStack clone = backpack("same", BackpackItem.Mode.SLOTS, null);
    ItemStack unrelated = backpack("different", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.duplicateIdentity(new ItemStack[]{opened, clone}, opened, "same")).isTrue();
    assertThat(CraftingBackpacks.duplicateIdentity(new ItemStack[]{opened, unrelated}, opened, "same")).isFalse();
    assertThat(CraftingBackpacks.duplicateIdentity(null, opened, "same")).isFalse();
    assertThat(CraftingBackpacks.duplicateIdentity(new ItemStack[]{opened}, opened, null)).isFalse();
  }

  // ---------------------------------------------------------------- helpers

  private static ItemStack plain(Material type, int amount) {
    ItemStack stack = mock(ItemStack.class);
    lenient().when(stack.getType()).thenReturn(type);
    lenient().when(stack.getAmount()).thenReturn(amount);
    return stack;
  }

  private static ItemStack backpack(String id, BackpackItem.Mode mode, byte[] blob) {
    ItemStack stack = plain(Material.BUNDLE, 1);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    lenient().when(meta.getPersistentDataContainer()).thenReturn(data);
    lenient().when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(true);
    lenient().when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
        .thenReturn(BukkitGson.gson.toJson(BackpackItem.Data.of(id, mode, 9, 0)));
    lenient().when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BYTE_ARRAY)))
        .thenReturn(blob);
    return stack;
  }
}
