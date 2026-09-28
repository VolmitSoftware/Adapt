package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class PreferenceGuiSupport {
  private final Adaptation<?> adaptation;
  private final UIWindow window;
  private final int row;
  private List<PlayerPreference<?>> visiblePreferences = List.of();
  private int page;

  private PreferenceGuiSupport(Adaptation<?> adaptation, Placement placement) {
    this.adaptation = adaptation;
    window = placement.window();
    row = placement.row();
  }

  static void populate(Adaptation<?> adaptation, UIWindow window, int row) {
    new PreferenceGuiSupport(adaptation, new Placement(window, row)).render(false);
  }

  private void render(boolean update) {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    List<PlayerPreference<?>> preferences = adaptation.getPlayerPreferences();
    visiblePreferences = visiblePreferences(player);
    boolean paged = preferences.size() > 8;
    int capacity = paged ? 6 : 8;
    page = GuiLayout.clampPage(page, (preferences.size() + capacity - 1) / capacity);
    int start = page * capacity;
    int count = Math.min(capacity, preferences.size() - start);
    Element[] controls = new Element[GuiLayout.WIDTH];
    for (int index = 0; index < count; index++) {
      PlayerPreference<?> preference = preferences.get(start + index);
      if (!visiblePreferences.contains(preference)) {
        continue;
      }
      int position = paged ? index - 3 : GuiLayout.centeredPosition(index, count + 1);
      controls[position + 4] = control(preference, player, position);
    }
    int resetPosition = paged ? 4 : GuiLayout.centeredPosition(count, count + 1);
    controls[resetPosition + 4] = new UIElement("preference-reset")
        .setMaterial(new MaterialBlock(Material.MILK_BUCKET))
        .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREFERENCE_RESET))
        .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.PREFERENCE_RESET_DESCRIPTION))
        .onLeftClick(element -> onOwner(this::reset));
    if (paged) {
      controls[0] = new UIElement("preference-previous")
          .setMaterial(new MaterialBlock(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(element -> onOwner(() -> changePage(-1)));
      controls[7] = new UIElement("preference-next")
          .setMaterial(new MaterialBlock(start + count < preferences.size() ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(element -> onOwner(() -> changePage(1)));
    }
    for (int slot = 0; slot < controls.length; slot++) {
      if (update) {
        window.updateElement(slot - 4, row, controls[slot]);
      } else if (controls[slot] != null) {
        window.setElement(slot - 4, row, controls[slot]);
      }
    }
  }

  private <E extends Enum<E>> Element control(PlayerPreference<E> preference, AdaptPlayer player, int position) {
    int level = adaptation.getLevel(player);
    PreferencePolicy policy = PlayerPreferences.policy(adaptation, preference);
    E value = PlayerPreferences.resolve(adaptation, player.getData(), level, preference);
    PlayerPreference.Choice<E> selected = preference.choice(value);
    List<PlayerPreference.Choice<E>> allowed = PlayerPreferences.allowedChoices(adaptation, preference, level);
    boolean serverControlled = !policy.playerEditable || policy.allowedValues.size() == 1;
    Element element = new UIElement("preference-" + preference.id())
        .setMaterial(new MaterialBlock(serverControlled || allowed.isEmpty()
            ? Material.GRAY_STAINED_GLASS_PANE : selected.icon()))
        .setName(C.WHITE + AdaptLanguage.text(preference.label()))
        .addLore(C.WHITE + AdaptLanguage.text(GuiMessages.PREFERENCE_CURRENT,
            trusted("value", AdaptLanguage.text(selected.label()))));
    if (serverControlled) {
      element.addLore(C.GOLD + AdaptLanguage.text(GuiMessages.PREFERENCE_SERVER_CONTROLLED));
    } else if (level < 1) {
      element.addLore(C.GRAY + AdaptLanguage.text(GuiMessages.PREFERENCE_LEARN_FIRST));
    } else if (allowed.size() > 1) {
      element.addLore(C.GRAY + AdaptLanguage.text(GuiMessages.PREFERENCE_CYCLE));
    }
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      if (choice.minimumLevel() > level) {
        element.addLore(C.DARK_GRAY + AdaptLanguage.text(GuiMessages.PREFERENCE_LEVEL_REQUIRED,
            trusted("value", AdaptLanguage.text(choice.label())), trusted("level", choice.minimumLevel())));
      } else if (!allowed.contains(choice)) {
        element.addLore(C.DARK_GRAY + AdaptLanguage.text(GuiMessages.PREFERENCE_SERVER_DISABLED,
            trusted("value", AdaptLanguage.text(choice.label()))));
      }
    }
    return element.onLeftClick(clicked -> onOwner(() -> cycle(preference, position, 1)))
        .onRightClick(clicked -> onOwner(() -> cycle(preference, position, -1)));
  }

  private <E extends Enum<E>> void cycle(PlayerPreference<E> preference, int position, int direction) {
    AdaptPlayer player = adaptation.getPlayer(window.getViewer());
    if (player == null) {
      return;
    }
    if (!adaptation.isPlayerPreferenceVisible(player, preference)) {
      render(true);
      feedback(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F);
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
    feedback(changed ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, changed ? 1.2F : 0.7F);
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
      feedback(accepted ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, accepted ? 0.9F : 0.7F);
    }
  }

  private void changePage(int direction) {
    int previousPage = page;
    page += direction;
    render(true);
    boolean changed = previousPage != page;
    feedback(changed ? Sound.ITEM_BOOK_PAGE_TURN : Sound.BLOCK_NOTE_BLOCK_BASS, changed ? 1.1F : 0.7F);
  }

  private void feedback(Sound sound, float pitch) {
    Player viewer = window.getViewer();
    AdaptPlayer player = adaptation.getPlayer(viewer);
    if (player == null || !player.getData().isEffectsEnabled() || !adaptation.areSoundsEnabled()) {
      return;
    }
    SoundPlayer.of(viewer).play(viewer.getLocation(), sound, 0.3F, pitch);
  }

  private void onOwner(Runnable action) {
    Player viewer = window.getViewer();
    if (!J.isOwnedByCurrentRegion(viewer)) {
      J.runEntity(viewer, () -> onOwner(action));
      return;
    }
    if (!viewer.isOnline() || !window.isVisible()
        || Adapt.instance.getGuiLeftovers().get(viewer.getUniqueId().toString()) != window
        || Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(adaptation.getSkill().getName()) != adaptation.getSkill()
        || !adaptation.isEnabled() || !adaptation.getSkill().isEnabled()
        || !adaptation.getSkill().getAdaptations().contains(adaptation)
        || !adaptation.getSkill().hasUsePermission(viewer, adaptation.getSkill())
        || !adaptation.hasUsePermission(viewer, adaptation)) {
      return;
    }
    action.run();
  }

  private record Placement(UIWindow window, int row) {
  }
}
