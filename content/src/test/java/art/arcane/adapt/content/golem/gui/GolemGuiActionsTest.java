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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class GolemGuiActionsTest {
  @Test
  void everyChannelColourHasItsOwnThemePathSoItsIconIsNotSharedWithTheFirstColourResolved() {
    Set<String> paths = new HashSet<>();
    try (MockedStatic<GolemTheme> themes = mockStatic(GolemTheme.class, invocation -> {
      if (!"element".equals(invocation.getMethod().getName())) {
        return null;
      }
      Object[] arguments = invocation.getArguments();
      StringBuilder path = new StringBuilder();
      for (int index = 2; index < arguments.length; index++) {
        path.append('/').append(arguments[index]);
      }
      paths.add(path.toString());
      return new GolemElement(invocation.getArgument(0)).setMaterial(invocation.getArgument(1));
    })) {
      assertThat(GolemGuiActions.channelElement("none", ChannelMask.NONE).getMaterial()).isEqualTo(Id.minecraft("glass_bottle"));
      for (DyeColor color : DyeColor.values()) {
        assertThat(GolemGuiActions.channelElement("channel-" + color.id(), ChannelMask.of(color)).getMaterial())
            .isEqualTo(GolemGuiActions.channelIcon(ChannelMask.of(color)));
      }
    }
    assertThat(paths).hasSize(DyeColor.values().length + 1);
  }

  @Test
  void roleGolemModesCycleForwardAndBackwardThroughEveryModeAndWrap() {
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.WORK, true)).isEqualTo(GolemMode.FOLLOW);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.FOLLOW, true)).isEqualTo(GolemMode.STAY);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.STAY, true)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.HOME, true)).isEqualTo(GolemMode.WORK);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.WORK, false)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.GUARDIAN), GolemMode.STAY, false)).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void companionGolemsCycleOnlyFollowStayAndHome() {
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.BLANK), GolemMode.FOLLOW, true)).isEqualTo(GolemMode.STAY);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.BLANK), GolemMode.HOME, true)).isEqualTo(GolemMode.FOLLOW);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.BLANK), GolemMode.FOLLOW, false)).isEqualTo(GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.BLANK), GolemMode.WORK, true)).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void onlyFrostGolemsOfferTheSnowTrailToggle() {
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.FROST)).isTrue();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.COPPER)).isFalse();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.IRON)).isFalse();
    assertThat(GolemGuiActions.hasSnowTrail(GolemBody.BONE)).isFalse();
    assertThat(GolemGuiActions.snowTrailIcon(true)).isEqualTo(Id.minecraft("snow_block"));
    assertThat(GolemGuiActions.snowTrailIcon(false)).isEqualTo(Id.minecraft("snowball"));
    assertThat(GolemGuiActions.snowTrailName(true)).isEqualTo(Text.key("adapt.golemancy.gui.snow_trail_on"));
    assertThat(GolemGuiActions.snowTrailName(false)).isEqualTo(Text.key("adapt.golemancy.gui.snow_trail_off"));
  }

  @Test
  void theCombatToggleShowsItsStateAndTheGuardianLock() {
    assertThat(GolemGuiActions.combatIcon(true, false)).isEqualTo(Id.minecraft("iron_sword"));
    assertThat(GolemGuiActions.combatIcon(false, false)).isEqualTo(Id.minecraft("barrier"));
    assertThat(GolemGuiActions.combatIcon(true, true)).isEqualTo(Id.minecraft("shield"));
    assertThat(GolemGuiActions.combatName(true, false)).isEqualTo(Text.key("adapt.golemancy.gui.combat_on"));
    assertThat(GolemGuiActions.combatName(false, false)).isEqualTo(Text.key("adapt.golemancy.gui.combat_off"));
    assertThat(GolemGuiActions.combatName(true, true)).isEqualTo(Text.key("adapt.golemancy.gui.combat_locked"));
    assertThat(GolemGuiActions.combatLore(true, false)).isEqualTo(Text.key("adapt.golemancy.gui.combat_on_lore"));
    assertThat(GolemGuiActions.combatLore(false, false)).isEqualTo(Text.key("adapt.golemancy.gui.combat_off_lore"));
    assertThat(GolemGuiActions.combatLore(true, true)).isEqualTo(Text.key("adapt.golemancy.gui.combat_locked_lore"));
  }

  @Test
  void theColossusToggleShowsWhetherTheFormIsOn() {
    assertThat(GolemGuiActions.colossusIcon(true)).isEqualTo(Id.minecraft("iron_block"));
    assertThat(GolemGuiActions.colossusIcon(false)).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(GolemGuiActions.colossusName(true)).isEqualTo(Text.key("adapt.golemancy.gui.colossus_on"));
    assertThat(GolemGuiActions.colossusName(false)).isEqualTo(Text.key("adapt.golemancy.gui.colossus_off"));
  }

  @Test
  void refusedAndBusyColossusTogglesTellThePlayerWhy() {
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.NO_ROOM)).contains(Text.key("adapt.golemancy.message.colossus_no_room"));
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.BUSY)).contains(Text.key("adapt.golemancy.message.colossus_busy"));
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.GROW)).isEmpty();
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.SHRINK)).isEmpty();
    assertThat(GolemGuiActions.colossusRefusal(GolemColossus.Toggle.UNAVAILABLE)).isEmpty();
  }

  @Test
  void aSingleChannelPickerReplacesOrClearsTheCurrentChoice() {
    int red = ChannelMask.of(DyeColor.RED);
    assertThat(GolemGuiActions.toggleChannel(red, DyeColor.BLUE, 1)).isEqualTo(ChannelMask.of(DyeColor.BLUE));
    assertThat(GolemGuiActions.toggleChannel(red, DyeColor.RED, 1)).isEqualTo(ChannelMask.NONE);
    assertThat(GolemGuiActions.channelColor(ChannelMask.NONE)).isNull();
  }

  @Test
  void anOrderGolemTogglesChannelsUpToItsChannelSlots() {
    int red = ChannelMask.of(DyeColor.RED);
    int redBlue = ChannelMask.of(DyeColor.RED, DyeColor.BLUE);

    assertThat(GolemGuiActions.toggleChannel(red, DyeColor.BLUE, 2)).isEqualTo(redBlue);
    assertThat(GolemGuiActions.toggleChannel(redBlue, DyeColor.LIME, 2)).isEqualTo(redBlue);
    assertThat(GolemGuiActions.toggleChannel(redBlue, DyeColor.RED, 2)).isEqualTo(ChannelMask.of(DyeColor.BLUE));
    assertThat(GolemGuiActions.toggleChannel(ChannelMask.of(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME), DyeColor.LIME, 2)).isEqualTo(redBlue);
    assertThat(GolemGuiActions.toggleChannel(ChannelMask.NONE, DyeColor.WHITE, 1)).isEqualTo(ChannelMask.of(DyeColor.WHITE));
  }

  @Test
  void channelNamesListEveryChannelOrNone() {
    assertThat(GolemGuiActions.channelNames(ChannelMask.of(DyeColor.RED, DyeColor.WHITE)))
        .containsExactly(Text.key("adapt.golemancy.channel.white"), Text.key("adapt.golemancy.channel.red"));
    assertThat(GolemGuiActions.channelNames(ChannelMask.NONE)).containsExactly(Text.key("adapt.golemancy.channel.none"));
  }

  @Test
  void slottedRunesSayWhetherTheyWorkAndWhatUnlocksThem() {
    GolemRuneLayout layout = new GolemRuneLayout(2, List.of(1, 3, 5), 1, List.of(1, 3, 0), Set.of(GolemRuneType.EMBER));
    List<GolemRune> runes = List.of(rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.MIGHT, 2), rune(GolemRuneType.TIDE, 1));

    assertThat(GolemGuiActions.runeNote(layout, runes, 0, GolemBody.IRON)).isEqualTo(runeNote(true, Text.key("adapt.golemancy.gui.rune_active")));
    assertThat(GolemGuiActions.runeNote(layout, runes, 1, GolemBody.IRON))
        .isEqualTo(runeNote(false, Text.key("adapt.golemancy.gui.rune_over_tier").withMarkup("tier", String.valueOf(2)).withMarkup("level", String.valueOf(3))));
    assertThat(GolemGuiActions.runeNote(layout, runes, 2, GolemBody.IRON))
        .isEqualTo(runeNote(false, Text.key("adapt.golemancy.gui.rune_slot_locked").withMarkup("level", String.valueOf(5))));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.EMBER, 1)), 0, GolemBody.FROST)).isEqualTo(runeNote(false,
        Text.key("adapt.golemancy.gui.rune_blocked").with("aspect", Text.key("adapt.golemancy.aspect.ember")).with("body", Text.key("adapt.golemancy.body.frost"))));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.ORDER, 1), rune(GolemRuneType.ORDER, 1)), 1, GolemBody.IRON))
        .isEqualTo(runeNote(false, Text.key("adapt.golemancy.gui.rune_duplicate").with("aspect", Text.key("adapt.golemancy.aspect.order"))));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.BULWARK, 3)), 0, GolemBody.IRON))
        .isEqualTo(runeNote(false, Text.key("adapt.golemancy.gui.rune_unpowered")));
    assertThat(GolemGuiActions.runeNote(GolemRuneLayout.NONE, List.of(rune(GolemRuneType.ORDER, 1)), 0, GolemBody.IRON))
        .isEqualTo(runeNote(false, Text.key("adapt.golemancy.gui.rune_unpowered")));
  }

  @Test
  void refusedRunesTellThePlayerWhy() {
    Text aspect = Text.key("adapt.golemancy.aspect.might");
    Text body = Text.key("adapt.golemancy.body.bone");
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.NO_SLOT, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(Text.key("adapt.golemancy.message.rune_no_slot"));
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.DUPLICATE, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(Text.key("adapt.golemancy.message.rune_duplicate").with("aspect", aspect));
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.BLOCKED, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(Text.key("adapt.golemancy.message.rune_blocked").with("aspect", aspect).with("body", body));
  }

  @Test
  void takingARuneBackRemovesOnlyTheExpectedRuneFromItsSlot() {
    GolemRune swiftness = rune(GolemRuneType.SWIFTNESS, 1);
    GolemRune might = rune(GolemRuneType.MIGHT, 2);
    GolemRune tide = rune(GolemRuneType.TIDE, 3);
    List<GolemRune> runes = List.of(swiftness, might, tide);

    assertThat(GolemGuiActions.withoutRune(runes, 1, might)).contains(List.of(swiftness, tide));
    assertThat(GolemGuiActions.withoutRune(runes, 1, swiftness)).isEmpty();
    assertThat(GolemGuiActions.withoutRune(runes, 3, tide)).isEmpty();
    assertThat(GolemGuiActions.withRune(runes.subList(0, 2), tide)).containsExactly(swiftness, might, tide);
  }

  @Test
  void missingShatteredAndPackedEntriesCanBeReleasedFromTheRoster() {
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.MISSING)).isTrue();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.SHATTERED)).isTrue();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.PACKED)).isTrue();
    assertThat(GolemGuiActions.releasableFromRoster(GolemRoster.Status.ACTIVE)).isFalse();
  }

  @Test
  void releasingAShatteredOrPackedEntryWarnsThatItsItemCrumbles() {
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.SHATTERED)).isEqualTo(Text.key("adapt.golemancy.roster.release_shattered_confirm"));
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.PACKED)).isEqualTo(Text.key("adapt.golemancy.roster.release_packed_confirm"));
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.MISSING)).isEqualTo(Text.key("adapt.golemancy.roster.release_confirm"));
  }

  @Test
  void onlyLoadedActiveGolemsInTheOwnersWorldCanBeRecalled() {
    assertThat(GolemGuiActions.recallable(GolemRoster.Status.ACTIVE, true, true)).isTrue();
    assertThat(GolemGuiActions.recallable(GolemRoster.Status.ACTIVE, false, true)).isFalse();
    assertThat(GolemGuiActions.recallable(GolemRoster.Status.ACTIVE, true, false)).isFalse();
    assertThat(GolemGuiActions.recallable(GolemRoster.Status.PACKED, true, true)).isFalse();
    assertThat(GolemGuiActions.recallable(GolemRoster.Status.MISSING, true, true)).isFalse();
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
    Set<Id> icons = new HashSet<>();
    Set<Text> names = new HashSet<>();
    for (DyeColor color : DyeColor.values()) {
      icons.add(GolemGuiActions.channelIcon(ChannelMask.of(color)));
      names.add(GolemGuiActions.channelName(ChannelMask.of(color)));
    }
    icons.add(GolemGuiActions.channelIcon(ChannelMask.NONE));
    names.add(GolemGuiActions.channelName(ChannelMask.NONE));

    assertThat(icons).hasSize(17);
    assertThat(names).hasSize(17);
    assertThat(GolemGuiActions.channelIcon(ChannelMask.of(DyeColor.LIGHT_BLUE))).isEqualTo(Id.minecraft("light_blue_dye"));
  }

  @Test
  void bodiesModesAndStatusesHaveDistinctPresentation() {
    Set<Id> bodyIcons = new HashSet<>();
    for (GolemBody body : GolemBody.values()) {
      bodyIcons.add(GolemGuiActions.bodyIcon(body));
    }
    Set<Id> modeIcons = new HashSet<>();
    Set<Text> modeNames = new HashSet<>();
    for (GolemMode mode : GolemMode.values()) {
      modeIcons.add(GolemGuiActions.modeIcon(mode));
      modeNames.add(mode.displayName());
    }
    Set<Text> statusNames = new HashSet<>();
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
    GolemAnchor home = GolemAnchor.of(overworld, 0, 64, 0);

    assertThat(GolemGuiActions.chestInReach(GolemAnchor.of(overworld, 32, 64, 0), overworld, home, 32D)).isTrue();
    assertThat(GolemGuiActions.chestInReach(GolemAnchor.of(overworld, 20, 84, 20), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemAnchor.of(overworld, 33, 64, 0), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemAnchor.of(nether, 0, 64, 0), overworld, home, 32D)).isFalse();
    assertThat(GolemGuiActions.chestInReach(GolemAnchor.of(nether, 0, 64, 0), nether, home, 32D)).isFalse();
  }

  @Test
  void carriedViewShowsEighteenStacksOrSeventeenAndAMoreMarker() {
    assertThat(GolemGuiActions.carriedShown(0)).isZero();
    assertThat(GolemGuiActions.carriedShown(18)).isEqualTo(18);
    assertThat(GolemGuiActions.carriedShown(19)).isEqualTo(17);
    assertThat(GolemGuiActions.carriedShown(40)).isEqualTo(17);
  }

  @Test
  void theEssenceButtonShowsForGatherersAndForAnyGolemStillHoldingEssence() {
    assertThat(GolemGuiActions.showsEssence(List.of(GolemCoreType.GATHERER), 0)).isTrue();
    assertThat(GolemGuiActions.showsEssence(List.of(GolemCoreType.GUARDIAN), 0)).isFalse();
    assertThat(GolemGuiActions.showsEssence(List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER), 0)).isTrue();
    assertThat(GolemGuiActions.showsEssence(List.of(GolemCoreType.GUARDIAN), 12)).isTrue();
  }

  @Test
  void approximateChunkCentersComeFromTheChunkKey() {
    long key = GolemRoster.chunkKey(-3, 5);

    assertThat(GolemGuiActions.chunkCenterX(key)).isEqualTo(-40);
    assertThat(GolemGuiActions.chunkCenterZ(key)).isEqualTo(88);
  }

  @Test
  void aCompanionWithASecondRoleCoreCanWork() {
    assertThat(GolemMode.selectableFor(List.of(GolemCoreType.BLANK, GolemCoreType.HARVESTER))).containsExactly(GolemMode.values());
    assertThat(GolemMode.selectableFor(List.of(GolemCoreType.BLANK))).containsExactly(GolemMode.FOLLOW, GolemMode.STAY, GolemMode.HOME);
    assertThat(GolemMode.selectableFor(List.of())).containsExactly(GolemMode.FOLLOW, GolemMode.STAY, GolemMode.HOME);
    assertThat(GolemGuiActions.cycleMode(List.of(GolemCoreType.BLANK, GolemCoreType.HARVESTER), GolemMode.HOME, true)).isEqualTo(GolemMode.WORK);
  }

  @Test
  void theMasterworkSlotShowsForEveryBodyButBoneOnceTheOwnerCanDesignate() {
    assertThat(GolemGuiActions.showsMasterwork(false, 1)).isTrue();
    assertThat(GolemGuiActions.showsMasterwork(true, 0)).isTrue();
    assertThat(GolemGuiActions.showsMasterwork(false, 0)).isFalse();
  }

  @Test
  void theSecondCoreSlotShowsWithTheDualCorePerkOrWhileASecondCoreIsSlotted() {
    assertThat(GolemGuiActions.showsSecondCore(List.of(GolemCoreType.GUARDIAN), true)).isTrue();
    assertThat(GolemGuiActions.showsSecondCore(List.of(GolemCoreType.GUARDIAN), false)).isFalse();
    assertThat(GolemGuiActions.showsSecondCore(List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER), false)).isTrue();
  }

  @Test
  void everyDesignationAndCoreRefusalHasAMessageWithItsArguments() {
    Set<String> designations = new HashSet<>();
    for (GolemRegistry.Designation designation : GolemRegistry.Designation.values()) {
      designations.add(GolemGuiActions.designationNotice(designation, 2).key());
    }
    Set<String> refusals = new HashSet<>();
    for (GolemMastery.CoreRefusal refusal : GolemMastery.CoreRefusal.values()) {
      refusals.add(GolemGuiActions.coreRefusalNotice(refusal, GolemCoreType.GUARDIAN, GolemBody.IRON).key());
    }

    assertThat(designations).hasSize(GolemRegistry.Designation.values().length);
    assertThat(refusals).hasSize(GolemMastery.CoreRefusal.values().length);
    assertThat(GolemGuiActions.designationNotice(GolemRegistry.Designation.CAP, 2))
        .isEqualTo(Text.key("adapt.golemancy.masterwork.cap").withMarkup("count", String.valueOf(2)));
    assertThat(GolemGuiActions.designationNotice(GolemRegistry.Designation.LOCKED, 2))
        .isEqualTo(Text.key("adapt.golemancy.masterwork.locked"));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.NOT_ALLOWED, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(Text.key("adapt.golemancy.binding.core_not_allowed").with("body", Text.key("adapt.golemancy.body.iron")).with("core", Text.key("adapt.golemancy.role.guardian")));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.LOCKED, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(Text.key("adapt.golemancy.binding.core_locked").with("core", Text.key("adapt.golemancy.role.guardian")));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.NO_SLOT, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(Text.key("adapt.golemancy.masterwork.core_no_slot"));
  }

  private static GolemGuiActions.RuneNote runeNote(boolean active, Text notice) {
    return new GolemGuiActions.RuneNote(notice, active);
  }

  private static GolemRune rune(GolemRuneType aspect, int tier) {
    return new GolemRune(aspect, tier);
  }
}
