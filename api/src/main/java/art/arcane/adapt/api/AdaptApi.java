package art.arcane.adapt.api;

import art.arcane.adapt.api.ability.AbilityHooks;
import art.arcane.adapt.api.addon.AddonRegistry;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.event.EventRegistry;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.api.protection.ProtectionHooks;
import art.arcane.adapt.api.service.ServiceRegistry;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformLog;
import org.jetbrains.annotations.ApiStatus;

/**
 * The entry point of the Adapt API. One class, the same on Paper, Folia, Spigot, Fabric, Forge and NeoForge.
 *
 * <p>The accessors fall into two groups.
 *
 * <p><b>Registries</b> work at any time, also before Adapt has started, and keep what is registered across
 * restarts of Adapt's core: {@link #addons()}, {@link #events()}, {@link #abilities()}, {@link #protection()},
 * {@link #services()}.
 *
 * <p><b>Queries</b> need a running core and throw {@link IllegalStateException} without one:
 * {@link #content()}, {@link #players()}, {@link #platform()}, {@link #settings()}, {@link #log()}.
 *
 * <p><b>Time</b> works at any time and never throws: {@link #scheduler()} does nothing while no core runs, and
 * {@link #clock()} falls back to system time. A core runs from {@code AdaptStartedEvent} to {@code AdaptStoppingEvent}; on a mod platform that
 * is one server run, and a client that opens several worlds sees several. Code inside a skill or adaptation always
 * has a running core.
 *
 * <p><b>Compatibility.</b> A minor release may add constants to any enum, methods to interfaces Adapt implements
 * (marked {@code NonExtendable}) and accessors to values Adapt creates, so a {@code switch} over an Adapt enum needs a
 * {@code default} branch. Members marked {@code ApiStatus.Internal} are for Adapt's platform hosts and may change at
 * any time; {@code ApiStatus.Experimental} types may change in a minor release.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptApi {
  /** The API version, independent of the Adapt version. Semantic: a new minor only adds. */
  public static final String VERSION = "1.0.0";

  private AdaptApi() {
  }

  /** Whether a core is running, so the query accessors work. */
  public static boolean isRunning() {
    return Engine.isRunning();
  }

  /**
   * Whether the running core has begun to stop: true from {@code AdaptStoppingEvent} until it is gone. Work that
   * would outlive the core, such as a restore scheduled for later, checks it; false when no core runs.
   */
  @ApiStatus.Experimental
  public static boolean isStopping() {
    return Engine.isRunning() && Engine.bridge().isStopping();
  }

  /** Where addons install and uninstall themselves. Usable at any time. */
  public static AddonRegistry addons() {
    return Engine.addons();
  }

  /** Where listeners subscribe to Adapt's progression and lifecycle events. Usable at any time. */
  public static EventRegistry events() {
    return Engine.events();
  }

  /** Where use policies and cost providers hook into ability activation. Usable at any time. */
  public static AbilityHooks abilities() {
    return Engine.abilities();
  }

  /** Where protection plugins register the checks that gate adaptations. Usable at any time. */
  public static ProtectionHooks protection() {
    return Engine.protectors();
  }

  /** Where addons publish services for each other and for Adapt. Usable at any time. */
  public static ServiceRegistry services() {
    return Engine.services();
  }

  /** The skills and adaptations currently installed. Needs a running core. */
  public static ContentCatalog content() {
    return Engine.bridge().content();
  }

  /** The Adapt profiles of online players. Needs a running core. */
  public static PlayerDirectory players() {
    return Engine.bridge().players();
  }

  /**
   * The platform Adapt runs on: players, worlds, items, registries, menus, effects. Needs a running core. Its hosts
   * forward to the platform's own and leave out what only Adapt may do; permissions and recipes an addon registers
   * are removed through the registration they return.
   */
  public static AdaptPlatform platform() {
    return Engine.bridge().platform();
  }

  /**
   * Schedules work on the thread that owns an entity or a location, which matters on Folia. Usable at any time:
   * while no core runs, and for a null target or task, {@code run*} return false, {@code runAsync} and
   * {@code repeat*} return an already cancelled handle and ownership checks are false; nothing is ever null. Negative
   * delays count as 0 and periods below 1 as 1. Cancel your own tasks through their handles.
   */
  public static AdaptScheduler scheduler() {
    return Engine.scheduler();
  }

  /** Wall time and the server tick; system time and tick 0 while no core runs. */
  public static AdaptClock clock() {
    return Engine.clock();
  }

  /** The server-wide settings from {@code adapt.toml}. Needs a running core. */
  public static AdaptSettings settings() {
    return Engine.bridge().settings();
  }

  /** Adapt's log, for addon messages that belong with Adapt's own; {@code java.util.logging} while no core runs. */
  public static PlatformLog log() {
    return Engine.log();
  }
}
