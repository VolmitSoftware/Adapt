package art.arcane.adapt.content.adaptation.crafting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins for the polish pass: air right-clicks must open the backpack, and the
 * bundle-mode nav row must read as greyed-out dead space, never as items.
 */
class CraftingBackpacksPolishTest {
  private static String interactHandler;
  private static String navRegion;

  @BeforeAll
  static void readSource() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/crafting/CraftingBackpacks.java"));
    interactHandler = region(source, "public void on(PlayerInteractEvent", "private void openBackpack");
    navRegion = region(source, "private ItemStack navFiller()", "// ---------------------------------------------------------------- deposits");
  }

  private static String region(String source, String from, String to) {
    int start = source.indexOf(from);
    int end = source.indexOf(to);
    assertThat(start).as("region start: " + from).isNotNegative();
    assertThat(end).as("region end: " + to).isGreaterThan(start);
    return source.substring(start, end);
  }

  @Test
  void theInteractHandlerNeverConsultsIsCancelled() {
    // Bukkit reports RIGHT_CLICK_AIR as cancelled by default (useInteractedBlock
    // is DENY with no block), so gating on isCancelled() makes air opens
    // impossible - the handler must gate on prior item-use denial instead.
    assertThat(interactHandler).doesNotContain("isCancelled()");
    assertThat(interactHandler).contains("itemUseAlreadyDenied");
  }

  @Test
  void theNavRowShowsGreyFillerAndNeverABundleItem() {
    assertThat(navRegion).doesNotContain("Material.BUNDLE");
    assertThat(navRegion).contains("Material.GRAY_STAINED_GLASS_PANE");
  }
}
