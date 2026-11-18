package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingBackpacksTest {
  @TempDir
  Path dataDirectory;

  @BeforeEach
  void installPlatform() {
    TestCores.install(TestPlatform.builder(dataDirectory).build().platform());
  }

  @AfterEach
  void clearPlatform() {
    TestCores.clear();
  }

  // ------------------------------------------------------------- mode cycle

  @Test
  void aLoneBackpackInTheGridIsWhatCyclesTheMode() {
    ItemRef backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{null, backpack, null})).isTrue();
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{backpack})).isTrue();
  }

  @Test
  void aBackpackNextToAnythingElseDoesNotCycle() {
    ItemRef backpack = backpack("a", BackpackItem.Mode.SLOTS, null);
    ItemRef leather = plain(Id.minecraft("leather"), 1);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{backpack, leather})).isFalse();
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{leather})).isFalse();
  }

  @Test
  void twoBackpacksNeverCycle() {
    ItemRef first = backpack("a", BackpackItem.Mode.SLOTS, null);
    ItemRef second = backpack("b", BackpackItem.Mode.BUNDLE, null);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{first, second})).isFalse();
  }

  @Test
  void anEmptyGridNeverCycles() {
    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{null, null})).isFalse();
    assertThat(CraftingBackpacks.isLoneBackpack(null)).isFalse();
  }

  @Test
  void aStackedBackpackNeverCyclesSoOneInputCanOnlyEverYieldOneOutput() {
    ItemRef stacked = backpack("a", BackpackItem.Mode.SLOTS, null);
    stacked.setAmount(2);

    assertThat(CraftingBackpacks.isLoneBackpack(new ItemRef[]{stacked})).isFalse();
  }

  @Test
  void aBackpackIsRecognizedAnywhereInAnUnrelatedRecipeMatrix() {
    ItemRef backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.matrixHasBackpack(new ItemRef[]{null, plain(Id.minecraft("leather"), 1), backpack}))
        .isTrue();
    assertThat(CraftingBackpacks.matrixHasBackpack(new ItemRef[]{plain(Id.minecraft("leather"), 1)})).isFalse();
    assertThat(CraftingBackpacks.matrixHasBackpack(null)).isFalse();
  }

  @Test
  void theFirstBackpackInTheGridIsTheOneThatGetsCycled() {
    ItemRef backpack = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.firstBackpack(new ItemRef[]{null, backpack})).isSameAs(backpack);
    assertThat(CraftingBackpacks.firstBackpack(new ItemRef[]{plain(Id.minecraft("leather"), 1)})).isNull();
    assertThat(CraftingBackpacks.firstBackpack(null)).isNull();
  }

  @Test
  void onlyAnEmptyBackpackMayCycleItsMode() {
    ItemRef empty = backpack("a", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isEmptyBackpack(empty)).isTrue();
    assertThat(CraftingBackpacks.isEmptyBackpack(null)).isFalse();
    assertThat(CraftingBackpacks.isEmptyBackpack(plain(Id.minecraft("stone"), 1))).isFalse();
  }

  @Test
  void anUnreadableBackpackIsTreatedAsPackedRatherThanEmptied() {
    ItemRef broken = backpack("a", BackpackItem.Mode.SLOTS, new byte[]{99, 1, 2, 3});

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
    ItemRef backing = backpack("open", BackpackItem.Mode.SLOTS, null);
    ItemRef other = backpack("other", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.isBacking(backing, "open")).isTrue();
    assertThat(CraftingBackpacks.isBacking(other, "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(plain(Id.minecraft("stone"), 1), "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(null, "open")).isFalse();
    assertThat(CraftingBackpacks.isBacking(backing, null)).isFalse();
  }

  @Test
  void aClonedIdentityIsDetectedSoTheOpenedBackpackGetsARestamp() {
    ItemRef opened = backpack("same", BackpackItem.Mode.SLOTS, null);
    ItemRef clone = backpack("same", BackpackItem.Mode.SLOTS, null);
    ItemRef unrelated = backpack("different", BackpackItem.Mode.SLOTS, null);

    assertThat(CraftingBackpacks.duplicateIdentity(new ItemRef[]{opened, clone}, opened, "same")).isTrue();
    assertThat(CraftingBackpacks.duplicateIdentity(new ItemRef[]{opened, unrelated}, opened, "same")).isFalse();
    assertThat(CraftingBackpacks.duplicateIdentity(null, opened, "same")).isFalse();
    assertThat(CraftingBackpacks.duplicateIdentity(new ItemRef[]{opened}, opened, null)).isFalse();
  }

  // ---------------------------------------------------------------- helpers

  private static ItemRef plain(Id type, int amount) {
    return new FakeItem(type, amount);
  }

  private static ItemRef backpack(String id, BackpackItem.Mode mode, byte[] blob) {
    ItemRef stack = plain(Id.minecraft("bundle"), 1);
    stack.data().set(BackpackItem.io.dataKey(), DataBoundItem.GSON.toJson(BackpackItem.Data.of(id, mode, 9, 0)));
    if (blob != null) {
      stack.data().set(BackpackItem.contentsKey(), blob);
    }
    return stack;
  }
}
