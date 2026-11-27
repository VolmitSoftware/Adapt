package art.arcane.adapt.pack.menu;

import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Generated long-tail menus of every shape keep their slots, handlers, hints and text through the skin. */
class LedgerSkinTest {
  private static final String[] TARGETS = {"back", "next", "previous", "first", "last", "page-info", "golem", ""};
  private final PackResources resources = PackSamples.resources();

  @Test
  void everyMenuShapeKeepsItsButtonsAndGainsALook() {
    Random random = new Random(7L);
    for (int round = 0; round < 300; round++) {
      int rows = 1 + random.nextInt(6);
      List<Integer> fired = new ArrayList<>();
      ChestMenu.Builder builder = ChestMenu.builder("Menu " + round, rows);
      for (int slot = 0; slot < rows * 9; slot++) {
        int kind = random.nextInt(5);
        int index = slot;
        MenuIcon icon = MenuIcon.of(Id.minecraft("stone"), kind == 0 ? "" : "Entry " + slot, kind == 0 ? List.of() : List.of("lore"));
        MenuButton button = switch (kind) {
          case 0, 1 -> MenuButton.display(icon);
          case 2 -> new MenuButton(icon, (MenuClick click) -> fired.add(index));
          case 3 -> new MenuButton(icon, (MenuClick click) -> fired.add(index))
              .withHint(MenuHint.navigation(TARGETS[random.nextInt(TARGETS.length)]));
          default -> new MenuButton(icon, (MenuClick click) -> fired.add(index))
              .withHint(MenuHint.text("v", (MenuClick context, String text) -> { }));
        };
        if (random.nextInt(4) > 0) {
          builder.set(slot, button);
        }
      }
      ChestMenu menu = builder.freePlayerInventory(random.nextBoolean()).build();
      LedgerSkin.Skinned skinned = LedgerSkin.skin(menu, resources, null, (MenuSession session) -> { });
      PackSurface surface = skinned.surface();

      assertThat(skinned.menu().rows()).isEqualTo(rows);
      assertThat(skinned.menu().buttons().keySet()).isEqualTo(menu.buttons().keySet());
      assertThat(surface.title()).isNotEmpty();
      assertThat(surface.lower()).isEmpty();
      for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
        MenuButton skinnedButton = skinned.menu().button(entry.getKey());
        assertThat(skinnedButton.icon().name()).isEqualTo(entry.getValue().icon().name());
        assertThat(skinnedButton.icon().lore()).isEqualTo(entry.getValue().icon().lore());
        assertThat(skinnedButton.hint()).isSameAs(entry.getValue().hint());
        assertThat(skinnedButton.clickable()).isEqualTo(entry.getValue().clickable());
        assertThat(surface.look(PackSlot.top(entry.getKey()))).isNotNull();
        if (skinnedButton.clickable()) {
          skinnedButton.action().click(new MenuClick(null, null, entry.getKey(), ClickKind.LEFT));
        }
      }
      long clickable = menu.buttons().values().stream().filter(MenuButton::clickable).count();
      assertThat(fired).hasSize((int) clickable);
    }
  }

  @Test
  void decorationIsBlankUnhintedAndInert() {
    MenuIcon blank = MenuIcon.of(Id.minecraft("gray_stained_glass_pane"), "§7 ", List.of(" "));
    assertThat(LedgerSkin.decoration(MenuButton.display(blank))).isTrue();
    assertThat(LedgerSkin.decoration(new MenuButton(blank, (MenuClick click) -> { }))).isFalse();
    assertThat(LedgerSkin.decoration(MenuButton.display(blank).withHint(MenuHint.toggle(true)))).isFalse();
    assertThat(LedgerSkin.decoration(MenuButton.display(MenuIcon.of(Id.minecraft("stone"), "Info", List.of())))).isFalse();
  }

  @Test
  void chromeComesFromNavigationHints() {
    MenuIcon arrow = MenuIcon.of(Id.minecraft("arrow"), "Next", List.of());
    assertThat(LedgerSkin.chrome(MenuButton.display(arrow).withHint(MenuHint.navigation("NEXT")))).isEqualTo("next");
    assertThat(LedgerSkin.chrome(MenuButton.display(arrow).withHint(MenuHint.navigation("golems")))).isNull();
    assertThat(LedgerSkin.chrome(MenuButton.display(arrow))).isNull();
  }

  @Test
  void theHeadingJoinsTheBreadcrumbAndTitle() {
    ChestMenu menu = ChestMenu.builder("Leaf", 1).breadcrumb(List.of("Root", "Branch")).build();
    assertThat(LedgerSkin.crumbs(menu)).containsExactly("Root", "Branch", "Leaf");
  }
}
