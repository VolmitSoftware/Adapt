package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingCurseCleansingTest {
  @Test
  void sourceSelectionCleansesOnlyOneItemWithFirstSlotPrecedence() {
    assertThat(EnchantingCurseCleansing.selectSourceSlot(true, true)).isZero();
    assertThat(EnchantingCurseCleansing.selectSourceSlot(true, false)).isZero();
    assertThat(EnchantingCurseCleansing.selectSourceSlot(false, true)).isOne();
    assertThat(EnchantingCurseCleansing.selectSourceSlot(false, false)).isEqualTo(-1);
  }

  @Test
  void curseRemovalSelectionPreservesPositiveEntriesAndOtherItemState() {
    Map<String, Integer> enchantments = new LinkedHashMap<>();
    enchantments.put("sharpness", 5);
    enchantments.put("binding", 1);
    enchantments.put("mending", 1);
    enchantments.put("vanishing", 1);
    TestItemState source = new TestItemState("Blade", 417, List.of("Forged"), enchantments);
    TestItemState cleaned = source.copy();
    Set<String> knownCurses = Set.of("binding", "vanishing");

    EnchantingCurseCleansing.removePresent(
        knownCurses,
        cleaned.enchantments()::containsKey,
        cleaned.enchantments()::remove);

    assertThat(cleaned.name()).isEqualTo("Blade");
    assertThat(cleaned.damage()).isEqualTo(417);
    assertThat(cleaned.lore()).containsExactly("Forged");
    assertThat(cleaned.enchantments())
        .containsEntry("sharpness", 5)
        .containsEntry("mending", 1)
        .doesNotContainKeys("binding", "vanishing");
    assertThat(source.enchantments()).containsKeys("binding", "vanishing");
  }

  @Test
  void removalHelperIgnoresAbsentCurses() {
    List<String> removed = new ArrayList<>();

    EnchantingCurseCleansing.removePresent(
        List.of("binding", "vanishing"),
        "binding"::equals,
        removed::add);

    assertThat(removed).containsExactly("binding");
  }

  @Test
  void runtimePathClonesTheOriginalInputAndHandlesDirectAndStoredEnchantments() throws IOException {
    Path sourcePath = Path.of("src/main/java/art/arcane/adapt/content/adaptation/enchanting/EnchantingCurseCleansing.java");
    String source = Files.readString(sourcePath);

    assertThat(source).contains(
        "ItemStack cleaned = item.clone();",
        "directEnchantments::containsKey",
        "cleaned::removeEnchantment",
        "stored::hasStoredEnchant",
        "stored::removeStoredEnchant",
        "cleaned.setItemMeta(stored);");
    assertThat(source).doesNotContain("e.getCurrentItem()", "p.setLevel(", "p.setExp(");
  }

  @Test
  void cleanseRewardScalesPerCurseAndNeverBecomesNegative() {
    assertThat(EnchantingCurseCleansing.cleanseReward(30.0D, 2)).isEqualTo(60.0D);
    assertThat(EnchantingCurseCleansing.cleanseReward(-30.0D, 2)).isZero();
    assertThat(EnchantingCurseCleansing.cleanseReward(30.0D, -1)).isZero();
  }

  @Test
  void cleanseHandlerRunsBeforeOtherGrindstoneRewards() throws ReflectiveOperationException {
    Method method = EnchantingCurseCleansing.class.getMethod("on", InventoryClickEvent.class);
    EventHandler handler = method.getAnnotation(EventHandler.class);

    assertThat(handler).isNotNull();
    assertThat(handler.priority()).isEqualTo(EventPriority.LOWEST);
    assertThat(handler.ignoreCancelled()).isTrue();
  }

  private record TestItemState(String name, int damage, List<String> lore, Map<String, Integer> enchantments) {
    private TestItemState copy() {
      return new TestItemState(name, damage, List.copyOf(lore), new LinkedHashMap<>(enchantments));
    }
  }
}
