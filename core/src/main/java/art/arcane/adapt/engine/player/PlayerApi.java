package art.arcane.adapt.engine.player;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;

/**
 * The engine's player API: the directory behind {@code AdaptApi.players()} and the one entry that posts progress
 * events on the running core's bus. Works with or without an installed engine bridge.
 */
public final class PlayerApi {
  private static final EnginePlayerDirectory DIRECTORY = new EnginePlayerDirectory();

  private PlayerApi() {
  }

  public static EnginePlayerDirectory directory() {
    return DIRECTORY;
  }

  /** The profile of a loaded player, bound to that player's runtime. */
  public static EnginePlayerProfile profile(AdaptPlayer player) {
    return new EnginePlayerProfile(player);
  }

  /** Whether anything on the running core's bus would receive an event of this type. */
  public static boolean listening(Class<? extends AdaptEvent> type) {
    AdaptEventBus bus = bus();
    return bus != null && bus.hasListeners(type);
  }

  /** Posts on the running core's bus; without a running core the event is returned untouched. */
  public static <E extends AdaptEvent> E post(E event) {
    AdaptEventBus bus = bus();
    return bus == null ? event : bus.post(event);
  }

  private static AdaptEventBus bus() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getBus();
  }
}
