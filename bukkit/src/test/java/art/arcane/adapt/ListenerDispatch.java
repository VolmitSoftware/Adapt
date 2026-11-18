package art.arcane.adapt;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ListenerDispatch {
  private ListenerDispatch() {
  }

  public static void dispatch(Plugin plugin, Event event, Listener... listeners) {
    List<RegisteredListener> handlers = new ArrayList<>();
    for (Listener listener : listeners) {
      collectHandlers(plugin, listener, event, handlers);
    }
    handlers.sort(Comparator.comparing(RegisteredListener::getPriority));
    try {
      for (RegisteredListener handler : handlers) {
        handler.callEvent(event);
      }
    } catch (EventException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

  private static void collectHandlers(Plugin plugin, Listener listener, Event event, List<RegisteredListener> handlers) {
    for (Method method : listener.getClass().getDeclaredMethods()) {
      EventHandler annotation = method.getAnnotation(EventHandler.class);
      if (annotation == null || method.getParameterCount() != 1 || !method.getParameterTypes()[0].isInstance(event)) {
        continue;
      }
      method.setAccessible(true);
      handlers.add(new RegisteredListener(listener, (target, fired) -> invoke(method, target, fired), annotation.priority(), plugin, annotation.ignoreCancelled()));
    }
  }

  private static void invoke(Method method, Listener target, Event event) throws EventException {
    try {
      method.invoke(target, event);
    } catch (InvocationTargetException e) {
      throw new EventException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new EventException(e);
    }
  }
}
