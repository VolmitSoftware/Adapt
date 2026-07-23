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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.telemetry.AdaptRuntimeTelemetry;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
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

  public static boolean shouldIgnoreCancelled(Method method, EventHandler annotation, Class<? extends Event> eventType) {
    if (!Cancellable.class.isAssignableFrom(eventType)) {
      return annotation.ignoreCancelled();
    }

    if (PlayerInteractEvent.class.isAssignableFrom(eventType)) {
      return false;
    }

    if (method.isAnnotationPresent(ReceiveCancelledEvents.class)) {
      return false;
    }

    return true;
  }

  public static boolean enforcesInteractionValidity(Method method, Class<? extends Event> eventType) {
    return PlayerInteractEvent.class.isAssignableFrom(eventType)
        && !method.isAnnotationPresent(ReceiveCancelledEvents.class);
  }

  public static EventExecutor createExecutor(Method method, Class<? extends Event> eventType, boolean enforceInteractionValidity) {
    BiConsumer<Object, Object> direct = tryCreateDirectInvoker(method);
    if (direct != null) {
      if (enforceInteractionValidity) {
        return (target, event) -> {
          if (!eventType.isAssignableFrom(event.getClass()) || isVetoedInteraction(event)) {
            return;
          }

          try {
            invokeMeasured(target, () -> direct.accept(target, event));
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
          invokeMeasured(target, () -> direct.accept(target, event));
        } catch (Throwable ex) {
          throw new EventException(ex);
        }
      };
    }

    if (enforceInteractionValidity) {
      return (target, event) -> {
        if (!eventType.isAssignableFrom(event.getClass()) || isVetoedInteraction(event)) {
          return;
        }

        try {
          invokeMeasured(target, () -> method.invoke(target, event));
        } catch (InvocationTargetException ex) {
          throw new EventException(ex.getCause());
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
        invokeMeasured(target, () -> method.invoke(target, event));
      } catch (InvocationTargetException ex) {
        throw new EventException(ex.getCause());
      } catch (Throwable ex) {
        throw new EventException(ex);
      }
    };
  }

  private static boolean isVetoedInteraction(Event event) {
    if (!(event instanceof PlayerInteractEvent interaction)) {
      return false;
    }

    if (interaction.getClickedBlock() != null) {
      return interaction.useInteractedBlock() == Event.Result.DENY;
    }

    return interaction.useItemInHand() == Event.Result.DENY;
  }

  private static void invokeMeasured(Object target, Invocation invocation) throws Throwable {
    if (!(target instanceof Adaptation<?> adaptation)) {
      invocation.invoke();
      return;
    }

    long startedNanos = AbilityCheckTelemetry.beginExecution(adaptation.getName());
    AdaptRuntimeTelemetry.recordEventHandlerOp(System.currentTimeMillis());
    try {
      invocation.invoke();
    } finally {
      AbilityCheckTelemetry.endExecution(adaptation.getName(), startedNanos);
    }
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

  @FunctionalInterface
  private interface Invocation {
    void invoke() throws Throwable;
  }
}
