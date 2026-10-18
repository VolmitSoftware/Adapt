package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.List;

public final class GolemGuiActions {
  private static final List<GolemMode> MODES = List.of(GolemMode.values());
  private static final List<DyeColor> COLORS = List.of(DyeColor.values());

  private GolemGuiActions() {
  }

  public static GolemMode cycleMode(GolemMode current, boolean forward) {
    int size = MODES.size();
    return MODES.get(Math.floorMod(current.ordinal() + (forward ? 1 : -1), size));
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

  public static int runeSlots(GolemBody body) {
    return switch (body) {
      case FROST -> 1;
      case COPPER -> 2;
      case IRON -> 3;
      case BONE -> 0;
    };
  }

  public static boolean releasableFromRoster(GolemRoster.Status status) {
    return status == GolemRoster.Status.MISSING;
  }

  public static LocateAction locateAction(boolean loaded, boolean sameWorld) {
    if (!sameWorld) {
      return LocateAction.REPORT;
    }
    return loaded ? LocateAction.GLOW : LocateAction.POINT;
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

  public enum LocateAction {
    GLOW,
    POINT,
    REPORT
  }
}
