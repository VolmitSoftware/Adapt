package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiActions;
import art.arcane.adapt.api.golem.gui.GolemGuiSupport;
import art.arcane.adapt.api.golem.gui.RosterGui;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class BoneLatticeGui {
  private static final int ROWS = 3;
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;
  private static final int LATTICE_ROW = 0;
  private static final int FIRST_CHANNEL_ROW = 1;
  private static final int CHANNELS_PER_ROW = 8;
  private static final String ASPECT_SEPARATOR = ", ";

  private BoneLatticeGui() {
  }

  static void open(GolemBoneFrame frame, Player player, int rosterPage) {
    if (player == null || !player.isOnline()) {
      return;
    }
    if (!J.isOwnedByCurrentRegion(player)) {
      J.runEntity(player, () -> open(frame, player, rosterPage));
      return;
    }
    AdaptPlayer adaptPlayer = frame.adaptPlayer(player).orElse(null);
    int level = frame.level(player.getUniqueId());
    if (adaptPlayer == null || frame.latticeSlots(level) <= 0) {
      RosterGui.open(player, rosterPage);
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
      GolemRoster roster = adaptPlayer.getData().getGolemancy();
      View view = new View(frame, player, rosterPage, roster, new BoneLatticeEditor(roster, adaptPlayer::requestSave, frame.latticeSlots(level), frame.aspects()),
          frame.latticeSlots(level), maxTier(frame, player));
      UIWindow window = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(ROWS);
      window.batch(() -> place(window, view));
      window.setTitle(AdaptLanguage.text(GolemancyMessages.BONE_FRAME_LATTICE));
      window.onClosed(closed -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window));
      window.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
    }
  }

  private static int maxTier(GolemBoneFrame frame, Player player) {
    return frame.runicAugments().map(runic -> runic.maxTier(runic.level(player.getUniqueId()))).orElse(0);
  }

  private static void place(UIWindow window, View view) {
    List<GolemRune> lattice = view.roster().lattice();
    int shown = Math.min(BoneFrameRules.MAX_LATTICE_SLOTS, Math.max(view.frame().mostLatticeSlots(), lattice.size()));
    for (int index = 0; index < shown; index++) {
      window.setElement(GuiLayout.centeredPosition(index, shown), LATTICE_ROW, slotElement(view, lattice, index));
    }
    window.setElement(LEFT_EDGE, LATTICE_ROW, GuiTheme.element("bone-lattice-back", Material.ARROW, "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
        .setProgress(1D)
        .onLeftClick(clicked -> RosterGui.open(view.player(), view.rosterPage())));
    DyeColor selected = view.roster().servantChannel().orElse(null);
    window.setElement(RIGHT_EDGE, LATTICE_ROW, GuiTheme.element("bone-lattice-channel-none", Material.GLASS_BOTTLE, "gui", "golemancy", "channels_clear")
        .setName((selected == null ? C.GREEN : C.YELLOW) + channelLabel(GolemancyMessages.CHANNEL_NONE))
        .setEnchanted(selected == null)
        .setProgress(1D)
        .onLeftClick(clicked -> setChannel(view, null)));
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      window.setElement(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), FIRST_CHANNEL_ROW + index / CHANNELS_PER_ROW,
          channelElement(view, color, color == selected));
    }
  }

  private static Element slotElement(View view, List<GolemRune> lattice, int index) {
    if (index < lattice.size()) {
      return runeElement(view, lattice.get(index), index);
    }
    if (index < view.slots()) {
      return GuiTheme.element("bone-lattice-" + index, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "gui", "golemancy", "rune_empty")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_EMPTY))
          .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_EMPTY_LORE, trusted("aspects", aspectNames(view.frame().aspects()))))
          .setProgress(1D)
          .onLeftClick(clicked -> insert(view));
    }
    return GuiTheme.element("bone-lattice-" + index, Material.BARRIER, "gui", "golemancy", "rune_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_UNLOCK_LEVEL, trusted("level", view.frame().slotUnlockLevel(index))))
        .setProgress(1D);
  }

  private static Element runeElement(View view, GolemRune rune, int index) {
    ItemStack item = GolemItems.rune(rune);
    Element element = new UIElement("bone-lattice-" + index)
        .setMaterial(new MaterialBlock(item.getType()))
        .setBaseItemStack(item)
        .setProgress(1D);
    ItemMeta meta = item.getItemMeta();
    if (meta != null && meta.getLore() != null) {
      for (String line : meta.getLore()) {
        element.addLore(line);
      }
    }
    int tier = BoneFrameRules.effectiveTier(rune.tier(), view.maxTier());
    if (index >= view.slots()) {
      element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_INERT, trusted("level", view.frame().slotUnlockLevel(index))));
    } else if (tier <= 0) {
      element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_UNPOWERED));
    } else {
      element.addLore(C.GREEN + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_RUNE_TIER, trusted("tier", tier)));
    }
    return element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_REMOVE))
        .onLeftClick(clicked -> take(view, index, rune));
  }

  private static Element channelElement(View view, DyeColor color, boolean selected) {
    int mask = ChannelMask.of(color);
    return GuiTheme.element("bone-lattice-channel-" + color.name().toLowerCase(Locale.ROOT), GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
        .setName((selected ? C.GREEN : C.YELLOW) + channelLabel(GolemGuiActions.channelName(mask)))
        .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
        .setEnchanted(selected)
        .setProgress(1D)
        .onLeftClick(clicked -> setChannel(view, color));
  }

  private static String channelLabel(TextKey channel) {
    return AdaptLanguage.text(GolemancyMessages.BONE_FRAME_CHANNEL, trusted("channel", AdaptLanguage.text(channel)));
  }

  private static String aspectNames(Set<GolemRuneType> aspects) {
    List<String> names = new ArrayList<>(aspects.size());
    for (GolemRuneType aspect : aspects) {
      names.add(AdaptLanguage.text(aspect.displayName()));
    }
    return String.join(ASPECT_SEPARATOR, names);
  }

  private static void insert(View view) {
    Player player = view.player();
    BoneLatticeEditor.Outcome outcome = view.editor().insert(player);
    GolemRune rune = outcome.rune();
    switch (outcome.result()) {
      case SLOTTED -> GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RUNE_SLOTTED, trusted("aspect", aspectName(rune)));
      case NO_RUNE -> GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RUNE_NEEDED);
      case ASPECT -> GolemGuiSupport.warn(player, GolemancyMessages.BONE_FRAME_REFUSED_ASPECT, trusted("aspect", aspectName(rune)));
      case DUPLICATE -> GolemGuiSupport.warn(player, GolemancyMessages.BONE_FRAME_DUPLICATE, trusted("aspect", aspectName(rune)));
      case NO_SLOT -> GolemGuiSupport.warn(player, GolemancyMessages.BONE_FRAME_NO_SLOT);
      case TAKEN, STALE -> {
      }
    }
    reopen(view);
  }

  private static void take(View view, int index, GolemRune rune) {
    BoneLatticeEditor.Outcome outcome = view.editor().take(view.player(), index, rune);
    if (outcome.result() == BoneLatticeEditor.Result.TAKEN) {
      GolemGuiSupport.notify(view.player(), GolemancyMessages.MESSAGE_RUNE_REMOVED, trusted("aspect", aspectName(rune)));
    }
    reopen(view);
  }

  private static void setChannel(View view, DyeColor channel) {
    Player player = view.player();
    view.roster().setServantChannel(channel);
    view.frame().adaptPlayer(player).ifPresent(AdaptPlayer::requestSave);
    TextKey name = channel == null ? GolemancyMessages.CHANNEL_NONE : GolemGuiActions.channelName(ChannelMask.of(channel));
    GolemGuiSupport.notify(player, GolemancyMessages.BONE_FRAME_CHANNEL_SET, trusted("channel", GolemGuiSupport.localized(name)));
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.requestUpdate(player));
    reopen(view);
  }

  private static Object aspectName(GolemRune rune) {
    return GolemGuiSupport.localized(rune.aspect().displayName());
  }

  private static void reopen(View view) {
    open(view.frame(), view.player(), view.rosterPage());
  }

  private record View(GolemBoneFrame frame, Player player, int rosterPage, GolemRoster roster, BoneLatticeEditor editor, int slots, int maxTier) {
  }
}
