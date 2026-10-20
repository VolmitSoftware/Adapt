package art.arcane.adapt.api.golem.gui;

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
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class GolemGuiActions {
  public static final int CARRIED_SLOTS = 18;
  private static final List<DyeColor> COLORS = List.of(DyeColor.values());

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

  public static int cycleChannel(int mask, boolean forward) {
    DyeColor current = channelColor(mask);
    int position = current == null ? COLORS.size() : current.ordinal();
    int next = Math.floorMod(position + (forward ? 1 : -1), COLORS.size() + 1);
    return next == COLORS.size() ? ChannelMask.NONE : ChannelMask.of(COLORS.get(next));
  }

  public static DyeColor channelColor(int mask) {
    List<DyeColor> colors = ChannelMask.colors(mask);
    return colors.isEmpty() ? null : colors.getFirst();
  }

  public static int toggleChannel(int mask, DyeColor color, int slots) {
    if (ChannelMask.contains(mask, color)) {
      return ChannelMask.without(mask, color);
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

  public static Material snowTrailIcon(boolean snowTrail) {
    return snowTrail ? Material.SNOW_BLOCK : Material.SNOWBALL;
  }

  public static TextKey snowTrailName(boolean snowTrail) {
    return snowTrail ? GolemancyMessages.GUI_SNOW_TRAIL_ON : GolemancyMessages.GUI_SNOW_TRAIL_OFF;
  }

  public static Material colossusIcon(boolean colossus) {
    return colossus ? Material.IRON_BLOCK : Material.IRON_INGOT;
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

  public static boolean chestInReach(GolemState.Anchor chest, UUID golemWorld, GolemState.Anchor center, double leashRadius) {
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

  public static Material bodyIcon(GolemBody body) {
    return switch (body) {
      case FROST -> Material.SNOW_BLOCK;
      case COPPER -> Material.COPPER_BLOCK;
      case IRON -> Material.IRON_BLOCK;
      case BONE -> Material.BONE_BLOCK;
    };
  }

  public static Material modeIcon(GolemMode mode) {
    return switch (mode) {
      case WORK -> Material.IRON_PICKAXE;
      case FOLLOW -> Material.LEAD;
      case STAY -> Material.ARMOR_STAND;
      case HOME -> Material.RED_BED;
    };
  }

  public static Material channelIcon(int mask) {
    DyeColor color = channelColor(mask);
    if (color == null) {
      return Material.GLASS_BOTTLE;
    }
    return switch (color) {
      case WHITE -> Material.WHITE_DYE;
      case ORANGE -> Material.ORANGE_DYE;
      case MAGENTA -> Material.MAGENTA_DYE;
      case LIGHT_BLUE -> Material.LIGHT_BLUE_DYE;
      case YELLOW -> Material.YELLOW_DYE;
      case LIME -> Material.LIME_DYE;
      case PINK -> Material.PINK_DYE;
      case GRAY -> Material.GRAY_DYE;
      case LIGHT_GRAY -> Material.LIGHT_GRAY_DYE;
      case CYAN -> Material.CYAN_DYE;
      case PURPLE -> Material.PURPLE_DYE;
      case BLUE -> Material.BLUE_DYE;
      case BROWN -> Material.BROWN_DYE;
      case GREEN -> Material.GREEN_DYE;
      case RED -> Material.RED_DYE;
      case BLACK -> Material.BLACK_DYE;
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
