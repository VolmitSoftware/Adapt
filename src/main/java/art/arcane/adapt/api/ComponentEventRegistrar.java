/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.damage.DamageEventDispatcher;
import art.arcane.adapt.api.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentEventRegistrar {
  private static final Map<Plugin, DamageEventDispatcher> DAMAGE_DISPATCHERS = new IdentityHashMap<>();

  private ComponentEventRegistrar() {
  }

  public static boolean register(Plugin plugin, Listener listener) {
    String componentType = componentType(listener);
    if (componentType == null) {
      return false;
    }

    boolean registeredAny = false;
    for (Method method : collectHandlerMethods(listener.getClass()).values()) {
      EventHandler annotation = method.getAnnotation(EventHandler.class);
      if (annotation == null || method.getParameterCount() != 1 || Modifier.isStatic(method.getModifiers())) {
        continue;
      }

      Class<?> parameterType = method.getParameterTypes()[0];
      if (!Event.class.isAssignableFrom(parameterType)) {
        continue;
      }

      @SuppressWarnings("unchecked")
      Class<? extends Event> eventType = (Class<? extends Event>) parameterType;
      try {
        method.setAccessible(true);
      } catch (Throwable ex) {
        Adapt.warn("Failed enabling access to " + componentType + " handler "
            + listener.getClass().getName() + "#" + method.getName()
            + ": " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        continue;
      }

      EventExecutor executor = EventHandlerInvoker.createExecutor(
          listener,
          method,
          eventType,
          EventHandlerInvoker.enforcesInteractionValidity(method, eventType)
      );
      boolean ignoreCancelled = EventHandlerInvoker.shouldIgnoreCancelled(method, annotation, eventType);
      if (annotation.priority() == EventPriority.HIGHEST && EntityDamageEvent.class.isAssignableFrom(eventType)) {
        registerDamageHandler(plugin, listener, eventType.asSubclass(EntityDamageEvent.class), executor, ignoreCancelled);
      } else {
        Bukkit.getPluginManager().registerEvent(eventType, listener, annotation.priority(), executor, plugin, ignoreCancelled);
      }
      registeredAny = true;
    }

    return registeredAny;
  }

  public static synchronized void unregister(Plugin plugin, Listener listener) {
    DamageEventDispatcher dispatcher = DAMAGE_DISPATCHERS.get(plugin);
    if (dispatcher != null) {
      dispatcher.unregister(listener);
    }
  }

  public static synchronized void unregisterAll(Plugin plugin) {
    DamageEventDispatcher dispatcher = DAMAGE_DISPATCHERS.remove(plugin);
    if (dispatcher != null) {
      dispatcher.clear();
      HandlerList.unregisterAll(dispatcher);
    }
  }

  private static synchronized void registerDamageHandler(Plugin plugin, Listener listener,
                                                        Class<? extends EntityDamageEvent> eventType,
                                                        EventExecutor executor, boolean ignoreCancelled) {
    DamageEventDispatcher dispatcher = DAMAGE_DISPATCHERS.get(plugin);
    if (dispatcher == null) {
      dispatcher = new DamageEventDispatcher(plugin);
      DamageEventDispatcher registeredDispatcher = dispatcher;
      Bukkit.getPluginManager().registerEvent(EntityDamageEvent.class, dispatcher, EventPriority.HIGHEST,
          (target, event) -> {
            if (event instanceof EntityDamageEvent damage) {
              registeredDispatcher.onDamage(damage);
            }
          }, plugin, false);
      DAMAGE_DISPATCHERS.put(plugin, dispatcher);
    }
    dispatcher.register(listener, eventType, executor, ignoreCancelled);
  }

  private static String componentType(Listener listener) {
    if (listener instanceof Skill<?>) {
      return "skill";
    }
    if (listener instanceof Adaptation<?>) {
      return "adaptation";
    }
    return null;
  }

  private static Map<String, Method> collectHandlerMethods(Class<?> type) {
    Map<String, Method> methods = new LinkedHashMap<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.isAnnotationPresent(EventHandler.class)) {
          methods.putIfAbsent(signature(method), method);
        }
      }
      current = current.getSuperclass();
    }
    return methods;
  }

  private static String signature(Method method) {
    return method.getName() + "|" + Arrays.toString(method.getParameterTypes());
  }
}
