package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class PreferenceGuiSupport {
  private static final Id MILK_BUCKET = Id.minecraft("milk_bucket");
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");

  private final Adaptation<?> adaptation;
  private final GuiWindow window;
  private final int row;
  private final int adaptationPage;
  private List<PlayerPreference<?>> visiblePreferences = List.of();
  private int page;

  private PreferenceGuiSupport(Adaptation<?> adaptation, Placement placement) {
    this.adaptation = adaptation;
    window = placement.window();
    row = placement.row();
    adaptationPage = placement.adaptationPage();
  }

  static void populate(Adaptation<?> adaptation, GuiWindow window, int row, int adaptationPage) {
    new PreferenceGuiSupport(adaptation, new Placement(window, row, adaptationPage)).render(false);
  }

  static boolean acceptsClick(Adaptation<?> adaptation, GuiWindow window) {
    PlayerRef viewer = window.getViewer();
    return viewer.isOnline() && window.isVisible()
        && AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(adaptation.getSkill().getName()) == adaptation.getSkill()
        && adaptation.isEnabled() && adaptation.getSkill().isEnabled()
        && Skill.owns(adaptation.getSkill(), adaptation)
        && adaptation.getSkill().hasUsePermission(viewer, adaptation.getSkill())
        && adaptation.hasUsePermission(viewer, adaptation);
  }

  static void feedback(Adaptation<?> adaptation, PlayerRef viewer, Id sound, float pitch) {
    AdaptPlayer player = adaptation.getPlayer(viewer);
    if (player == null || !player.getData().isEffectsEnabled() || !adaptation.areSoundsEnabled()) {
      return;
    }
    SoundPlayer.of(viewer).play(viewer.location(), sound, 0.3F, pitch);
  }

  private void render(boolean update) {
    if (!AdaptConfig.get().isPlayerSettingsEnabled()) {
      if (update) {
        for (int slot = 0; slot < GuiLayout.WIDTH; slot++) {
          window.setElement(slot - 4, row, null);
        }
        window.update();
      }
      return;
    }
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    List<Entry> entries = entries();
    visiblePreferences = visiblePreferences(player);
    boolean paged = entries.size() > 8;
    int capacity = paged ? 6 : 8;
    page = GuiLayout.clampPage(page, (entries.size() + capacity - 1) / capacity);
    int start = page * capacity;
    int count = Math.min(capacity, entries.size() - start);
    GuiElement[] controls = new GuiElement[GuiLayout.WIDTH];
    for (int index = 0; index < count; index++) {
      Entry entry = entries.get(start + index);
      int position = paged ? index - 3 : GuiLayout.centeredPosition(index, count + 1);
      controls[position + 4] = switch (entry) {
        case PreferenceEntry preference -> visiblePreferences.contains(preference.preference())
            ? control(preference.preference(), player, position) : null;
        case GroupEntry group -> groupVisible(group.group()) ? groupControl(group.group(), player) : null;
      };
    }
    int resetPosition = paged ? 4 : GuiLayout.centeredPosition(count, count + 1);
    controls[resetPosition + 4] = GuiTheme.element("preference-reset", MILK_BUCKET, "gui", "preferences", "reset")
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_RESET))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_RESET_DESCRIPTION))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick click) -> onOwner(this::reset));
    if (paged) {
      controls[0] = GuiTheme.element("preference-previous", page > 0 ? ARROW : GRAY_PANE,
          "gui", "navigation", page > 0 ? "previous" : "previous-disabled")
          .setName(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREVIOUS))
          .setHint(null)
          .setRole(page > 0 ? MenuRole.ACTION : MenuRole.DECORATION)
          .onLeftClick((MenuClick click) -> onOwner(() -> changePage(-1)));
      controls[7] = GuiTheme.element("preference-next", start + count < entries.size() ? ARROW : GRAY_PANE,
          "gui", "navigation", start + count < entries.size() ? "next" : "next-disabled")
          .setName(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.NEXT))
          .setHint(null)
          .setRole(start + count < entries.size() ? MenuRole.ACTION : MenuRole.DECORATION)
          .onLeftClick((MenuClick click) -> onOwner(() -> changePage(1)));
    }
    for (int slot = 0; slot < controls.length; slot++) {
      if (update || controls[slot] != null) {
        window.setElement(slot - 4, row, controls[slot]);
      }
    }
    if (update) {
      window.update();
    }
  }

  private List<Entry> entries() {
    List<PreferenceToggleGroup> groups = adaptation.getPlayerPreferenceGroups();
    List<PlayerPreference<?>> preferences = adaptation.getPlayerPreferences();
    List<Entry> entries = new ArrayList<>(preferences.size());
    List<PreferenceToggleGroup> placed = new ArrayList<>(groups.size());
    for (PlayerPreference<?> preference : preferences) {
      PreferenceToggleGroup group = groupOf(groups, preference);
      if (group == null) {
        entries.add(new PreferenceEntry(preference));
      } else if (!placed.contains(group)) {
        placed.add(group);
        entries.add(new GroupEntry(group));
      }
    }
    return List.copyOf(entries);
  }

  private static PreferenceToggleGroup groupOf(List<PreferenceToggleGroup> groups, PlayerPreference<?> preference) {
    for (PreferenceToggleGroup group : groups) {
      if (group.contains(preference)) {
        return group;
      }
    }
    return null;
  }

  private boolean groupVisible(PreferenceToggleGroup group) {
    for (PreferenceToggleGroup.Entry entry : group.entries()) {
      if (visiblePreferences.contains(entry.preference())) {
        return true;
      }
    }
    return false;
  }

  private <E extends Enum<E>> GuiElement control(PlayerPreference<E> preference, AdaptPlayer player, int position) {
    int level = adaptation.getLevel(player);
    PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
    E value = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
    PlayerPreference.Choice<E> selected = preference.choice(value);
    List<PlayerPreference.Choice<E>> allowed = PlayerPreferences.allowedChoices(adaptation, preference, level);
    boolean serverControlled = !policy.playerEditable || policy.allowedValues.size() == 1;
    String current = AdaptLanguage.text(selected.label());
    List<String> description = new ArrayList<>();
    GuiElement element = GuiTheme.element("preference-" + preference.id(), serverControlled || allowed.isEmpty()
            ? GRAY_PANE : selected.shownIcon(),
            "gui", "preferences", "adaptation", adaptation.getName(), preference.id(),
            serverControlled || allowed.isEmpty() ? "locked" : value.name().toLowerCase(Locale.ROOT))
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), preference.label()))
        .addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_CURRENT,
            trusted("value", current)));
    if (serverControlled || level < 1) {
      String status = serverControlled
          ? AdaptLanguage.textStyled(C.GOLD.toString(), GuiMessages.PREFERENCE_SERVER_CONTROLLED)
          : AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_LEARN_FIRST);
      element.addLore(status);
      description.add(status);
    } else if (allowed.size() > 1) {
      element.addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_CYCLE));
    }
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      String line = null;
      if (choice.minimumLevel() > level) {
        line = AdaptLanguage.textStyled(C.DARK_GRAY.toString(), GuiMessages.PREFERENCE_LEVEL_REQUIRED,
            trusted("value", AdaptLanguage.text(choice.label())), trusted("level", choice.minimumLevel()));
      } else if (!allowed.contains(choice)) {
        line = AdaptLanguage.textStyled(C.DARK_GRAY.toString(), GuiMessages.PREFERENCE_SERVER_DISABLED,
            trusted("value", AdaptLanguage.text(choice.label())));
      }
      if (line != null) {
        element.addLore(line);
        description.add(line);
      }
    }
    if (value instanceof CommonPreferences.Toggle toggle && !serverControlled && !allowed.isEmpty()) {
      element.setHint(MenuHint.toggle(toggle == CommonPreferences.Toggle.ON));
    } else {
      element.setValue(current);
      if (!serverControlled && !allowed.isEmpty()) {
        element.setHint(MenuHint.choice(current, choiceLabels(allowed), null));
      }
    }
    element.setRole(element.getHint() == null ? MenuRole.INFO : MenuRole.SETTING).setDescription(description);
    return element.onLeftClick((MenuClick clicked) -> onOwner(() -> cycle(preference, position, 1)))
        .onRightClick((MenuClick clicked) -> onOwner(() -> cycle(preference, position, -1)));
  }

  private static <E extends Enum<E>> List<String> choiceLabels(List<PlayerPreference.Choice<E>> choices) {
    List<String> labels = new ArrayList<>(choices.size());
    for (PlayerPreference.Choice<E> choice : choices) {
      labels.add(AdaptLanguage.text(choice.label()));
    }
    return labels;
  }

  private GuiElement groupControl(PreferenceToggleGroup group, AdaptPlayer player) {
    int level = adaptation.getLevel(player);
    int enabled = 0;
    int total = 0;
    for (PreferenceToggleGroup.Entry entry : group.entries()) {
      if (!visiblePreferences.contains(entry.preference())) {
        continue;
      }
      total++;
      if (!adaptation.isPlayerPreferenceBlocked(entry.preference())
          && PlayerPreferences.resolve(adaptation, player.getData(), level, entry.preference()) == CommonPreferences.Toggle.ON) {
        enabled++;
      }
    }
    return GuiTheme.element("preference-group-" + group.id(), group.icon(),
            "gui", "preferences", "adaptation", adaptation.getName(), group.id(), "group")
        .setName(AdaptLanguage.textStyled(C.WHITE.toString(), group.label()))
        .addLore(AdaptLanguage.textStyled(C.WHITE.toString(), GuiMessages.PREFERENCE_GROUP_ENABLED,
            trusted("enabled", enabled), trusted("total", total)))
        .addLore(AdaptLanguage.textStyled(C.GRAY.toString(), GuiMessages.PREFERENCE_GROUP_OPEN))
        .setRole(MenuRole.NAVIGATION)
        .setValue(enabled + "/" + total)
        .setDescription(List.of())
        .onLeftClick((MenuClick clicked) -> onOwner(() -> openGroup(group)))
        .onRightClick((MenuClick clicked) -> onOwner(() -> openGroup(group)));
  }

  private void openGroup(PreferenceToggleGroup group) {
    PreferenceGroupGui.open(adaptation, group, window.getViewer(), adaptationPage);
  }

  private <E extends Enum<E>> void cycle(PlayerPreference<E> preference, int position, int direction) {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    if (!adaptation.isPlayerPreferenceVisible(player, preference)) {
      render(true);
      feedback(adaptation, window.getViewer(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7F);
      return;
    }
    int level = adaptation.getLevel(player);
    List<PlayerPreference.Choice<E>> allowed = PlayerPreferences.allowedChoices(adaptation, preference, level);
    boolean changed = false;
    if (level > 0 && PlayerPreferences.policy(adaptation, preference).playerEditable && allowed.size() > 1) {
      E current = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
      int index = allowed.indexOf(preference.choice(current));
      int next = Math.floorMod(index + direction, allowed.size());
      changed = PlayerPreferences.set(adaptation, player, preference, allowed.get(next).value());
    }
    if (!visiblePreferences.equals(visiblePreferences(player))) {
      render(true);
    } else {
      window.updateElement(position, row, control(preference, player, position));
    }
    feedback(adaptation, window.getViewer(), changed ? Sounds.UI_BUTTON_CLICK : Sounds.BLOCK_NOTE_BLOCK_BASS, changed ? 1.2F : 0.7F);
  }

  private List<PlayerPreference<?>> visiblePreferences(AdaptPlayer player) {
    List<PlayerPreference<?>> preferences = new ArrayList<>();
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      if (adaptation.isPlayerPreferenceVisible(player, preference)) {
        preferences.add(preference);
      }
    }
    return List.copyOf(preferences);
  }

  private void reset() {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player != null) {
      boolean accepted = PlayerPreferences.reset(adaptation, player);
      render(true);
      feedback(adaptation, window.getViewer(), accepted ? Sounds.UI_BUTTON_CLICK : Sounds.BLOCK_NOTE_BLOCK_BASS, accepted ? 0.9F : 0.7F);
    }
  }

  private void changePage(int direction) {
    int previousPage = page;
    page += direction;
    render(true);
    boolean changed = previousPage != page;
    feedback(adaptation, window.getViewer(), changed ? Sounds.ITEM_BOOK_PAGE_TURN : Sounds.BLOCK_NOTE_BLOCK_BASS, changed ? 1.1F : 0.7F);
  }

  private void onOwner(Runnable action) {
    PlayerRef viewer = window.getViewer();
    if (!Tasks.isOwnedByCurrentRegion(viewer)) {
      Tasks.runEntity(viewer, () -> onOwner(action));
      return;
    }
    if (!AdaptConfig.get().isPlayerSettingsEnabled()) {
      render(true);
      return;
    }
    if (!acceptsClick(adaptation, window)) {
      return;
    }
    action.run();
  }

  private sealed interface Entry permits PreferenceEntry, GroupEntry {
  }

  private record PreferenceEntry(PlayerPreference<?> preference) implements Entry {
  }

  private record GroupEntry(PreferenceToggleGroup group) implements Entry {
  }

  private record Placement(GuiWindow window, int row, int adaptationPage) {
  }
}
