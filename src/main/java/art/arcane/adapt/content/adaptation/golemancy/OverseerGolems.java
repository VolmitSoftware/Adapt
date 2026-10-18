package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;

final class OverseerGolems implements OverseerReceiver {
  private final DoubleSupplier sentryRange;

  OverseerGolems(DoubleSupplier sentryRange) {
    this.sentryRange = Objects.requireNonNull(sentryRange);
  }

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
        signal.hears(GolemRunes.channels(handle)), signal.sameWorld(at), signal.inRange(at), reachable(handle, signal.target(), at),
        signal.rally() != null);
    switch (OverseerRules.answer(hearing)) {
      case SKIP -> {
      }
      case MODE -> {
        GolemancyRuntime.brain(golem).ifPresent(GolemBrain::clearCommand);
        setMode(handle, signal.signal().mode().orElseThrow());
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
    GolemancyRuntime.brain(golem).ifPresent(GolemBrain::clearCommand);
    setMode(handle, GolemMode.FOLLOW);
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

  private boolean reachable(GolemHandle handle, LivingEntity target, Location at) {
    if (target == null || handle.body() != GolemBody.FROST) {
      return true;
    }
    Location targetAt = target.getLocation();
    double distanceSquared = Objects.equals(targetAt.getWorld(), at.getWorld()) ? targetAt.distanceSquared(at) : Double.NaN;
    return OverseerRules.reaches(handle.body(), distanceSquared, sentryRange.getAsDouble());
  }

  private static void setMode(GolemHandle handle, GolemMode mode) {
    handle.setMode(mode);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
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
