package art.arcane.adapt.content.adaptation.golemancy;

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
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;
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

  static void open(GolemBoneFrame frame, PlayerRef player, int rosterPage) {
    if (player == null || !player.isOnline()) {
      return;
    }
    if (!Tasks.isOwnedByCurrentRegion(player)) {
      Tasks.runEntity(player, () -> open(frame, player, rosterPage));
      return;
    }
    AdaptPlayer adaptPlayer = frame.adaptPlayer(player).orElse(null);
    int level = frame.ownerLevel(player.uuid());
    if (adaptPlayer == null || (level < 1 && adaptPlayer.getData().getGolemancy().lattice().isEmpty())) {
      RosterGui.open(player, rosterPage);
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      GolemRoster roster = adaptPlayer.getData().getGolemancy();
      View view = new View(frame, player, rosterPage, roster, new BoneLatticeEditor(adaptPlayer, frame.latticeSlots(level), frame.aspects()),
          frame.latticeSlots(level), maxTier(frame, player));
      GuiWindow window = new GuiWindow(player);
      GuiTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(ROWS);
      window.setFreePlayerInventory(true);
      place(window, view);
      window.setTitle(AdaptLanguage.text(GolemancyMessages.BONE_FRAME_LATTICE));
      window.open();
    }
  }

  private static int maxTier(GolemBoneFrame frame, PlayerRef player) {
    return frame.runicAugments().map(runic -> runic.maxTier(runic.ownerLevel(player.uuid()))).orElse(0);
  }

  private static void place(GuiWindow window, View view) {
    List<GolemRune> lattice = view.roster().lattice();
    int shown = Math.min(BoneFrameRules.MAX_LATTICE_SLOTS, Math.max(view.frame().mostLatticeSlots(), lattice.size()));
    for (int index = 0; index < shown; index++) {
      window.setElement(GuiLayout.centeredPosition(index, shown), LATTICE_ROW, slotElement(view, lattice, index));
    }
    window.setElement(LEFT_EDGE, LATTICE_ROW, GuiTheme.element("bone-lattice-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
        .onLeftClick(clicked -> RosterGui.open(view.player(), view.rosterPage())));
    DyeColor selected = view.roster().servantChannel().map(DyeColor::valueOf).orElse(null);
    window.setElement(RIGHT_EDGE, LATTICE_ROW, GuiTheme.element("bone-lattice-channel-none", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName((selected == null ? C.GREEN : C.YELLOW) + channelLabel(GolemancyMessages.CHANNEL_NONE))
        .setEnchanted(selected == null)
        .onLeftClick(clicked -> setChannel(view, null)));
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      window.setElement(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), FIRST_CHANNEL_ROW + index / CHANNELS_PER_ROW,
          channelElement(view, color, color == selected));
    }
  }

  private static GuiElement slotElement(View view, List<GolemRune> lattice, int index) {
    if (index < lattice.size()) {
      return runeElement(view, lattice.get(index), index);
    }
    if (index < view.slots()) {
      return GuiTheme.element("bone-lattice-" + index, Id.minecraft("magenta_stained_glass_pane"), "gui", "golemancy", "slots", "rune_empty")
          .setName(C.LIGHT_PURPLE + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_EMPTY))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_EMPTY_LORE, trusted("aspects", aspectNames(view.frame().aspects()))))
            .onLeftClick(clicked -> insert(view));
    }
    return GuiTheme.element("bone-lattice-" + index, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "rune_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_SLOT_UNLOCK_LEVEL, trusted("level", view.frame().slotUnlockLevel(index))));
  }

  private static GuiElement runeElement(View view, GolemRune rune, int index) {
    ItemRef item = GolemItems.rune(rune);
    GuiElement element = new GuiElement("bone-lattice-" + index)
        .setItem(item);
    for (String line : item.lore()) {
      element.addLore(line);
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

  private static GuiElement channelElement(View view, DyeColor color, boolean selected) {
    int mask = ChannelMask.of(color);
    return GuiTheme.element("bone-lattice-channel-" + color.id(), GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
        .setName((selected ? C.GREEN : C.YELLOW) + channelLabel(GolemGuiActions.channelName(mask)))
        .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
        .setEnchanted(selected)
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
    PlayerRef player = view.player();
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
    PlayerRef player = view.player();
    view.roster().setServantChannel(channel == null ? null : channel.name());
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

  private record View(GolemBoneFrame frame, PlayerRef player, int rosterPage, GolemRoster roster, BoneLatticeEditor editor, int slots, int maxTier) {
  }
}
