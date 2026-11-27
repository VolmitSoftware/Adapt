package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class PreferenceGroupGui {
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");

  private final Adaptation<?> adaptation;
  private final PreferenceToggleGroup group;
  private final GuiWindow window;
  private final int adaptationPage;
  private final Layout layout;
  private List<PlayerPreference<?>> visiblePreferences = List.of();

  private PreferenceGroupGui(Adaptation<?> adaptation, PreferenceToggleGroup group, GuiWindow window, int adaptationPage) {
    this.adaptation = adaptation;
    this.group = group;
    this.window = window;
    this.adaptationPage = adaptationPage;
    layout = Layout.of(group.entries().size(), AdaptConfig.get().isGuiBackButton());
  }

  static void open(Adaptation<?> adaptation, PreferenceToggleGroup group, PlayerRef player, int adaptationPage) {
    if (!Tasks.isPrimaryThread()) {
      Tasks.runEntity(player, () -> open(adaptation, group, player, adaptationPage));
      return;
    }
    if (AdaptCore.get().getAdaptServer().getPlayer(player) == null) {
      return;
    }
    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "skill/" + SkillHost.lineKey(adaptation.getSkill()) + "|" + adaptation.getId() + "|" + group.id());
    populate(adaptation, group, window, adaptationPage);
    window.setTitle(SkillHost.legacyColor(adaptation.getSkill().getColor()) + group.label().resolve());
    window.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onClosed(adaptation, player, adaptationPage)));
    window.open();
  }

  static void populate(Adaptation<?> adaptation, PreferenceToggleGroup group, GuiWindow window, int adaptationPage) {
    PreferenceGroupGui gui = new PreferenceGroupGui(adaptation, group, window, adaptationPage);
    window.setViewportHeight(gui.layout.rows());
    window.setLayout(MenuLayout.FORM);
    gui.render(false);
  }

  private void render(boolean update) {
    AdaptPlayer player = AdaptCore.get().getAdaptServer().getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    visiblePreferences = visiblePreferences(player);
    List<PreferenceToggleGroup.Entry> entries = group.entries();
    for (int index = 0; index < entries.size(); index++) {
      PreferenceToggleGroup.Entry entry = entries.get(index);
      Slot slot = layout.slot(index);
      GuiElement element = visiblePreferences.contains(entry.preference()) ? control(entry, player, slot) : null;
      if (update || element != null) {
        window.setElement(slot.position(), slot.row(), element);
      }
    }
    if (!update && layout.backButton()) {
      window.setElement(0, layout.rows() - 1, GuiTheme.element("back", ARROW, "gui", "navigation", "back")
          .setName(C.RESET + "" + C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
          .onLeftClick((MenuClick clicked) -> navigateBack()));
    }
    if (update) {
      window.update();
    }
  }

  private GuiElement control(PreferenceToggleGroup.Entry entry, AdaptPlayer player, Slot slot) {
    PlayerPreference<CommonPreferences.Toggle> preference = entry.preference();
    int level = AdaptationHost.of(adaptation).level(player);
    PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
    CommonPreferences.Toggle value = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
    List<PlayerPreference.Choice<CommonPreferences.Toggle>> allowed = PlayerPreferences.allowedChoices(adaptation, preference, level);
    boolean blocked = adaptation.isPlayerPreferenceBlocked(preference);
    boolean locked = blocked || !policy.playerEditable || policy.allowedValues.size() == 1 || allowed.isEmpty();
    GuiElement element = GuiTheme.element("preference-" + preference.id(), locked ? GRAY_PANE : entry.icon(),
            "gui", "preferences", "adaptation", adaptation.getId().toString(), preference.id(),
            locked ? "locked" : value.name().toLowerCase(Locale.ROOT))
        .setName(PlayerPreferences.shown(C.WHITE.toString(),
            PlayerPreferences.label(PlayerPreferences.ownerKey(adaptation), preference)))
        .setEnchanted(!locked && value == CommonPreferences.Toggle.ON);
    List<String> description = new ArrayList<>(1);
    if (blocked) {
      element.addLore(AdaptLanguage.textStyled(C.GOLD.toString(), GuiMessages.PREFERENCE_SERVER_BLOCKED))
          .setValue(AdaptLanguage.text(GuiMessages.PREFERENCE_SERVER_BLOCKED));
    } else {
      String current = PlayerPreferences.shown(
          PlayerPreferences.label(PlayerPreferences.ownerKey(adaptation), preference, value));
      element.addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_CURRENT,
          trusted("value", current)));
      if (locked || level < 1) {
        String status = locked
            ? AdaptLanguage.textStyled(C.GOLD.toString(), GuiMessages.PREFERENCE_SERVER_CONTROLLED)
            : AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_LEARN_FIRST);
        element.addLore(status);
        description.add(status);
      } else if (allowed.size() > 1) {
        element.addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_CYCLE));
      }
      if (locked) {
        element.setValue(current);
      } else {
        element.setHint(MenuHint.toggle(value == CommonPreferences.Toggle.ON));
      }
    }
    element.setRole(locked ? MenuRole.INFO : MenuRole.SETTING).setDescription(description);
    return element.onLeftClick((MenuClick clicked) -> onOwner(() -> toggle(entry, slot)))
        .onRightClick((MenuClick clicked) -> onOwner(() -> toggle(entry, slot)));
  }

  private void toggle(PreferenceToggleGroup.Entry entry, Slot slot) {
    AdaptPlayer player = AdaptCore.get().getAdaptServer().getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    PlayerPreference<CommonPreferences.Toggle> preference = entry.preference();
    if (!adaptation.isPlayerPreferenceVisible(PlayerApi.profile(player), preference)) {
      render(true);
      PreferenceGuiSupport.feedback(adaptation, window.getViewer(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7F);
      return;
    }
    int level = AdaptationHost.of(adaptation).level(player);
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
        changed ? Sounds.UI_BUTTON_CLICK : Sounds.BLOCK_NOTE_BLOCK_BASS, changed ? 1.2F : 0.7F);
  }

  private List<PlayerPreference<?>> visiblePreferences(AdaptPlayer player) {
    List<PlayerPreference<?>> preferences = new ArrayList<>(group.entries().size());
    for (PreferenceToggleGroup.Entry entry : group.entries()) {
      if (adaptation.isPlayerPreferenceVisible(PlayerApi.profile(player), entry.preference())) {
        preferences.add(entry.preference());
      }
    }
    return List.copyOf(preferences);
  }

  private void navigateBack() {
    PlayerRef viewer = window.getViewer();
    AdaptationGuiSupport.playCloseSound(viewer);
    AdaptationGuiSupport.openGui(adaptation, viewer, adaptationPage);
  }

  private void onOwner(Runnable action) {
    PlayerRef viewer = window.getViewer();
    if (!Tasks.isOwnedByCurrentRegion(viewer)) {
      Tasks.runEntity(viewer, () -> onOwner(action));
      return;
    }
    if (!AdaptConfig.get().isPlayerSettingsEnabled() || !PreferenceGuiSupport.acceptsClick(adaptation, window)) {
      return;
    }
    action.run();
  }

  private static void onClosed(Adaptation<?> adaptation, PlayerRef player, int adaptationPage) {
    if (GuiCloseSuppression.consume(player)) {
      return;
    }
    AdaptationGuiSupport.playCloseSound(player);
    if (AdaptConfig.get().isEscClosesAllGuis()) {
      return;
    }
    Tasks.runEntity(player, () -> {
      if (GuiCloseSuppression.windowFree(player)) {
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
