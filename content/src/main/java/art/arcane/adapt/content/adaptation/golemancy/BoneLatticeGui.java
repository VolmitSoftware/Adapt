package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.gui.GolemGuiActions;
import art.arcane.adapt.content.golem.gui.GolemGuiSupport;
import art.arcane.adapt.content.golem.gui.RosterGui;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.content.golem.gui.GolemElement;
import art.arcane.adapt.content.golem.gui.GolemLayout;
import art.arcane.adapt.content.golem.gui.GolemTheme;
import art.arcane.adapt.content.golem.gui.GolemWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


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
    if (!AdaptApi.scheduler().owns(player)) {
      AdaptApi.scheduler().runEntity(player, () -> open(frame, player, rosterPage));
      return;
    }
    PlayerProfile adaptPlayer = frame.adaptPlayer(player).orElse(null);
    int level = frame.ownerLevel(player.uuid());
    if (adaptPlayer == null || (level < 1 && adaptPlayer.section(GolemRoster.section()).lattice().isEmpty())) {
      RosterGui.open(player, rosterPage);
      return;
    }
    Text.renderFor(player.uuid(), () -> {
      GolemRoster roster = adaptPlayer.section(GolemRoster.section());
      View view = new View(frame, player, rosterPage, roster, new BoneLatticeEditor(adaptPlayer, frame.latticeSlots(level), frame.aspects()),
          frame.latticeSlots(level), maxTier(frame, player));
      GolemWindow window = new GolemWindow(player);
      GolemTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(ROWS);
      window.setFreePlayerInventory(true);
      window.setLayout(MenuLayout.GRID);
      window.setBreadcrumb(List.of(Text.key("adapt.golemancy.roster.title").resolve()));
      place(window, view);
      window.setTitle(Text.key("adapt.golemancy.bone_frame.lattice").resolve());
      window.open();
    });
  }

  private static int maxTier(GolemBoneFrame frame, PlayerRef player) {
    return frame.runicAugments().map(runic -> runic.maxTier(runic.ownerLevel(player.uuid()))).orElse(0);
  }

  private static void place(GolemWindow window, View view) {
    List<GolemRune> lattice = view.roster().lattice();
    int shown = Math.min(BoneFrameRules.MAX_LATTICE_SLOTS, Math.max(view.frame().mostLatticeSlots(), lattice.size()));
    for (int index = 0; index < shown; index++) {
      window.setElement(GolemLayout.centeredPosition(index, shown), LATTICE_ROW, slotElement(view, lattice, index));
    }
    window.setElement(LEFT_EDGE, LATTICE_ROW, GolemTheme.element("bone-lattice-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(TextFormat.GRAY + Text.key("adapt.golemancy.gui.back").resolve())
        .onLeftClick(clicked -> RosterGui.open(view.player(), view.rosterPage())));
    DyeColor selected = view.roster().servantChannel().map(DyeColor::valueOf).orElse(null);
    window.setElement(RIGHT_EDGE, LATTICE_ROW, GolemTheme.element("bone-lattice-channel-none", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName((selected == null ? TextFormat.GREEN : TextFormat.YELLOW) + channelLabel(Text.key("adapt.golemancy.channel.none")))
        .setLabel(Text.key("adapt.golemancy.channel.none").resolve())
        .setEnchanted(selected == null)
        .onLeftClick(clicked -> setChannel(view, null))
        .setRole(MenuRole.ENTRY)
        .setValue(selected == null ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve()));
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      window.setElement(GolemLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), FIRST_CHANNEL_ROW + index / CHANNELS_PER_ROW,
          channelElement(view, color, color == selected));
    }
  }

  private static GolemElement slotElement(View view, List<GolemRune> lattice, int index) {
    if (index < lattice.size()) {
      return runeElement(view, lattice.get(index), index);
    }
    if (index < view.slots()) {
      return GolemTheme.element("bone-lattice-" + index, Id.minecraft("magenta_stained_glass_pane"), "gui", "golemancy", "slots", "rune_empty")
          .setName(TextFormat.LIGHT_PURPLE + Text.key("adapt.golemancy.bone_frame.slot_empty").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.bone_frame.slot_empty_lore").withMarkup("aspects", aspectNames(view.frame().aspects())).resolve())
            .onLeftClick(clicked -> insert(view))
            .setRole(MenuRole.ACTION)
            .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.bone_frame.slot_empty_lore").withMarkup("aspects", aspectNames(view.frame().aspects())).resolve());
    }
    return GolemTheme.element("bone-lattice-" + index, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "rune_locked")
        .setName(TextFormat.RED + Text.key("adapt.golemancy.bone_frame.slot_locked").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.bone_frame.slot_unlock_level").withMarkup("level", String.valueOf(view.frame().slotUnlockLevel(index))).resolve())
        .setRole(MenuRole.INFO);
  }

  private static GolemElement runeElement(View view, GolemRune rune, int index) {
    ItemRef item = GolemItems.rune(rune);
    GolemElement element = new GolemElement("bone-lattice-" + index)
        .setItem(item);
    for (String line : item.lore()) {
      element.addLore(line);
    }
    int tier = BoneFrameRules.effectiveTier(rune.tier(), view.maxTier());
    if (index >= view.slots()) {
      element.addLore(TextFormat.RED + Text.key("adapt.golemancy.bone_frame.slot_inert").withMarkup("level", String.valueOf(view.frame().slotUnlockLevel(index))).resolve());
    } else if (tier <= 0) {
      element.addLore(TextFormat.RED + Text.key("adapt.golemancy.gui.rune_unpowered").resolve());
    } else {
      element.addLore(TextFormat.GREEN + Text.key("adapt.golemancy.bone_frame.rune_tier").withMarkup("tier", String.valueOf(tier)).resolve());
    }
    return element.setDescription(element.getLore())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.rune_remove").resolve())
        .onLeftClick(clicked -> take(view, index, rune))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.rune_remove").resolve());
  }

  private static GolemElement channelElement(View view, DyeColor color, boolean selected) {
    int mask = ChannelMask.of(color);
    return GolemGuiActions.channelElement("bone-lattice-channel-" + color.id(), mask)
        .setName((selected ? TextFormat.GREEN : TextFormat.YELLOW) + channelLabel(GolemGuiActions.channelName(mask)))
        .setLabel(GolemGuiActions.channelName(mask).resolve())
        .addLore(selected ? TextFormat.GREEN + Text.key("adapt.golemancy.gui.channel_selected").resolve() : TextFormat.GRAY + Text.key("adapt.golemancy.gui.channel_select").resolve())
        .setEnchanted(selected)
        .onLeftClick(clicked -> setChannel(view, color))
        .setRole(MenuRole.ENTRY)
        .setValue(selected ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
        .setDescription(List.of())
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve());
  }

  private static String channelLabel(Text channel) {
    return Text.key("adapt.golemancy.bone_frame.channel").withMarkup("channel", channel.resolve()).resolve();
  }

  private static String aspectNames(Set<GolemRuneType> aspects) {
    List<String> names = new ArrayList<>(aspects.size());
    for (GolemRuneType aspect : aspects) {
      names.add(aspect.displayName().resolve());
    }
    return String.join(ASPECT_SEPARATOR, names);
  }

  private static void insert(View view) {
    PlayerRef player = view.player();
    BoneLatticeEditor.Outcome outcome = view.editor().insert(player);
    GolemRune rune = outcome.rune();
    switch (outcome.result()) {
      case SLOTTED -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.rune_slotted").with("aspect", aspectName(rune)));
      case NO_RUNE -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.rune_needed"));
      case ASPECT -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.bone_frame.refused_aspect").with("aspect", aspectName(rune)));
      case DUPLICATE -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.bone_frame.duplicate").with("aspect", aspectName(rune)));
      case NO_SLOT -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.bone_frame.no_slot"));
      case TAKEN, STALE -> {
      }
    }
    reopen(view);
  }

  private static void take(View view, int index, GolemRune rune) {
    BoneLatticeEditor.Outcome outcome = view.editor().take(view.player(), index, rune);
    if (outcome.result() == BoneLatticeEditor.Result.TAKEN) {
      GolemGuiSupport.notify(view.player(), Text.key("adapt.golemancy.message.rune_removed").with("aspect", aspectName(rune)));
    }
    reopen(view);
  }

  private static void setChannel(View view, DyeColor channel) {
    PlayerRef player = view.player();
    view.roster().setServantChannel(channel == null ? null : channel.name());
    view.frame().adaptPlayer(player).ifPresent(PlayerProfile::requestSave);
    Text name = channel == null ? Text.key("adapt.golemancy.channel.none") : GolemGuiActions.channelName(ChannelMask.of(channel));
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.bone_frame.channel_set").with("channel", name));
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.requestUpdate(player));
    reopen(view);
  }

  private static Text aspectName(GolemRune rune) {
    return rune.aspect().displayName();
  }

  private static void reopen(View view) {
    open(view.frame(), view.player(), view.rosterPage());
  }

  private record View(GolemBoneFrame frame, PlayerRef player, int rosterPage, GolemRoster roster, BoneLatticeEditor editor, int slots, int maxTier) {
  }
}
