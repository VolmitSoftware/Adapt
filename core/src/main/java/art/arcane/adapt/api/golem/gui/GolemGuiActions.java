package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class GolemGuiActions {
  public static final int CARRIED_SLOTS = 18;

  private GolemGuiActions() {
  }

  public static GolemMode cycleMode(List<GolemCoreType> cores, GolemMode current, boolean forward) {
    List<GolemMode> modes = GolemMode.selectableFor(cores);
    int position = modes.indexOf(current);
    if (position < 0) {
      return modes.getFirst();
    }
    return modes.get(Math.floorMod(position + (forward ? 1 : -1), modes.size()));
  }

  public static DyeColor channelColor(int mask) {
    List<DyeColor> colors = ChannelMask.colors(mask);
    return colors.isEmpty() ? null : colors.getFirst();
  }

  public static int toggleChannel(int mask, DyeColor color, int slots) {
    if (ChannelMask.contains(mask, color)) {
      return ChannelMask.without(mask, color);
    }
    if (slots == 1) {
      return ChannelMask.of(color);
    }
    return Integer.bitCount(ChannelMask.normalize(mask)) >= slots ? mask : ChannelMask.with(mask, color);
  }

  public static List<TextKey> channelNames(int mask) {
    List<DyeColor> colors = ChannelMask.colors(mask);
    if (colors.isEmpty()) {
      return List.of(GolemancyMessages.CHANNEL_NONE);
    }
    List<TextKey> names = new ArrayList<>(colors.size());
    for (DyeColor color : colors) {
      names.add(channelName(ChannelMask.of(color)));
    }
    return names;
  }

  public static RuneNote runeNote(GolemRuneLayout layout, List<GolemRune> runes, int index, GolemBody body) {
    GolemRune rune = runes.get(index);
    return switch (layout.status(runes, index)) {
      case ACTIVE -> new RuneNote(notice(GolemancyMessages.GUI_RUNE_ACTIVE), true);
      case OVER_TIER -> unlockNote(GolemancyMessages.GUI_RUNE_OVER_TIER, layout.tierUnlockLevel(rune.tier()), trusted("tier", rune.tier()));
      case LOCKED_SLOT -> unlockNote(GolemancyMessages.GUI_RUNE_SLOT_LOCKED, layout.slotUnlockLevel(index));
      case BLOCKED -> new RuneNote(notice(GolemancyMessages.GUI_RUNE_BLOCKED, aspectArgument(rune.aspect()), bodyArgument(body)), false);
      case DUPLICATE -> new RuneNote(notice(GolemancyMessages.GUI_RUNE_DUPLICATE, aspectArgument(rune.aspect())), false);
    };
  }

  public static Notice refusalNotice(GolemRuneLayout.Refusal refusal, GolemRuneType aspect, GolemBody body) {
    return switch (refusal) {
      case NO_SLOT -> notice(GolemancyMessages.MESSAGE_RUNE_NO_SLOT);
      case DUPLICATE -> notice(GolemancyMessages.MESSAGE_RUNE_DUPLICATE, aspectArgument(aspect));
      case BLOCKED -> notice(GolemancyMessages.MESSAGE_RUNE_BLOCKED, aspectArgument(aspect), bodyArgument(body));
    };
  }

  public static List<GolemRune> withRune(List<GolemRune> runes, GolemRune rune) {
    List<GolemRune> next = new ArrayList<>(runes.size() + 1);
    next.addAll(runes);
    next.add(rune);
    return next;
  }

  public static Optional<List<GolemRune>> withoutRune(List<GolemRune> runes, int index, GolemRune expected) {
    if (index < 0 || index >= runes.size() || !runes.get(index).equals(expected)) {
      return Optional.empty();
    }
    List<GolemRune> next = new ArrayList<>(runes);
    next.remove(index);
    return Optional.of(next);
  }

  public static boolean hasSnowTrail(GolemBody body) {
    return body == GolemBody.FROST;
  }

  public static Id snowTrailIcon(boolean snowTrail) {
    return snowTrail ? Id.minecraft("snow_block") : Id.minecraft("snowball");
  }

  public static TextKey snowTrailName(boolean snowTrail) {
    return snowTrail ? GolemancyMessages.GUI_SNOW_TRAIL_ON : GolemancyMessages.GUI_SNOW_TRAIL_OFF;
  }

  public static Id combatIcon(boolean combat, boolean locked) {
    if (locked) {
      return Id.minecraft("shield");
    }
    return combat ? Id.minecraft("iron_sword") : Id.minecraft("barrier");
  }

  public static TextKey combatName(boolean combat, boolean locked) {
    if (locked) {
      return GolemancyMessages.GUI_COMBAT_LOCKED;
    }
    return combat ? GolemancyMessages.GUI_COMBAT_ON : GolemancyMessages.GUI_COMBAT_OFF;
  }

  public static TextKey combatLore(boolean combat, boolean locked) {
    if (locked) {
      return GolemancyMessages.GUI_COMBAT_LOCKED_LORE;
    }
    return combat ? GolemancyMessages.GUI_COMBAT_ON_LORE : GolemancyMessages.GUI_COMBAT_OFF_LORE;
  }

  public static Id colossusIcon(boolean colossus) {
    return colossus ? Id.minecraft("iron_block") : Id.minecraft("iron_ingot");
  }

  public static TextKey colossusName(boolean colossus) {
    return colossus ? GolemancyMessages.GUI_COLOSSUS_ON : GolemancyMessages.GUI_COLOSSUS_OFF;
  }

  public static Optional<TextKey> colossusRefusal(GolemColossus.Toggle toggle) {
    return switch (toggle) {
      case NO_ROOM -> Optional.of(GolemancyMessages.MESSAGE_COLOSSUS_NO_ROOM);
      case BUSY -> Optional.of(GolemancyMessages.MESSAGE_COLOSSUS_BUSY);
      case UNAVAILABLE, GROW, SHRINK -> Optional.empty();
    };
  }

  public static boolean showsMasterwork(boolean masterwork, int maxMasterworks) {
    return masterwork || maxMasterworks > 0;
  }

  public static boolean showsSecondCore(List<GolemCoreType> cores, boolean dualCore) {
    return cores.size() > 1 || dualCore;
  }

  public static Notice designationNotice(GolemRegistry.Designation designation, int masterworks) {
    return switch (designation) {
      case DESIGNATED -> notice(GolemancyMessages.MASTERWORK_DESIGNATED);
      case LOCKED -> notice(GolemancyMessages.MASTERWORK_LOCKED);
      case CAP -> notice(GolemancyMessages.MASTERWORK_CAP, trusted("count", masterworks));
      case ALREADY -> notice(GolemancyMessages.MASTERWORK_ALREADY);
      case UNAVAILABLE -> notice(GolemancyMessages.BINDING_UNAVAILABLE);
    };
  }

  public static Notice coreRefusalNotice(GolemMastery.CoreRefusal refusal, GolemCoreType core, GolemBody body) {
    return switch (refusal) {
      case UNAVAILABLE -> notice(GolemancyMessages.BINDING_UNAVAILABLE);
      case NO_SLOT -> notice(GolemancyMessages.MASTERWORK_CORE_NO_SLOT);
      case BLANK -> notice(GolemancyMessages.MASTERWORK_CORE_BLANK);
      case DUPLICATE -> notice(GolemancyMessages.MASTERWORK_CORE_DUPLICATE, coreArgument(core));
      case NOT_ALLOWED -> notice(GolemancyMessages.BINDING_CORE_NOT_ALLOWED, bodyArgument(body), coreArgument(core));
      case LOCKED -> notice(GolemancyMessages.BINDING_CORE_LOCKED, coreArgument(core));
      case HEART -> notice(GolemancyMessages.RUNEFORGED_HEART_NEEDED);
    };
  }

  public static boolean releasableFromRoster(GolemRoster.Status status) {
    return status != GolemRoster.Status.ACTIVE;
  }

  public static TextKey releaseConfirm(GolemRoster.Status status) {
    return switch (status) {
      case SHATTERED -> GolemancyMessages.ROSTER_RELEASE_SHATTERED_CONFIRM;
      case PACKED -> GolemancyMessages.ROSTER_RELEASE_PACKED_CONFIRM;
      case ACTIVE, MISSING -> GolemancyMessages.ROSTER_RELEASE_CONFIRM;
    };
  }

  public static boolean recallable(GolemRoster.Status status, boolean loaded, boolean sameWorld) {
    return status == GolemRoster.Status.ACTIVE && loaded && sameWorld;
  }

  public static LocateAction locateAction(boolean loaded, boolean sameWorld) {
    if (!sameWorld) {
      return LocateAction.REPORT;
    }
    return loaded ? LocateAction.GLOW : LocateAction.POINT;
  }

  public static boolean chestInReach(GolemAnchor chest, UUID golemWorld, GolemAnchor center, double leashRadius) {
    if (!chest.world().equals(golemWorld) || !center.world().equals(golemWorld)) {
      return false;
    }
    double dx = chest.x() - center.x();
    double dy = chest.y() - center.y();
    double dz = chest.z() - center.z();
    return dx * dx + dy * dy + dz * dz <= leashRadius * leashRadius;
  }

  public static int carriedShown(int stored) {
    return stored > CARRIED_SLOTS ? CARRIED_SLOTS - 1 : stored;
  }

  public static boolean showsEssence(List<GolemCoreType> cores, int essencePoints) {
    return cores.contains(GolemCoreType.GATHERER) || essencePoints > 0;
  }

  public static int chunkCenterX(long chunkKey) {
    return (GolemRoster.chunkX(chunkKey) << 4) + 8;
  }

  public static int chunkCenterZ(long chunkKey) {
    return (GolemRoster.chunkZ(chunkKey) << 4) + 8;
  }

  public static Id bodyIcon(GolemBody body) {
    return switch (body) {
      case FROST -> Id.minecraft("snow_block");
      case COPPER -> Id.minecraft("copper_block");
      case IRON -> Id.minecraft("iron_block");
      case BONE -> Id.minecraft("bone_block");
    };
  }

  public static Id modeIcon(GolemMode mode) {
    return switch (mode) {
      case WORK -> Id.minecraft("iron_pickaxe");
      case FOLLOW -> Id.minecraft("lead");
      case STAY -> Id.minecraft("armor_stand");
      case HOME -> Id.minecraft("red_bed");
    };
  }

  /** A channel tile themed per colour, so each colour keeps its own icon and resource-pack model. */
  public static GuiElement channelElement(String id, int mask) {
    Id icon = channelIcon(mask);
    return GuiTheme.element(id, icon, "gui", "golemancy", "channel", icon.path());
  }

  public static Id channelIcon(int mask) {
    DyeColor color = channelColor(mask);
    if (color == null) {
      return Id.minecraft("glass_bottle");
    }
    return switch (color) {
      case WHITE -> Id.minecraft("white_dye");
      case ORANGE -> Id.minecraft("orange_dye");
      case MAGENTA -> Id.minecraft("magenta_dye");
      case LIGHT_BLUE -> Id.minecraft("light_blue_dye");
      case YELLOW -> Id.minecraft("yellow_dye");
      case LIME -> Id.minecraft("lime_dye");
      case PINK -> Id.minecraft("pink_dye");
      case GRAY -> Id.minecraft("gray_dye");
      case LIGHT_GRAY -> Id.minecraft("light_gray_dye");
      case CYAN -> Id.minecraft("cyan_dye");
      case PURPLE -> Id.minecraft("purple_dye");
      case BLUE -> Id.minecraft("blue_dye");
      case BROWN -> Id.minecraft("brown_dye");
      case GREEN -> Id.minecraft("green_dye");
      case RED -> Id.minecraft("red_dye");
      case BLACK -> Id.minecraft("black_dye");
    };
  }

  public static TextKey channelName(int mask) {
    DyeColor color = channelColor(mask);
    if (color == null) {
      return GolemancyMessages.CHANNEL_NONE;
    }
    return switch (color) {
      case WHITE -> GolemancyMessages.CHANNEL_WHITE;
      case ORANGE -> GolemancyMessages.CHANNEL_ORANGE;
      case MAGENTA -> GolemancyMessages.CHANNEL_MAGENTA;
      case LIGHT_BLUE -> GolemancyMessages.CHANNEL_LIGHT_BLUE;
      case YELLOW -> GolemancyMessages.CHANNEL_YELLOW;
      case LIME -> GolemancyMessages.CHANNEL_LIME;
      case PINK -> GolemancyMessages.CHANNEL_PINK;
      case GRAY -> GolemancyMessages.CHANNEL_GRAY;
      case LIGHT_GRAY -> GolemancyMessages.CHANNEL_LIGHT_GRAY;
      case CYAN -> GolemancyMessages.CHANNEL_CYAN;
      case PURPLE -> GolemancyMessages.CHANNEL_PURPLE;
      case BLUE -> GolemancyMessages.CHANNEL_BLUE;
      case BROWN -> GolemancyMessages.CHANNEL_BROWN;
      case GREEN -> GolemancyMessages.CHANNEL_GREEN;
      case RED -> GolemancyMessages.CHANNEL_RED;
      case BLACK -> GolemancyMessages.CHANNEL_BLACK;
    };
  }

  public static TextKey statusName(GolemRoster.Status status) {
    return switch (status) {
      case ACTIVE -> GolemancyMessages.STATUS_ACTIVE;
      case PACKED -> GolemancyMessages.STATUS_PACKED;
      case SHATTERED -> GolemancyMessages.STATUS_SHATTERED;
      case MISSING -> GolemancyMessages.STATUS_MISSING;
    };
  }

  private static RuneNote unlockNote(TextKey key, int level, MessageArgument... leading) {
    if (level <= 0) {
      return new RuneNote(notice(GolemancyMessages.GUI_RUNE_UNPOWERED), false);
    }
    List<MessageArgument> arguments = new ArrayList<>(List.of(leading));
    arguments.add(trusted("level", level));
    return new RuneNote(new Notice(key, arguments), false);
  }

  private static Notice notice(TextKey key, MessageArgument... arguments) {
    return new Notice(key, List.of(arguments));
  }

  private static MessageArgument aspectArgument(GolemRuneType aspect) {
    return trusted("aspect", GolemGuiSupport.localized(aspect.displayName()));
  }

  private static MessageArgument bodyArgument(GolemBody body) {
    return trusted("body", GolemGuiSupport.localized(body.displayName()));
  }

  private static MessageArgument coreArgument(GolemCoreType core) {
    return trusted("core", GolemGuiSupport.localized(core.displayName()));
  }

  public record Notice(TextKey key, List<MessageArgument> arguments) {
    public Notice {
      arguments = List.copyOf(arguments);
    }

    public String text() {
      return AdaptLanguage.text(key, arguments.toArray(MessageArgument[]::new));
    }
  }

  public record RuneNote(Notice notice, boolean active) {
  }

  public enum LocateAction {
    GLOW,
    POINT,
    REPORT
  }
}
