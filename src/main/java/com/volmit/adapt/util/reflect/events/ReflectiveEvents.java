package com.volmit.adapt.util.reflect.events;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.collection.KMap;
import com.volmit.adapt.util.reflect.Reflect;
import com.volmit.adapt.util.reflect.events.api.Event;
import com.volmit.adapt.util.reflect.events.api.ReflectiveHandler;
import com.volmit.adapt.util.reflect.events.api.entity.EndermanAttackPlayerEvent;
import com.volmit.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import com.volmit.adapt.util.reflect.events.api.entity.EntityMountEvent;
import lombok.NonNull;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReflectiveEvents {
    private static final KMap<Class<? extends Event>, Class<?>> EVENTS = new KMap<>();
    private static final KMap<Class<?>, HandlerList> HANDLERS = new KMap<>();

    static {
        register(EntityMountEvent.class, "org.bukkit.event.entity.EntityMountEvent", "org.spigotmc.event.entity.EntityMountEvent");
        register(EntityDismountEvent.class, "org.bukkit.event.entity.EntityDismountEvent", "org.spigotmc.event.entity.EntityDismountEvent");
        register(EndermanAttackPlayerEvent.class, "com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent");
    }

    public static void register(@NonNull Listener listener) {
        if (Adapt.bad) return;

        Arrays.stream(listener.getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(ReflectiveHandler.class))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                .collect(Collectors.toMap(method -> HANDLERS.get(method.getParameterTypes()[0]),
                        method -> {
                            try {
                                if (!Modifier.isPublic(method.getModifiers())) {
                                    method.setAccessible(true);
                                }
                            } catch (Throwable e) {
                                return new KList<RegisteredListener>();
                            }


                            Class<?> eventClass = method.getParameterTypes()[0];
                            Class<?> bukkitClass = EVENTS.get(eventClass);
                            ReflectiveHandler handler = method.getAnnotation(ReflectiveHandler.class);

                            EventExecutor executor = (obj, event) -> {
                                if (!bukkitClass.isAssignableFrom(event.getClass()))
                                    return;

                                try {
                                    method.invoke(obj, newProxy(obj, eventClass));
                                } catch (InvocationTargetException e) {
                                    throw new EventException(e.getCause());
                                } catch (Throwable e) {
                                    throw new EventException(e);
                                }
                            };

                            return new KList<>(new RegisteredListener(listener, executor, handler.priority(), Adapt.instance, handler.ignoreCancelled()));
                        }, KList::add))
                .forEach((handlerList, registeredListeners) -> {
                    if (handlerList == null) return;
                    handlerList.registerAll(registeredListeners);
                });
    }

    public static void register(Class<? extends Event> eventInterface, String... classes) {
        for (String clazz : classes) {
            Optional<Class<?>> opt = Reflect.getClass(clazz);
            if (opt.isEmpty())
                continue;

            var handlerList = getHandlerList(opt.get());
            if (handlerList == null) {
                Adapt.warn("Event class does not contain HandlerList: " + clazz);
                continue;
            }

            EVENTS.put(eventInterface, opt.get());
            HANDLERS.put(opt.get(), handlerList);
            return;
        }
    }

    public static boolean exists(@NonNull Class<? extends Event> eventInterface) {
        return EVENTS.containsKey(eventInterface);
    }

    @Nullable
    private static HandlerList getHandlerList(Class<?> parent) {
        while (parent != null) {
            if (!org.bukkit.event.Event.class.isAssignableFrom(parent))
                return null;

            try {
                var method = parent.getDeclaredMethod("getHandlerList");
                return (HandlerList) method.invoke(null);
            } catch (Throwable e) {
                parent = parent.getSuperclass();
            }
        }
        return null;
    }

    private static Object newProxy(Object o, Class<?>... interfaces) {
        return Proxy.newProxyInstance(Event.class.getClassLoader(), interfaces, (proxy, method, args) -> method.invoke(o, args));
    }
}
