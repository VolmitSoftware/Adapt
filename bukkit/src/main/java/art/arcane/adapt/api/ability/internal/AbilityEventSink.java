package art.arcane.adapt.api.ability.internal;

import org.bukkit.event.Event;

public interface AbilityEventSink {
  AbilityEventSink NONE = new AbilityEventSink() {
    @Override
    public boolean hasListeners(Event event) {
      return false;
    }

    @Override
    public void fire(Event event) {
    }
  };

  boolean hasListeners(Event event);

  void fire(Event event);
}
