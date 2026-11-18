package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.block.BlockBrushEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.state;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Events only some Bukkit servers have (Spigot's brush event), looked up when the server starts. */
final class SpigotTranslations {
  static final String BRUSH_EVENT = "org.bukkit.event.block.BlockBrushEvent";

  private SpigotTranslations() {
  }

  static List<Translation<?>> all() {
    Translation<?> brush = brush(BRUSH_EVENT);
    return brush == null ? List.of() : List.of(brush);
  }

  static Translation<?> brush(String className) {
    try {
      Class<? extends Event> type = Class.forName(className).asSubclass(Event.class);
      if (!Cancellable.class.isAssignableFrom(type)) {
        return null;
      }
      return brush(type, type.getMethod("getPlayer"), type.getMethod("getBlock"), type.getMethod("getNewState"));
    } catch (ReflectiveOperationException | LinkageError | ClassCastException absent) {
      return null;
    }
  }

  private static <B extends Event> Translation<B> brush(Class<B> type, Method getPlayer, Method getBlock, Method getNewState) {
    return new Translation<>(type, List.of(BlockBrushEvent.class)) {
      @Override
      public void bridge(B event, Poster poster) {
        Object player;
        Object brushed;
        Object newState;
        try {
          player = getPlayer.invoke(event);
          brushed = getBlock.invoke(event);
          newState = getNewState.invoke(event);
        } catch (ReflectiveOperationException failure) {
          throw new IllegalStateException("Cannot read " + type.getName(), failure);
        }
        if (!(player instanceof Player who) || !(brushed instanceof Block where)) {
          return;
        }
        Cancellable cancellable = (Cancellable) event;
        boolean cancelled = cancellable.isCancelled();
        BlockBrushEvent core = new BlockBrushEvent(player(who), block(where),
            newState instanceof BlockState state ? state(state) : null);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(cancellable, cancelled, core);
      }
    };
  }
}
