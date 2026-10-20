package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemEquipmentSlot;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemToolNote;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static art.arcane.adapt.api.golem.gui.GolemView.Section.ANCHORS;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.BODY;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.CARRIED;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.CHANNELS;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.CORES;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.EQUIPMENT;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.ESSENCE;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.MODE;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.RUNES;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.STATS;
import static art.arcane.adapt.api.golem.gui.GolemView.Section.TOGGLES;
import static org.assertj.core.api.Assertions.assertThat;

class GolemViewTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final GolemRuneLayout LAYOUT = new GolemRuneLayout(2, List.of(1, 2), 3, List.of(1, 2, 3), Set.of());

  @Test
  void anUnchangedGolemShowsNoChange() {
    assertThat(base().changes(base())).isEmpty();
  }

  @Test
  void aCarriedStackThatGrewShowsOnlyTheCarriedChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(),
        new GolemView.CarriedItems(List.of("cobblestone-13", "oak-4"), 2), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(CARRIED);
  }

  @Test
  void aNewCarriedStackShowsTheCarriedChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(),
        new GolemView.CarriedItems(List.of("cobblestone-12", "oak-4", "dirt-1"), 3), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(CARRIED);
  }

  @Test
  void theMoreCarriedCountShowsTheCarriedChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(),
        new GolemView.CarriedItems(List.of("cobblestone-12", "oak-4"), 40), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(CARRIED);
  }

  @Test
  void healthShowsTheBodyChange() {
    GolemView next = new GolemView(GolemBody.IRON, new GolemView.Vitals(19, 100, false, ""), cores(), GolemMode.WORK, channels(), anchors(),
        runes(), carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(BODY);
  }

  @Test
  void maximumHealthShowsTheBodyChange() {
    GolemView next = new GolemView(GolemBody.IRON, new GolemView.Vitals(20, 120, false, ""), cores(), GolemMode.WORK, channels(), anchors(),
        runes(), carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(BODY);
  }

  @Test
  void dormancyShowsTheBodyChange() {
    GolemView next = new GolemView(GolemBody.IRON, new GolemView.Vitals(20, 100, true, ""), cores(), GolemMode.WORK, channels(), anchors(),
        runes(), carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(BODY);
  }

  @Test
  void essenceShowsTheEssenceChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(),
        9, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(ESSENCE);
  }

  @Test
  void modeShowsTheModeChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.FOLLOW, channels(), anchors(), runes(), carried(), stats(),
        0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(MODE);
  }

  @Test
  void aSlottedCoreShowsTheCoreAndEssenceChanges() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), new GolemView.CoreSlots(List.of(GolemCoreType.BREAKER, GolemCoreType.GATHERER), true),
        GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactlyInAnyOrder(CORES, ESSENCE);
  }

  @Test
  void aSlottedRuneShowsTheRuneChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(),
        new GolemView.RuneSlots(List.of(new GolemRune(GolemRuneType.MIGHT, 1), new GolemRune(GolemRuneType.EMBER, 2)), LAYOUT, GolemMastery.Perks.NONE),
        carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(RUNES);
  }

  @Test
  void aRuneTierShowsTheRuneChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(),
        new GolemView.RuneSlots(List.of(new GolemRune(GolemRuneType.MIGHT, 2)), LAYOUT, GolemMastery.Perks.NONE), carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(RUNES);
  }

  @Test
  void aUnlockedRuneSlotShowsTheRuneChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(),
        new GolemView.RuneSlots(List.of(new GolemRune(GolemRuneType.MIGHT, 1)), new GolemRuneLayout(1, List.of(1, 2), 3, List.of(1, 2, 3), Set.of()), GolemMastery.Perks.NONE),
        carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(RUNES);
  }

  @Test
  void theStatisticsShowTheStatsChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(),
        new GolemView.Statistics(3, "1.5", 5, 9), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(STATS);
  }

  @Test
  void theChannelsShowTheChannelChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, new GolemView.ChannelSet(4, 1), anchors(), runes(),
        carried(), stats(), 0, toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(CHANNELS);
  }

  @Test
  void theHomeChestShowsTheAnchorChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(),
        new GolemView.Anchors(GolemState.Anchor.of(WORLD, 1, 64, 1), GolemState.Anchor.of(WORLD, 2, 64, 2), null, null, 16D, 48D), runes(), carried(), stats(), 0,
        toggles(), equipment());

    assertThat(next.changes(base())).containsExactly(ANCHORS);
  }

  @Test
  void aToolNoteOrAHeldToolShowsTheAnchorChange() {
    GolemView noted = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(),
        new GolemView.Anchors(GolemState.Anchor.of(WORLD, 1, 64, 1), null, new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_ROD, null), null, 16D, 48D), runes(),
        carried(), stats(), 0, toggles(), equipment());
    GolemView holding = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(),
        new GolemView.Anchors(GolemState.Anchor.of(WORLD, 1, 64, 1), null, null, "rod", 16D, 48D), runes(), carried(), stats(), 0, toggles(), equipment());

    assertThat(noted.changes(base())).containsExactly(ANCHORS);
    assertThat(holding.changes(base())).containsExactly(ANCHORS);
  }

  @Test
  void aMasterworkShowsTheToggleChange() {
    GolemView next = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0,
        new GolemView.Toggles(false, false, false, true, 1, 2, true, false), equipment());

    assertThat(next.changes(base())).containsExactly(TOGGLES);
  }

  @Test
  void aCombatChangeShowsTheToggleChange() {
    GolemView off = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0,
        new GolemView.Toggles(false, false, false, false, 0, 0, false, false), equipment());

    assertThat(off.changes(base())).containsExactly(TOGGLES);
    assertThat(base().changes(off)).containsExactly(TOGGLES);
  }

  @Test
  void aCombatLockShowsTheToggleChange() {
    GolemView locked = new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0,
        new GolemView.Toggles(false, false, false, false, 0, 0, true, true), equipment());

    assertThat(locked.changes(base())).containsExactly(TOGGLES);
  }

  @Test
  void aWornItemShowsOnlyTheEquipmentChange() {
    GolemView next = new GolemView(GolemBody.BONE, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0, toggles(),
        new GolemView.Equipment(Map.of(GolemEquipmentSlot.HELMET, "iron_helmet-1")));
    GolemView bone = new GolemView(GolemBody.BONE, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0, toggles(),
        equipment());

    assertThat(next.changes(bone)).containsExactly(EQUIPMENT);
    assertThat(next.changes(next)).isEmpty();
  }

  @Test
  void severalChangesAreAllReported() {
    GolemView next = new GolemView(GolemBody.IRON, new GolemView.Vitals(10, 100, false, ""), cores(), GolemMode.STAY, channels(), anchors(), runes(),
        new GolemView.CarriedItems(List.of("cobblestone-1"), 1), stats(), 3, toggles(), equipment());

    assertThat(next.changes(base())).containsExactlyInAnyOrder(BODY, MODE, CARRIED, ESSENCE);
  }

  private static GolemView base() {
    return new GolemView(GolemBody.IRON, vitals(), cores(), GolemMode.WORK, channels(), anchors(), runes(), carried(), stats(), 0, toggles(), equipment());
  }

  private static GolemView.Vitals vitals() {
    return new GolemView.Vitals(20, 100, false, "");
  }

  private static GolemView.CoreSlots cores() {
    return new GolemView.CoreSlots(List.of(GolemCoreType.BREAKER), false);
  }

  private static GolemView.ChannelSet channels() {
    return new GolemView.ChannelSet(0, 1);
  }

  private static GolemView.Anchors anchors() {
    return new GolemView.Anchors(GolemState.Anchor.of(WORLD, 1, 64, 1), null, null, null, 16D, 48D);
  }

  private static GolemView.RuneSlots runes() {
    return new GolemView.RuneSlots(List.of(new GolemRune(GolemRuneType.MIGHT, 1)), LAYOUT, GolemMastery.Perks.NONE);
  }

  private static GolemView.CarriedItems carried() {
    return new GolemView.CarriedItems(List.of("cobblestone-12", "oak-4"), 2);
  }

  private static GolemView.Statistics stats() {
    return new GolemView.Statistics(3, "1.5", 4, 5);
  }

  private static GolemView.Equipment equipment() {
    return new GolemView.Equipment(Map.of());
  }

  private static GolemView.Toggles toggles() {
    return new GolemView.Toggles(false, false, false, false, 0, 0, true, false);
  }
}
