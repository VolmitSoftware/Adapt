package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemTeleport;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.overseer.OverseerReceiver;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

import java.util.Optional;

final class OverseerGolems implements OverseerReceiver {
  @Override
  public void receive(OverseerSignal signal) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (GolemHandle handle : registry.get().loadedOf(signal.owner())) {
      if (!handle.isDormant()) {
        J.runEntity(handle.entity(), () -> answer(handle, signal));
      }
    }
  }

  private void answer(GolemHandle handle, OverseerSignal signal) {
    Mob golem = handle.entity();
    if (handle.isRetired() || handle.isDormant() || !golem.isValid()) {
      return;
    }
    Location at = golem.getLocation();
    OverseerRules.Hearing hearing = new OverseerRules.Hearing(signal.signal(), handle.body(), handle.core(), handle.state().cores(),
        signal.hears(GolemRunes.channels(handle)), signal.sameWorld(at), signal.inRange(at), signal.rally() != null);
    switch (OverseerRules.answer(hearing)) {
      case SKIP -> {
      }
      case MODE -> {
        handle.setMode(signal.signal().mode().orElseThrow());
        signal.reached(golem);
      }
      case RALLY -> rally(handle, signal);
      case COMMAND -> command(handle, signal);
    }
  }

  private void rally(GolemHandle handle, OverseerSignal signal) {
    if (!signal.claimRally()) {
      return;
    }
    Mob golem = handle.entity();
    handle.setMode(GolemMode.FOLLOW);
    GolemTeleport.teleport(handle, signal.origin()).whenComplete((moved, failure) -> {
      if (failure != null) {
        Adapt.warn("Rally teleport failed for bound golem " + handle.id() + " of " + signal.owner() + ".", failure);
        return;
      }
      if (Boolean.TRUE.equals(moved)) {
        J.runEntity(golem, () -> signal.reached(golem));
      }
    });
  }

  private void command(GolemHandle handle, OverseerSignal signal) {
    if (signal.target() == null) {
      return;
    }
    Mob golem = handle.entity();
    GolemancyRuntime.brain(golem).ifPresent(brain -> {
      brain.command(signal.target(), signal.commandUntilMillis());
      signal.reached(golem);
    });
  }
}
