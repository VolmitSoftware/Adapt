package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCombat;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

final class OverseerGolems implements OverseerReceiver {
  private final DoubleSupplier sentryRange;
  private final BiConsumer<UUID, Integer> ignoredNote;

  OverseerGolems(DoubleSupplier sentryRange, BiConsumer<UUID, Integer> ignoredNote) {
    this.sentryRange = Objects.requireNonNull(sentryRange);
    this.ignoredNote = Objects.requireNonNull(ignoredNote);
  }

  @Override
  public void receive(OverseerSignal signal) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    List<GolemHandle> hearing = new ArrayList<>();
    for (GolemHandle handle : registry.get().loadedOf(signal.owner())) {
      if (!handle.isDormant()) {
        hearing.add(handle);
      }
    }
    Ignores ignores = new Ignores(hearing.size(), count -> ignoredNote.accept(signal.owner(), count));
    for (GolemHandle handle : hearing) {
      dispatch(handle, signal, ignores);
    }
  }

  private void dispatch(GolemHandle handle, OverseerSignal signal, Ignores ignores) {
    AtomicBoolean settled = new AtomicBoolean();
    Runnable settle = () -> {
      if (settled.compareAndSet(false, true)) {
        ignores.settled();
      }
    };
    boolean queued = J.runEntity(handle.entity(), () -> {
      try {
        answer(handle, signal, ignores);
      } finally {
        settle.run();
      }
    }, settle);
    if (!queued) {
      settle.run();
    }
  }

  private void answer(GolemHandle handle, OverseerSignal signal, Ignores ignores) {
    Mob golem = handle.entity();
    if (handle.isRetired() || handle.isDormant() || !golem.isValid()) {
      return;
    }
    Location at = golem.getLocation();
    OverseerRules.Hearing hearing = new OverseerRules.Hearing(signal.signal(), handle.body(), GolemMastery.cores(handle),
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
      case COMMAND -> command(handle, signal, ignores);
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
    if (!J.isOwnedByCurrentRegion(target)) {
      return false;
    }
    Location targetAt = target.getLocation();
    double distanceSquared = Objects.equals(targetAt.getWorld(), at.getWorld()) ? targetAt.distanceSquared(at) : Double.NaN;
    return OverseerRules.reaches(handle.body(), distanceSquared, sentryRange.getAsDouble());
  }

  private static void setMode(GolemHandle handle, GolemMode mode) {
    handle.setMode(mode);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
  }

  private void command(GolemHandle handle, OverseerSignal signal, Ignores ignores) {
    if (signal.target() == null) {
      return;
    }
    if (!GolemCombat.enabled(handle)) {
      ignores.ignored();
      return;
    }
    Mob golem = handle.entity();
    GolemancyRuntime.brain(golem).ifPresent(brain -> {
      brain.command(signal.target(), signal.commandUntilMillis());
      signal.reached(golem);
    });
  }

  private static final class Ignores {
    private final AtomicInteger pending;
    private final AtomicInteger ignored = new AtomicInteger();
    private final IntConsumer report;

    private Ignores(int golems, IntConsumer report) {
      this.pending = new AtomicInteger(golems);
      this.report = report;
    }

    private void ignored() {
      ignored.incrementAndGet();
    }

    private void settled() {
      if (pending.decrementAndGet() == 0 && ignored.get() > 0) {
        report.accept(ignored.get());
      }
    }
  }
}
