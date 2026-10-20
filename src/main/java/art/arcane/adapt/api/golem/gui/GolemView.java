package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

record GolemView(GolemBody body, Vitals vitals, CoreSlots cores, GolemMode mode, ChannelSet channels, Anchors anchors,
                 RuneSlots runes, CarriedItems carried, Statistics stats, int essence, Toggles toggles) {
  static GolemView of(GolemHandle handle) {
    GolemState state = handle.state();
    Mob golem = handle.entity();
    GolemState.Stats stats = state.stats();
    GolemRuneEffects effects = GolemRunes.effects(handle);
    UUID owner = handle.owner();
    List<GolemCoreType> slotted = state.cores();
    List<String> stored = state.carried();
    int shown = GolemGuiActions.carriedShown(stored.size());
    return new GolemView(state.body(),
        new Vitals((int) Math.ceil(Math.max(0D, golem.getHealth())), Math.round(Math.max(0D, GolemVitals.maxHealth(golem))), handle.isDormant()),
        new CoreSlots(slotted.isEmpty() ? List.of(state.primaryCore()) : slotted, GolemMastery.perks(handle).dualCore()),
        state.mode(),
        new ChannelSet(effects.channels(state.channels()), effects.channelSlots()),
        new Anchors(state.home(), state.homeChest()),
        new RuneSlots(state.runes(), GolemRunes.layout(state.body(), owner, state.masterwork())),
        new CarriedItems(List.copyOf(stored.subList(0, shown)), stored.size()),
        new Statistics(stats.kills(), String.format(Locale.ROOT, "%.1f", stats.damage()), stats.tasks(), stats.itemsMoved()),
        state.essencePoints(),
        new Toggles(state.snowTrail(), state.colossus(), GolemColossus.form().map(form -> form.available(handle)).orElse(false),
            state.masterwork(), GolemancyRuntime.registry().map(registry -> registry.masterworks(owner)).orElse(0), GolemMastery.maxMasterworks(owner)));
  }

  Set<Section> changes(GolemView previous) {
    Set<Section> changed = EnumSet.noneOf(Section.class);
    if (!vitals.equals(previous.vitals)) {
      changed.add(Section.BODY);
    }
    if (!cores.equals(previous.cores)) {
      changed.add(Section.CORES);
      changed.add(Section.ESSENCE);
    }
    if (mode != previous.mode) {
      changed.add(Section.MODE);
    }
    if (!channels.equals(previous.channels)) {
      changed.add(Section.CHANNELS);
    }
    if (!anchors.equals(previous.anchors)) {
      changed.add(Section.ANCHORS);
    }
    if (!runes.equals(previous.runes)) {
      changed.add(Section.RUNES);
    }
    if (!carried.equals(previous.carried)) {
      changed.add(Section.CARRIED);
    }
    if (!stats.equals(previous.stats)) {
      changed.add(Section.STATS);
    }
    if (essence != previous.essence) {
      changed.add(Section.ESSENCE);
    }
    if (!toggles.equals(previous.toggles)) {
      changed.add(Section.TOGGLES);
    }
    return changed;
  }

  enum Section {
    BODY,
    CORES,
    MODE,
    CHANNELS,
    ANCHORS,
    RUNES,
    CARRIED,
    STATS,
    ESSENCE,
    TOGGLES
  }

  record Vitals(int health, long maxHealth, boolean dormant) {
  }

  record CoreSlots(List<GolemCoreType> slotted, boolean dualCore) {
  }

  record ChannelSet(int mask, int slots) {
  }

  record Anchors(GolemState.Anchor home, GolemState.Anchor chest) {
  }

  record RuneSlots(List<GolemRune> slotted, GolemRuneLayout layout) {
  }

  record CarriedItems(List<String> shown, int total) {
  }

  record Statistics(long kills, String damage, long tasks, long itemsMoved) {
  }

  record Toggles(boolean snowTrail, boolean colossus, boolean colossusAvailable, boolean masterwork, int masterworks, int maxMasterworks) {
  }
}
