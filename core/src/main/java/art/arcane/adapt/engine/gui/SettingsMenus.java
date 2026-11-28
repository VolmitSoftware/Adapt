package art.arcane.adapt.engine.gui;

import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;

/** The footer's Settings control, the same item on every menu that has settings. */
public final class SettingsMenus {
  private static final Id SETTINGS = Id.minecraft("comparator");

  private SettingsMenus() {
  }

  public static GuiElement opener(String id, Runnable open) {
    return GuiTheme.element(id, SETTINGS, "gui", "navigation", "settings")
        .setName(TextFormat.WHITE + AdaptLanguage.text(GuiMessages.PACK_SETTINGS))
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick click) -> open.run());
  }
}
