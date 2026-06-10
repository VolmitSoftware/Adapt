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

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.plugin.EventExecutor;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

public final class EventHandlerInvoker {
  private EventHandlerInvoker() {
  }

  public static EventExecutor createExecutor(Method method, Class<? extends Event> eventType) {
    BiConsumer<Object, Object> direct = tryCreateDirectInvoker(method);
    if (direct != null) {
      return (target, event) -> {
        if (!eventType.isAssignableFrom(event.getClass())) {
          return;
        }

        try {
          direct.accept(target, event);
        } catch (Throwable ex) {
          throw new EventException(ex);
        }
      };
    }

    return (target, event) -> {
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
  }

  @SuppressWarnings("unchecked")
  private static BiConsumer<Object, Object> tryCreateDirectInvoker(Method method) {
    try {
      MethodHandles.Lookup caller = MethodHandles.lookup();
      MethodHandles.Lookup lookup;
      try {
        lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), caller);
      } catch (IllegalAccessException e) {
        lookup = caller;
      }

      MethodHandle handle = lookup.unreflect(method);
      MethodType invokedType = MethodType.methodType(BiConsumer.class);
      MethodType samType = MethodType.methodType(void.class, Object.class, Object.class);
      MethodType instantiatedType = MethodType.methodType(void.class, method.getDeclaringClass(), method.getParameterTypes()[0]);
      return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
          lookup, "accept", invokedType, samType, handle, instantiatedType
      ).getTarget().invokeExact();
    } catch (Throwable ignored) {
      return null;
    }
  }
}
