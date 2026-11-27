package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.projectile.ProjectileReplacements;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Replaces a projectile with a copy flying a new way, handing its pickup ownership to the copy. Failures are logged once
 * per operation, world and error type.
 */
public final class RicochetHandoff {
  private final String owner;
  private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

  public RicochetHandoff(String owner) {
    this.owner = owner;
  }

  /** The copy of {@code source} now flying from {@code location}, or null when the handoff failed and nothing changed. */
  public ProjectileRef relaunch(ProjectileRef source, Loc location, Vec3 velocity, PlayerRef shooter,
                                Consumer<ProjectileRef> hydrate) {
    RicochetReplacementTicket replacement = begin(source);
    if (replacement == null) {
      return null;
    }
    ProjectileRef copy = spawn(source, location, velocity, shooter, hydrate);
    if (copy == null) {
      cancel(replacement);
      return null;
    }
    if (!complete(replacement, copy)) {
      copy.remove();
      return null;
    }
    return copy;
  }

  RicochetReplacementTicket begin(ProjectileRef source) {
    try {
      return RicochetReplacementTicket.of(ProjectileReplacements.begin(source));
    } catch (RuntimeException | Error error) {
      report("begin projectile ownership transfer", source.world(), source.location(), error);
      return null;
    }
  }

  boolean complete(RicochetReplacementTicket replacement, ProjectileRef target) {
    try {
      return replacement.complete(target);
    } catch (RuntimeException | Error error) {
      report("complete projectile ownership transfer", target.world(), target.location(), error);
      return false;
    }
  }

  void cancel(RicochetReplacementTicket replacement) {
    if (replacement == null) {
      return;
    }
    try {
      replacement.cancel();
    } catch (RuntimeException | Error error) {
      AdaptApi.log().warn("Failed to cancel a " + owner + " projectile ownership transfer.");
      AdaptApi.log().error("RicochetHandoff failed", error);
    }
  }

  void report(String operation, WorldRef world, Loc location, Throwable throwable) {
    String failureKey = operation + ":" + world.uid() + ":" + throwable.getClass().getName();
    if (!reportedFailures.add(failureKey)) {
      return;
    }
    AdaptApi.log().warn("Failed to " + operation + " for " + owner + " in " + world.name()
        + " at " + location.blockX() + "," + location.blockY() + ","
        + location.blockZ() + ".");
    AdaptApi.log().error("RicochetHandoff failed", throwable);
  }

  private ProjectileRef spawn(ProjectileRef source, Loc location, Vec3 velocity, PlayerRef shooter,
                              Consumer<ProjectileRef> hydrate) {
    ProjectileSnapshot snapshot = source.snapshot();
    if (snapshot == null) {
      return null;
    }

    try {
      return snapshot.spawn(location, velocity, shooter, hydrate);
    } catch (RuntimeException exception) {
      report("spawn local projectile", source.world(), location, exception);
      return null;
    }
  }
}
