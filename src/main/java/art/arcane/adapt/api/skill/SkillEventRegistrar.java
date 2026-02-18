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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillEventRegistrar {
  private SkillEventRegistrar() {
  }

  public static boolean register(Plugin plugin, Listener listener) {
    if (!(listener instanceof Skill<?>)) {
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
        Adapt.warn("Failed enabling access to skill handler "
            + listener.getClass().getName() + "#" + method.getName()
            + ": " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        continue;
      }

      EventExecutor executor = (target, event) -> {
        if (!eventType.isAssignableFrom(event.getClass())) {
          return;
        }

        try {
          method.invoke(target, event);
        } catch (InvocationTargetException ex) {
          throw new EventException(ex.getCause());
        } catch (Throwable ex) {
          throw new EventException(ex);
        }
      };

      boolean ignoreCancelled = shouldIgnoreCancelled(method, annotation, eventType);
      Bukkit.getPluginManager().registerEvent(eventType, listener, annotation.priority(), executor, plugin, ignoreCancelled);
      registeredAny = true;
    }

    return registeredAny;
  }

  private static Map<String, Method> collectHandlerMethods(Class<?> type) {
    Map<String, Method> methods = new LinkedHashMap<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (!method.isAnnotationPresent(EventHandler.class)) {
          continue;
        }
        methods.putIfAbsent(signature(method), method);
      }
      current = current.getSuperclass();
    }
    return methods;
  }

  private static String signature(Method method) {
    return method.getName() + "|" + Arrays.toString(method.getParameterTypes());
  }

  private static boolean shouldIgnoreCancelled(Method method, EventHandler annotation, Class<? extends Event> eventType) {
    if (!Cancellable.class.isAssignableFrom(eventType)) {
      return annotation.ignoreCancelled();
    }

    if (method.isAnnotationPresent(ReceiveCancelledEvents.class)) {
      return false;
    }

    return true;
  }
}
