package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.AdaptClock;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformLog;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Where the engine plugs into the API. The API module depends on nothing, so the engine installs an
 * {@link EngineBridge} here when a core starts and removes it when the core stops.
 *
 * <p>What other plugins register (addons, listeners, ability hooks, protectors, services) is kept on this side, in
 * the API jar, so it survives every core restart. On mod platforms a core is rebuilt at each server start.
 *
 * <p>Not API. Addons call {@link art.arcane.adapt.api.AdaptApi}.
 */
@ApiStatus.Internal
public final class Engine {
  private static final InstalledAddons ADDONS = new InstalledAddons();
  private static final EventSubscriptions EVENTS = new EventSubscriptions();
  private static final AbilityHookList ABILITIES = new AbilityHookList();
  private static final ProtectorList PROTECTORS = new ProtectorList();
  private static final Services SERVICES = new Services();
  private static volatile EngineBridge bridge;
  private static HostKey host;

  private Engine() {
  }

  /**
   * Hands out the one {@link HostKey}. The first call wins; every later call throws {@link IllegalStateException}
   * naming the class that claimed first. Adapt's platform hosts claim in their bootstrap (the Bukkit plugin's
   * {@code onLoad}, the mod bootstrap's constructor), before any addon code runs. When other code claims first, Adapt
   * refuses to start and its error names that claimant.
   */
  public static synchronized HostKey claimHost() {
    Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
    if (host != null) {
      throw new IllegalStateException("The Adapt host key was already claimed by " + host.claimant().getName()
          + "; " + caller.getName() + " cannot claim it, and Adapt does not start while another party holds it");
    }
    host = new HostKey(caller);
    return host;
  }

  /** Throws unless {@code key} is the claimed host key. */
  public static void requireHost(HostKey key) {
    if (key == null || key != claimed()) {
      throw new IllegalStateException("Only Adapt's platform host may do this");
    }
  }

  private static synchronized HostKey claimed() {
    return host;
  }

  /** Installs a started core. It then applies every installed addon and binds every subscribed listener. */
  public static synchronized void start(HostKey key, EngineBridge started) {
    requireHost(key);
    Objects.requireNonNull(started, "started");
    if (bridge != null) {
      throw new IllegalStateException("An Adapt core is already running");
    }
    bridge = started;
  }

  /** Removes a stopping core; a call for a core that is not the running one does nothing. */
  public static synchronized void stop(HostKey key, EngineBridge stopping) {
    requireHost(key);
    if (bridge == stopping) {
      bridge = null;
    }
  }

  public static boolean isRunning() {
    return bridge != null;
  }

  /** The running core. Throws when none is running. */
  public static EngineBridge bridge() {
    EngineBridge current = bridge;
    if (current == null) {
      throw new IllegalStateException("Adapt is not running. Content is built by Adapt from the suppliers an addon "
          + "registers; queries are valid between AdaptStartedEvent and AdaptStoppingEvent.");
    }
    return current;
  }

  /** The running core, or null. */
  static EngineBridge current() {
    return bridge;
  }

  /** The key, for this package's calls into the running core; null before a host claimed it. */
  static HostKey key() {
    return claimed();
  }

  /** The scheduler addons use: the running core's, or one that does nothing while no core runs. */
  public static AdaptScheduler scheduler() {
    return CoreScheduler.INSTANCE;
  }

  /** The running core's clock, or system time while no core runs. */
  public static AdaptClock clock() {
    return CoreClock.INSTANCE;
  }

  /** The running core's log, or {@code java.util.logging} while no core runs. */
  public static PlatformLog log() {
    return CoreLog.INSTANCE;
  }

  public static InstalledAddons addons() {
    return ADDONS;
  }

  public static EventSubscriptions events() {
    return EVENTS;
  }

  public static AbilityHookList abilities() {
    return ABILITIES;
  }

  public static ProtectorList protectors() {
    return PROTECTORS;
  }

  public static Services services() {
    return SERVICES;
  }
}
