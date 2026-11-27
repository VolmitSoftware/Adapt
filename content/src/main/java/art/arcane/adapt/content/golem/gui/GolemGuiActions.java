package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemColossus;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneLayout;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


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

  public static List<Text> channelNames(int mask) {
    List<DyeColor> colors = ChannelMask.colors(mask);
    if (colors.isEmpty()) {
      return List.of(Text.key("adapt.golemancy.channel.none"));
    }
    List<Text> names = new ArrayList<>(colors.size());
    for (DyeColor color : colors) {
      names.add(channelName(ChannelMask.of(color)));
    }
    return names;
  }

  public static RuneNote runeNote(GolemRuneLayout layout, List<GolemRune> runes, int index, GolemBody body) {
    GolemRune rune = runes.get(index);
    return switch (layout.status(runes, index)) {
      case ACTIVE -> new RuneNote(Text.key("adapt.golemancy.gui.rune_active"), true);
      case OVER_TIER -> unlockNote(Text.key("adapt.golemancy.gui.rune_over_tier").withMarkup("tier", String.valueOf(rune.tier())), layout.tierUnlockLevel(rune.tier()));
      case LOCKED_SLOT -> unlockNote(Text.key("adapt.golemancy.gui.rune_slot_locked"), layout.slotUnlockLevel(index));
      case BLOCKED -> new RuneNote(Text.key("adapt.golemancy.gui.rune_blocked").with("aspect", rune.aspect().displayName()).with("body", body.displayName()), false);
      case DUPLICATE -> new RuneNote(Text.key("adapt.golemancy.gui.rune_duplicate").with("aspect", rune.aspect().displayName()), false);
    };
  }

  public static Text refusalNotice(GolemRuneLayout.Refusal refusal, GolemRuneType aspect, GolemBody body) {
    return switch (refusal) {
      case NO_SLOT -> Text.key("adapt.golemancy.message.rune_no_slot");
      case DUPLICATE -> Text.key("adapt.golemancy.message.rune_duplicate").with("aspect", aspect.displayName());
      case BLOCKED -> Text.key("adapt.golemancy.message.rune_blocked").with("aspect", aspect.displayName()).with("body", body.displayName());
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

  public static Text snowTrailName(boolean snowTrail) {
    return snowTrail ? Text.key("adapt.golemancy.gui.snow_trail_on") : Text.key("adapt.golemancy.gui.snow_trail_off");
  }

  public static Id combatIcon(boolean combat, boolean locked) {
    if (locked) {
      return Id.minecraft("shield");
    }
    return combat ? Id.minecraft("iron_sword") : Id.minecraft("barrier");
  }

  public static Text combatName(boolean combat, boolean locked) {
    if (locked) {
      return Text.key("adapt.golemancy.gui.combat_locked");
    }
    return combat ? Text.key("adapt.golemancy.gui.combat_on") : Text.key("adapt.golemancy.gui.combat_off");
  }

  public static Text combatLore(boolean combat, boolean locked) {
    if (locked) {
      return Text.key("adapt.golemancy.gui.combat_locked_lore");
    }
    return combat ? Text.key("adapt.golemancy.gui.combat_on_lore") : Text.key("adapt.golemancy.gui.combat_off_lore");
  }

  public static Id colossusIcon(boolean colossus) {
    return colossus ? Id.minecraft("iron_block") : Id.minecraft("iron_ingot");
  }

  public static Text colossusName(boolean colossus) {
    return colossus ? Text.key("adapt.golemancy.gui.colossus_on") : Text.key("adapt.golemancy.gui.colossus_off");
  }

  public static Optional<Text> colossusRefusal(GolemColossus.Toggle toggle) {
    return switch (toggle) {
      case NO_ROOM -> Optional.of(Text.key("adapt.golemancy.message.colossus_no_room"));
      case BUSY -> Optional.of(Text.key("adapt.golemancy.message.colossus_busy"));
      case UNAVAILABLE, GROW, SHRINK -> Optional.empty();
    };
  }

  public static boolean showsMasterwork(boolean masterwork, int maxMasterworks) {
    return masterwork || maxMasterworks > 0;
  }

  public static boolean showsSecondCore(List<GolemCoreType> cores, boolean dualCore) {
    return cores.size() > 1 || dualCore;
  }

  public static Text designationNotice(GolemRegistry.Designation designation, int masterworks) {
    return switch (designation) {
      case DESIGNATED -> Text.key("adapt.golemancy.masterwork.designated");
      case LOCKED -> Text.key("adapt.golemancy.masterwork.locked");
      case CAP -> Text.key("adapt.golemancy.masterwork.cap").withMarkup("count", String.valueOf(masterworks));
      case ALREADY -> Text.key("adapt.golemancy.masterwork.already");
      case UNAVAILABLE -> Text.key("adapt.golemancy.binding.unavailable");
    };
  }

  public static Text coreRefusalNotice(GolemMastery.CoreRefusal refusal, GolemCoreType core, GolemBody body) {
    return switch (refusal) {
      case UNAVAILABLE -> Text.key("adapt.golemancy.binding.unavailable");
      case NO_SLOT -> Text.key("adapt.golemancy.masterwork.core_no_slot");
      case BLANK -> Text.key("adapt.golemancy.masterwork.core_blank");
      case DUPLICATE -> Text.key("adapt.golemancy.masterwork.core_duplicate").with("core", core.displayName());
      case NOT_ALLOWED -> Text.key("adapt.golemancy.binding.core_not_allowed").with("body", body.displayName()).with("core", core.displayName());
      case LOCKED -> Text.key("adapt.golemancy.binding.core_locked").with("core", core.displayName());
      case HEART -> Text.key("adapt.upgrade.golemancy.runeforged.heart_needed");
    };
  }

  public static boolean releasableFromRoster(GolemRoster.Status status) {
    return status != GolemRoster.Status.ACTIVE;
  }

  public static Text releaseConfirm(GolemRoster.Status status) {
    return switch (status) {
      case SHATTERED -> Text.key("adapt.golemancy.roster.release_shattered_confirm");
      case PACKED -> Text.key("adapt.golemancy.roster.release_packed_confirm");
      case ACTIVE, MISSING -> Text.key("adapt.golemancy.roster.release_confirm");
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
  public static GolemElement channelElement(String id, int mask) {
    Id icon = channelIcon(mask);
    return GolemTheme.element(id, icon, "gui", "golemancy", "channel", icon.path());
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

  public static Text channelName(int mask) {
    DyeColor color = channelColor(mask);
    if (color == null) {
      return Text.key("adapt.golemancy.channel.none");
    }
    return switch (color) {
      case WHITE -> Text.key("adapt.golemancy.channel.white");
      case ORANGE -> Text.key("adapt.golemancy.channel.orange");
      case MAGENTA -> Text.key("adapt.golemancy.channel.magenta");
      case LIGHT_BLUE -> Text.key("adapt.golemancy.channel.light_blue");
      case YELLOW -> Text.key("adapt.golemancy.channel.yellow");
      case LIME -> Text.key("adapt.golemancy.channel.lime");
      case PINK -> Text.key("adapt.golemancy.channel.pink");
      case GRAY -> Text.key("adapt.golemancy.channel.gray");
      case LIGHT_GRAY -> Text.key("adapt.golemancy.channel.light_gray");
      case CYAN -> Text.key("adapt.golemancy.channel.cyan");
      case PURPLE -> Text.key("adapt.golemancy.channel.purple");
      case BLUE -> Text.key("adapt.golemancy.channel.blue");
      case BROWN -> Text.key("adapt.golemancy.channel.brown");
      case GREEN -> Text.key("adapt.golemancy.channel.green");
      case RED -> Text.key("adapt.golemancy.channel.red");
      case BLACK -> Text.key("adapt.golemancy.channel.black");
    };
  }

  public static Text statusName(GolemRoster.Status status) {
    return switch (status) {
      case ACTIVE -> Text.key("adapt.golemancy.status.active");
      case PACKED -> Text.key("adapt.golemancy.status.packed");
      case SHATTERED -> Text.key("adapt.golemancy.status.shattered");
      case MISSING -> Text.key("adapt.golemancy.status.missing");
    };
  }

  private static RuneNote unlockNote(Text notice, int level) {
    if (level <= 0) {
      return new RuneNote(Text.key("adapt.golemancy.gui.rune_unpowered"), false);
    }
    return new RuneNote(notice.withMarkup("level", String.valueOf(level)), false);
  }

  public record RuneNote(Text notice, boolean active) {
  }

  public enum LocateAction {
    GLOW,
    POINT,
    REPORT
  }
}
