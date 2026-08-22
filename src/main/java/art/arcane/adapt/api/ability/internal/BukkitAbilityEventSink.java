package art.arcane.adapt.api.ability.internal;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitAbilityEventSink implements AbilityEventSink {
  private final Logger logger;

  public BukkitAbilityEventSink(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public boolean hasListeners(Event event) {
    return event != null && event.getHandlers().getRegisteredListeners().length > 0;
  }

  @Override
  public void fire(Event event) {
    if (!hasListeners(event)) {
      return;
    }

    try {
      Bukkit.getPluginManager().callEvent(event);
    } catch (Throwable error) {
      logger.log(Level.WARNING, "Failed to dispatch " + event.getEventName(), error);
    }
  }
}
