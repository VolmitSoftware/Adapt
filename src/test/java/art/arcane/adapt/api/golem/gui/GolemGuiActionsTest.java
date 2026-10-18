package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemGuiActionsTest {
  @Test
  void roleGolemModesCycleForwardAndBackwardThroughEveryModeAndWrap() {
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.WORK, true)).isEqualTo(GolemMode.FOLLOW);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.FOLLOW, true)).isEqualTo(GolemMode.STAY);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.STAY, true)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.HOME, true)).isEqualTo(GolemMode.WORK);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.WORK, false)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.GUARDIAN, GolemMode.STAY, false)).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void companionGolemsCycleOnlyFollowStayAndHome() {
    assertThat(GolemMode.selectableFor(GolemCoreType.BLANK)).containsExactly(GolemMode.FOLLOW, GolemMode.STAY, GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.BLANK, GolemMode.FOLLOW, true)).isEqualTo(GolemMode.STAY);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.BLANK, GolemMode.HOME, true)).isEqualTo(GolemMode.FOLLOW);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.BLANK, GolemMode.FOLLOW, false)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemCoreType.BLANK, GolemMode.WORK, true)).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void onlyFrostGolemsOfferTheSnowTrailToggle() {
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.FROST)).isTrue();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.COPPER)).isFalse();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.IRON)).isFalse();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.BONE)).isFalse();
    assertThat(GolemGuiActions.snowTrailIcon(true)).isEqualTo(Material.SNOW_BLOCK);
    assertThat(GolemGuiActions.snowTrailIcon(false)).isEqualTo(Material.SNOWBALL);
    assertThat(GolemGuiActions.snowTrailName(true)).isEqualTo(GolemancyMessages.GUI_SNOW_TRAIL_ON);
    assertThat(GolemGuiActions.snowTrailName(false)).isEqualTo(GolemancyMessages.GUI_SNOW_TRAIL_OFF);
  }

  @Test
  void theColossusToggleShowsWhetherTheFormIsOn() {
    assertThat(GolemGuiActions.colossusIcon(true)).isEqualTo(Material.IRON_BLOCK);
    assertThat(GolemGuiActions.colossusIcon(false)).isEqualTo(Material.IRON_INGOT);
    assertThat(GolemGuiActions.colossusName(true)).isEqualTo(GolemancyMessages.GUI_COLOSSUS_ON);
    assertThat(GolemGuiActions.colossusName(false)).isEqualTo(GolemancyMessages.GUI_COLOSSUS_OFF);
  }

  @Test
  void refusedAndBusyColossusTogglesTellThePlayerWhy() {
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.NO_ROOM)).contains(GolemancyMessages.MESSAGE_COLOSSUS_NO_ROOM);
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.BUSY)).contains(GolemancyMessages.MESSAGE_COLOSSUS_BUSY);
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.GROW)).isEmpty();
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.SHRINK)).isEmpty();
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.UNAVAILABLE)).isEmpty();
  }

  @Test
  void channelCyclesThroughNoneAndAllSixteenDyes() {
    int mask = ChannelMask.NONE;
    Set<Integer> seen = new HashSet<>();
    for (int step = 0; step < 17; step++) {
      seen.add(mask);
      mask = GolemGuiActions.cycleChannel(mask, true);
    }

    assertThat(mask).isEqualTo(ChannelMask.NONE);
    assertThat(seen).hasSize(17);
    assertThat(GolemGuiActions.cycleChannel(ChannelMask.NONE, true)).isEqualTo(ChannelMask.of(DyeColor.WHITE));
    assertThat(GolemGuiActions.cycleChannel(ChannelMask.of(DyeColor.BLACK), true)).isEqualTo(ChannelMask.NONE);
    assertThat(GolemGuiActions.cycleChannel(ChannelMask.NONE, false)).isEqualTo(ChannelMask.of(DyeColor.BLACK));
    assertThat(GolemGuiActions.cycleChannel(ChannelMask.of(DyeColor.WHITE), false)).isEqualTo(ChannelMask.NONE);
    assertThat(GolemGuiActions.cycleChannel(ChannelMask.of(DyeColor.RED), true)).isEqualTo(ChannelMask.of(DyeColor.BLACK));
  }

  @Test
  void aMultiChannelMaskCyclesFromItsFirstChannelToASingleOne() {
    int mask = ChannelMask.of(DyeColor.ORANGE, DyeColor.BLUE);

    assertThat(GolemGuiActions.channelColor(mask)).isEqualTo(DyeColor.ORANGE);
    assertThat(GolemGuiActions.cycleChannel(mask, true)).isEqualTo(ChannelMask.of(DyeColor.MAGENTA));
    assertThat(GolemGuiActions.cycleChannel(mask, false)).isEqualTo(ChannelMask.of(DyeColor.WHITE));
    assertThat(GolemGuiActions.channelColor(ChannelMask.NONE)).isNull();
  }

  @Test
  void runeSlotsFollowTheBodyTable() {
    assertThat(GolemGuiActions.runeSlots(GolemBody.FROST)).isEqualTo(1);
    assertThat(GolemGuiActions.runeSlots(GolemBody.COPPER)).isEqualTo(2);
    assertThat(GolemGuiActions.runeSlots(GolemBody.IRON)).isEqualTo(3);
    assertThat(GolemGuiActions.runeSlots(GolemBody.BONE)).isZero();
  }

  @Test
  void onlyMissingEntriesCanBeReleasedFromTheRoster() {
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.MISSING)).isTrue();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.ACTIVE)).isFalse();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.PACKED)).isFalse();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.SHATTERED)).isFalse();
  }

  @Test
  void onlyLoadedGolemsInTheOwnersWorldCanBeLocatedByGlow() {
    assertThat(GolemGuiActions.locateAction(true, true)).isEqualTo(GolemGuiActions.LocateAction.GLOW);
    assertThat(GolemGuiActions.locateAction(false, true)).isEqualTo(GolemGuiActions.LocateAction.POINT);
    assertThat(GolemGuiActions.locateAction(true, false)).isEqualTo(GolemGuiActions.LocateAction.REPORT);
    assertThat(GolemGuiActions.locateAction(false, false)).isEqualTo(GolemGuiActions.LocateAction.REPORT);
  }

  @Test
  void everyDyeHasItsOwnIconAndName() {
    Set<Material> icons = new HashSet<>();
    Set<TextKey> names = new HashSet<>();
    for (DyeColor color : DyeColor.values()) {
      icons.add(GolemGuiActions.channelIcon(ChannelMask.of(color)));
      names.add(GolemGuiActions.channelName(ChannelMask.of(color)));
    }
    icons.add(GolemGuiActions.channelIcon(ChannelMask.NONE));
    names.add(GolemGuiActions.channelName(ChannelMask.NONE));

    assertThat(icons).hasSize(17);
    assertThat(names).hasSize(17);
    assertThat(GolemGuiActions.channelIcon(ChannelMask.of(DyeColor.LIGHT_BLUE))).isEqualTo(Material.LIGHT_BLUE_DYE);
  }

  @Test
  void bodiesModesAndStatusesHaveDistinctPresentation() {
    Set<Material> bodyIcons = new HashSet<>();
    for (GolemBody body : GolemBody.values()) {
      bodyIcons.add(GolemGuiActions.bodyIcon(body));
    }
    Set<Material> modeIcons = new HashSet<>();
    Set<TextKey> modeNames = new HashSet<>();
    for (GolemMode mode : GolemMode.values()) {
      modeIcons.add(GolemGuiActions.modeIcon(mode));
      modeNames.add(mode.displayName());
    }
    Set<TextKey> statusNames = new HashSet<>();
    for (GolemRoster.Status status : GolemRoster.Status.values()) {
      statusNames.add(GolemGuiActions.statusName(status));
    }

    assertThat(bodyIcons).hasSize(GolemBody.values().length);
    assertThat(modeIcons).hasSize(GolemMode.values().length);
    assertThat(modeNames).hasSize(GolemMode.values().length);
    assertThat(statusNames).hasSize(GolemRoster.Status.values().length);
  }

  @Test
  void homeChestsMustShareTheGolemsWorldAndSitWithinTheLeashOfItsHome() {
    UUID overworld = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    UUID nether = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    GolemState.Anchor home = GolemState.Anchor.of(overworld, 0, 64, 0);

    assertThat(GolemGuiActions.chestInReach(GolemState.Anchor.of(overworld, 32, 64, 0), overworld, home, 32D)).isTrue();
    assertThat(GolemGuiActions.chestInReach(GolemState.Anchor.of(overworld, 20, 84, 20), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemState.Anchor.of(overworld, 33, 64, 0), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemState.Anchor.of(nether, 0, 64, 0), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemState.Anchor.of(nether, 0, 64, 0), nether, home, 32D)).isFalse();
  }

  @Test
  void carriedViewShowsEighteenStacksOrSeventeenAndAMoreMarker() {
    assertThat(GolemGuiActions.carriedShown(0)).isZero();
    assertThat(GolemGuiActions.carriedShown(18)).isEqualTo(18);
    assertThat(GolemGuiActions.carriedShown(19)).isEqualTo(17);
    assertThat(GolemGuiActions.carriedShown(40)).isEqualTo(17);
  }

  @Test
  void approximateChunkCentersComeFromTheChunkKey() {
    long key = GolemRoster.chunkKey(-3, 5);

    assertThat(GolemGuiActions.chunkCenterX(key)).isEqualTo(-40);
    assertThat(GolemGuiActions.chunkCenterZ(key)).isEqualTo(88);
  }
}
