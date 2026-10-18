package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemGuiActionsTest {
  @Test
  void modeCyclesForwardAndBackwardThroughEveryModeAndWraps() {
    assertThat(GolemGuiActions.cycleMode(GolemMode.WORK, true)).isEqualTo(GolemMode.FOLLOW);
    assertThat(GolemGuiActions.cycleMode(GolemMode.FOLLOW, true)).isEqualTo(GolemMode.STAY);
    assertThat(GolemGuiActions.cycleMode(GolemMode.STAY, true)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemMode.HOME, true)).isEqualTo(GolemMode.WORK);
    assertThat(GolemGuiActions.cycleMode(GolemMode.WORK, false)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(GolemMode.STAY, false)).isEqualTo(GolemMode.FOLLOW);
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
  void approximateChunkCentersComeFromTheChunkKey() {
    long key = GolemRoster.chunkKey(-3, 5);

    assertThat(GolemGuiActions.chunkCenterX(key)).isEqualTo(-40);
    assertThat(GolemGuiActions.chunkCenterZ(key)).isEqualTo(88);
  }
}
