package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class PreferenceGroupGui {
  private final Adaptation<?> adaptation;
  private final PreferenceToggleGroup group;
  private final UIWindow window;
  private final int adaptationPage;
  private final Layout layout;
  private List<PlayerPreference<?>> visiblePreferences = List.of();

  private PreferenceGroupGui(Adaptation<?> adaptation, PreferenceToggleGroup group, UIWindow window, int adaptationPage) {
    this.adaptation = adaptation;
    this.group = group;
    this.window = window;
    this.adaptationPage = adaptationPage;
    layout = Layout.of(group.entries().size(), AdaptConfig.get().isGuiBackButton());
  }

  static void open(Adaptation<?> adaptation, PreferenceToggleGroup group, Player player, int adaptationPage) {
    if (!J.isPrimaryThread()) {
      J.runEntity(player, () -> open(adaptation, group, player, adaptationPage));
      return;
    }
    if (adaptation.getPlayer(player) == null) {
      return;
    }
    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "skill/" + adaptation.getSkill().getName() + "/" + adaptation.getName() + "/" + group.id());
    populate(adaptation, group, window, adaptationPage);
    window.setTitle(adaptation.getSkill().getColor() + AdaptLanguage.text(group.label()));
    window.onClosed(closed -> J.runEntity(player, () -> onClosed(adaptation, player, window, adaptationPage)));
    window.open();
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
  }

  static void populate(Adaptation<?> adaptation, PreferenceToggleGroup group, UIWindow window, int adaptationPage) {
    PreferenceGroupGui gui = new PreferenceGroupGui(adaptation, group, window, adaptationPage);
    window.setViewportHeight(gui.layout.rows());
    gui.render(false);
  }

  private void render(boolean update) {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    visiblePreferences = visiblePreferences(player);
    List<PreferenceToggleGroup.Entry> entries = group.entries();
    for (int index = 0; index < entries.size(); index++) {
      PreferenceToggleGroup.Entry entry = entries.get(index);
      Slot slot = layout.slot(index);
      Element element = visiblePreferences.contains(entry.preference()) ? control(entry, player, slot) : null;
      if (update) {
        window.updateElement(slot.position(), slot.row(), element);
      } else if (element != null) {
        window.setElement(slot.position(), slot.row(), element);
      }
    }
    if (!update && layout.backButton()) {
      window.setElement(0, layout.rows() - 1, GuiTheme.element("back", Material.ARROW, "gui", "navigation", "back")
          .setName(C.RESET + "" + C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
          .onLeftClick(clicked -> navigateBack()));
    }
  }

  private Element control(PreferenceToggleGroup.Entry entry, AdaptPlayer player, Slot slot) {
    PlayerPreference<CommonPreferences.Toggle> preference = entry.preference();
    int level = adaptation.getLevel(player);
    PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
    CommonPreferences.Toggle value = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
    List<PlayerPreference.Choice<CommonPreferences.Toggle>> allowed = PlayerPreferences.allowedChoices(adaptation, preference, level);
    boolean blocked = adaptation.isPlayerPreferenceBlocked(preference);
    boolean locked = blocked || !policy.playerEditable || policy.allowedValues.size() == 1 || allowed.isEmpty();
    Element element = GuiTheme.element("preference-" + preference.id(), locked ? Material.GRAY_STAINED_GLASS_PANE : entry.icon(),
            "gui", "preferences", "adaptation", adaptation.getName(), preference.id(),
            locked ? "locked" : value.name().toLowerCase(Locale.ROOT))
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), preference.label()))
        .setEnchanted(!locked && value == CommonPreferences.Toggle.ON);
    if (blocked) {
      element.addLore(AdaptLanguage.textStyled(C.GOLD.toString(), GuiMessages.PREFERENCE_SERVER_BLOCKED));
    } else {
      element.addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_CURRENT,
          trusted("value", AdaptLanguage.text(preference.choice(value).label()))));
      if (locked) {
        element.addLore(AdaptLanguage.textStyled(C.GOLD.toString(), GuiMessages.PREFERENCE_SERVER_CONTROLLED));
      } else if (level < 1) {
        element.addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_LEARN_FIRST));
      } else if (allowed.size() > 1) {
        element.addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_CYCLE));
      }
    }
    return element.onLeftClick(clicked -> onOwner(() -> toggle(entry, slot)))
        .onRightClick(clicked -> onOwner(() -> toggle(entry, slot)));
  }

  private void toggle(PreferenceToggleGroup.Entry entry, Slot slot) {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    PlayerPreference<CommonPreferences.Toggle> preference = entry.preference();
    if (!adaptation.isPlayerPreferenceVisible(player, preference)) {
      render(true);
      PreferenceGuiSupport.feedback(adaptation, window.getViewer(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F);
      return;
    }
    int level = adaptation.getLevel(player);
    boolean changed = false;
    if (level > 0 && !adaptation.isPlayerPreferenceBlocked(preference)
        && PlayerPreferences.policy(adaptation, preference).playerEditable
        && PlayerPreferences.allowedChoices(adaptation, preference, level).size() > 1) {
      CommonPreferences.Toggle current = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
      changed = PlayerPreferences.set(adaptation, player, preference,
          current == CommonPreferences.Toggle.ON ? CommonPreferences.Toggle.OFF : CommonPreferences.Toggle.ON);
    }
    if (!visiblePreferences.equals(visiblePreferences(player))) {
      render(true);
    } else {
      window.updateElement(slot.position(), slot.row(), control(entry, player, slot));
    }
    PreferenceGuiSupport.feedback(adaptation, window.getViewer(),
        changed ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, changed ? 1.2F : 0.7F);
  }

  private List<PlayerPreference<?>> visiblePreferences(AdaptPlayer player) {
    List<PlayerPreference<?>> preferences = new ArrayList<>(group.entries().size());
    for (PreferenceToggleGroup.Entry entry : group.entries()) {
      if (adaptation.isPlayerPreferenceVisible(player, entry.preference())) {
        preferences.add(entry.preference());
      }
    }
    return List.copyOf(preferences);
  }

  private void navigateBack() {
    Player viewer = window.getViewer();
    AdaptationGuiSupport.playCloseSound(viewer);
    AdaptationGuiSupport.openGui(adaptation, viewer, adaptationPage);
  }

  private void onOwner(Runnable action) {
    Player viewer = window.getViewer();
    if (!J.isOwnedByCurrentRegion(viewer)) {
      J.runEntity(viewer, () -> onOwner(action));
      return;
    }
    if (!AdaptConfig.get().isPlayerSettingsEnabled() || !PreferenceGuiSupport.acceptsClick(adaptation, window)) {
      return;
    }
    action.run();
  }

  private static void onClosed(Adaptation<?> adaptation, Player player, UIWindow window, int adaptationPage) {
    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window);
    if (GuiCloseSuppression.consume(player)) {
      return;
    }
    AdaptationGuiSupport.playCloseSound(player);
    if (AdaptConfig.get().isEscClosesAllGuis()) {
      return;
    }
    J.runEntity(player, () -> {
      if (player.isOnline() && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
        AdaptationGuiSupport.openGui(adaptation, player, adaptationPage);
      }
    }, 1);
  }

  private record Slot(int position, int row) {
  }

  private record Layout(int entries, int contentRows, int perRow, boolean backButton) {
    static Layout of(int entries, boolean backButton) {
      int contentRows = Math.max(1, (entries + GuiLayout.WIDTH - 1) / GuiLayout.WIDTH);
      int perRow = Math.max(1, (entries + contentRows - 1) / contentRows);
      return new Layout(entries, contentRows, perRow, backButton);
    }

    int rows() {
      return contentRows + (backButton ? 1 : 0);
    }

    Slot slot(int index) {
      int row = index / perRow;
      int start = row * perRow;
      return new Slot(GuiLayout.centeredPosition(index - start, Math.min(perRow, entries - start)), row);
    }
  }
}
