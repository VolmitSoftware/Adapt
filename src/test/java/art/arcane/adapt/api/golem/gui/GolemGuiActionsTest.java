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
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;

class GolemGuiActionsTest {
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
    assertThat(GolemGuiActions.snowTrailIcon(true)).isEqualTo(Material.SNOW_BLOCK);
    assertThat(GolemGuiActions.snowTrailIcon(false)).isEqualTo(Material.SNOWBALL);
    assertThat(GolemGuiActions.snowTrailName(true)).isEqualTo(GolemancyMessages.GUI_SNOW_TRAIL_ON);
    assertThat(GolemGuiActions.snowTrailName(false)).isEqualTo(GolemancyMessages.GUI_SNOW_TRAIL_OFF);
  }

  @Test
  void theCombatToggleShowsItsStateAndTheGuardianLock() {
    assertThat(GolemGuiActions.combatIcon(true, false)).isEqualTo(Material.IRON_SWORD);
    assertThat(GolemGuiActions.combatIcon(false, false)).isEqualTo(Material.BARRIER);
    assertThat(GolemGuiActions.combatIcon(true, true)).isEqualTo(Material.SHIELD);
    assertThat(GolemGuiActions.combatName(true, false)).isEqualTo(GolemancyMessages.GUI_COMBAT_ON);
    assertThat(GolemGuiActions.combatName(false, false)).isEqualTo(GolemancyMessages.GUI_COMBAT_OFF);
    assertThat(GolemGuiActions.combatName(true, true)).isEqualTo(GolemancyMessages.GUI_COMBAT_LOCKED);
    assertThat(GolemGuiActions.combatLore(true, false)).isEqualTo(GolemancyMessages.GUI_COMBAT_ON_LORE);
    assertThat(GolemGuiActions.combatLore(false, false)).isEqualTo(GolemancyMessages.GUI_COMBAT_OFF_LORE);
    assertThat(GolemGuiActions.combatLore(true, true)).isEqualTo(GolemancyMessages.GUI_COMBAT_LOCKED_LORE);
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
        .containsExactly(GolemancyMessages.CHANNEL_WHITE, GolemancyMessages.CHANNEL_RED);
    assertThat(GolemGuiActions.channelNames(ChannelMask.NONE)).containsExactly(GolemancyMessages.CHANNEL_NONE);
  }

  @Test
  void slottedRunesSayWhetherTheyWorkAndWhatUnlocksThem() {
    GolemRuneLayout layout = new GolemRuneLayout(2, List.of(1, 3, 5), 1, List.of(1, 3, 0), Set.of(GolemRuneType.EMBER));
    List<GolemRune> runes = List.of(rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.MIGHT, 2), rune(GolemRuneType.TIDE, 1));

    assertThat(GolemGuiActions.runeNote(layout, runes, 0, GolemBody.IRON)).isEqualTo(runeNote(true, GolemancyMessages.GUI_RUNE_ACTIVE));
    assertThat(GolemGuiActions.runeNote(layout, runes, 1, GolemBody.IRON))
        .isEqualTo(runeNote(false, GolemancyMessages.GUI_RUNE_OVER_TIER, trusted("tier", 2), trusted("level", 3)));
    assertThat(GolemGuiActions.runeNote(layout, runes, 2, GolemBody.IRON))
        .isEqualTo(runeNote(false, GolemancyMessages.GUI_RUNE_SLOT_LOCKED, trusted("level", 5)));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.EMBER, 1)), 0, GolemBody.FROST)).isEqualTo(runeNote(false,
        GolemancyMessages.GUI_RUNE_BLOCKED, trusted("aspect", GolemGuiSupport.localized(GolemancyMessages.ASPECT_EMBER)),
        trusted("body", GolemGuiSupport.localized(GolemancyMessages.BODY_FROST))));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.ORDER, 1), rune(GolemRuneType.ORDER, 1)), 1, GolemBody.IRON))
        .isEqualTo(runeNote(false, GolemancyMessages.GUI_RUNE_DUPLICATE, trusted("aspect", GolemGuiSupport.localized(GolemancyMessages.ASPECT_ORDER))));
    assertThat(GolemGuiActions.runeNote(layout, List.of(rune(GolemRuneType.BULWARK, 3)), 0, GolemBody.IRON))
        .isEqualTo(runeNote(false, GolemancyMessages.GUI_RUNE_UNPOWERED));
    assertThat(GolemGuiActions.runeNote(GolemRuneLayout.NONE, List.of(rune(GolemRuneType.ORDER, 1)), 0, GolemBody.IRON))
        .isEqualTo(runeNote(false, GolemancyMessages.GUI_RUNE_UNPOWERED));
  }

  @Test
  void refusedRunesTellThePlayerWhy() {
    TextKey aspect = GolemancyMessages.ASPECT_MIGHT;
    TextKey body = GolemancyMessages.BODY_BONE;
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.NO_SLOT, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(notice(GolemancyMessages.MESSAGE_RUNE_NO_SLOT));
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.DUPLICATE, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(notice(GolemancyMessages.MESSAGE_RUNE_DUPLICATE, trusted("aspect", GolemGuiSupport.localized(aspect))));
    assertThat(GolemGuiActions.refusalNotice(GolemRuneLayout.Refusal.BLOCKED, GolemRuneType.MIGHT, GolemBody.BONE))
        .isEqualTo(notice(GolemancyMessages.MESSAGE_RUNE_BLOCKED, trusted("aspect", GolemGuiSupport.localized(aspect)),
            trusted("body", GolemGuiSupport.localized(body))));
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
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.SHATTERED)).isEqualTo(GolemancyMessages.ROSTER_RELEASE_SHATTERED_CONFIRM);
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.PACKED)).isEqualTo(GolemancyMessages.ROSTER_RELEASE_PACKED_CONFIRM);
    assertThat(GolemGuiActions.releaseConfirm(GolemRoster.Status.MISSING)).isEqualTo(GolemancyMessages.ROSTER_RELEASE_CONFIRM);
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
    Set<TextKey> designations = new HashSet<>();
    for (GolemRegistry.Designation designation : GolemRegistry.Designation.values()) {
      designations.add(GolemGuiActions.designationNotice(designation, 2).key());
    }
    Set<TextKey> refusals = new HashSet<>();
    for (GolemMastery.CoreRefusal refusal : GolemMastery.CoreRefusal.values()) {
      refusals.add(GolemGuiActions.coreRefusalNotice(refusal, GolemCoreType.GUARDIAN, GolemBody.IRON).key());
    }

    assertThat(designations).hasSize(GolemRegistry.Designation.values().length);
    assertThat(refusals).hasSize(GolemMastery.CoreRefusal.values().length);
    assertThat(GolemGuiActions.designationNotice(GolemRegistry.Designation.CAP, 2))
        .isEqualTo(notice(GolemancyMessages.MASTERWORK_CAP, trusted("count", 2)));
    assertThat(GolemGuiActions.designationNotice(GolemRegistry.Designation.LOCKED, 2))
        .isEqualTo(notice(GolemancyMessages.MASTERWORK_LOCKED));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.NOT_ALLOWED, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(notice(GolemancyMessages.BINDING_CORE_NOT_ALLOWED, trusted("body", GolemGuiSupport.localized(GolemancyMessages.BODY_IRON)),
            trusted("core", GolemGuiSupport.localized(GolemancyMessages.ROLE_GUARDIAN))));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.LOCKED, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(notice(GolemancyMessages.BINDING_CORE_LOCKED, trusted("core", GolemGuiSupport.localized(GolemancyMessages.ROLE_GUARDIAN))));
    assertThat(GolemGuiActions.coreRefusalNotice(GolemMastery.CoreRefusal.NO_SLOT, GolemCoreType.GUARDIAN, GolemBody.IRON))
        .isEqualTo(notice(GolemancyMessages.MASTERWORK_CORE_NO_SLOT));
  }

  private static GolemGuiActions.Notice notice(TextKey key, MessageArgument... arguments) {
    return new GolemGuiActions.Notice(key, List.of(arguments));
  }

  private static GolemGuiActions.RuneNote runeNote(boolean active, TextKey key, MessageArgument... arguments) {
    return new GolemGuiActions.RuneNote(notice(key, arguments), active);
  }

  private static GolemRune rune(GolemRuneType aspect, int tier) {
    return new GolemRune(aspect, tier);
  }
}
