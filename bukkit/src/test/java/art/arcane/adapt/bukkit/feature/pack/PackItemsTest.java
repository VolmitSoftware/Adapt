package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackItemsTest {
  private static final SlotLook FRAMED = SlotLook.of(Id.adapt("ui/card"), "adapt:tome", 0);
  private static final List<String> NEVER_USED = List.of("enchantable", "enchantments", "repair_cost", "use_effects", "attribute_modifiers",
      "rarity", "break_sound", "swing_animation", "attack_animation", "interact_animation", "item_name");

  @Test
  void aDrawnSlotDropsEveryDefaultItNeverUses() {
    MenuIcon icon = MenuIcon.of(PackManifest.CARRIER, "Back", List.of());

    assertThat(PackItems.unused(icon, FRAMED)).containsAll(NEVER_USED).contains("lore", "tooltip_display", "max_stack_size")
        .doesNotContain("item_model", "custom_name", "tooltip_style", "custom_model_data");
  }

  @Test
  void aDrawnSlotKeepsLoreItHasAStackCountItShowsAndAHiddenTooltip() {
    MenuIcon icon = MenuIcon.of(PackManifest.CARRIER, "3 Knowledge", List.of("Agility: 3")).withAmount(3);

    assertThat(PackItems.unused(icon, FRAMED)).containsAll(NEVER_USED).doesNotContain("lore", "max_stack_size");
    assertThat(PackItems.unused(icon, SlotLook.hidden(Id.adapt("ui/blank")))).doesNotContain("tooltip_display");
  }

  @Test
  void framedTooltipTextStartsInTheTextInkAndPlainTooltipsStayAsTheyAre() {
    String ink = "§x§2§e§2§2§1§6";

    assertThat(PackItems.text("0/0 Power", FRAMED)).isEqualTo(ink + "0/0 Power");
    assertThat(PackItems.text("§x§2§3§4§0§6§eBlue§r rest", FRAMED)).isEqualTo("§x§2§3§4§0§6§eBlue" + ink + " rest");
    assertThat(PackItems.text("0/0 Power", null)).isEqualTo("0/0 Power");
    assertThat(PackItems.text("Hidden", SlotLook.hidden(Id.adapt("ui/blank")))).isEqualTo("Hidden");
  }

  @Test
  void realItemsAndUndrawnSlotsKeepTheirComponents() {
    assertThat(PackItems.unused(MenuIcon.of(Id.minecraft("string"), "String", List.of()), FRAMED)).isEmpty();
    assertThat(PackItems.unused(MenuIcon.of(PackManifest.CARRIER, "Plain", List.of()), null)).isEmpty();
  }
}
