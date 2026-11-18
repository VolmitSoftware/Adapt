package art.arcane.adapt.bukkit.feature.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import org.bukkit.entity.Mob;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Goal priorities, which Paper's goal API does not expose. Reads the mob's two selectors through the
 * Mojang-mapped server classes; when that fails every priority reads as {@link #UNKNOWN}.
 */
interface GoalPriorities {
  int UNKNOWN = -1;

  /** Priority lookup for one snapshot of the mob's selectors. */
  Index index(Mob mob);

  interface Index {
    int priority(Goal<Mob> goal);
  }

  Index NONE = (Goal<Mob> goal) -> UNKNOWN;

  static GoalPriorities reflective() {
    return Reflective.INSTANCE;
  }

  /** The server goal behind a Paper goal wrapper, or null for goals Paper wraps the other way round. */
  static Object handle(Object goal) {
    return Reflective.INSTANCE.invoke(goal, "getHandle");
  }

  final class Reflective implements GoalPriorities {
    private static final Reflective INSTANCE = new Reflective();
    private final Map<String, Optional<Method>> methods = new ConcurrentHashMap<>();
    private final Map<String, Field> fields = new ConcurrentHashMap<>();
    private volatile boolean broken;

    @Override
    public Index index(Mob mob) {
      if (broken) {
        return NONE;
      }
      try {
        Object handle = invoke(mob, "getHandle");
        if (handle == null) {
          return NONE;
        }
        Map<Object, Integer> byServerGoal = new IdentityHashMap<>();
        collect(field(handle, "goalSelector"), byServerGoal);
        collect(field(handle, "targetSelector"), byServerGoal);
        return (Goal<Mob> goal) -> lookup(byServerGoal, goal);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        broken = true;
        return NONE;
      }
    }

    private int lookup(Map<Object, Integer> byServerGoal, Goal<Mob> goal) {
      Object serverGoal = invoke(goal, "getHandle");
      Integer priority = byServerGoal.get(serverGoal == null ? goal : serverGoal);
      return priority == null ? UNKNOWN : priority;
    }

    private void collect(Object selector, Map<Object, Integer> into) throws ReflectiveOperationException {
      if (selector == null) {
        return;
      }
      Object available = required(selector.getClass(), "getAvailableGoals").invoke(selector);
      if (!(available instanceof Collection<?> wrappedGoals)) {
        return;
      }
      for (Object wrapped : wrappedGoals) {
        Object serverGoal = required(wrapped.getClass(), "getGoal").invoke(wrapped);
        int priority = ((Number) required(wrapped.getClass(), "getPriority").invoke(wrapped)).intValue();
        into.put(serverGoal, priority);
        Object paperGoal = invoke(serverGoal, "getHandle");
        if (paperGoal != null) {
          into.put(paperGoal, priority);
        }
      }
    }

    private Method required(Class<?> type, String name) throws NoSuchMethodException {
      Method method = method(type, name);
      if (method == null) {
        throw new NoSuchMethodException(type.getName() + "." + name);
      }
      return method;
    }

    private Object invoke(Object target, String name) {
      if (target == null) {
        return null;
      }
      try {
        Method method = method(target.getClass(), name);
        return method == null ? null : method.invoke(target);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        return null;
      }
    }

    private Method method(Class<?> type, String name) {
      String cacheKey = type.getName() + '#' + name;
      Optional<Method> cached = methods.get(cacheKey);
      if (cached == null) {
        cached = Optional.ofNullable(find(type, name));
        methods.put(cacheKey, cached);
      }
      return cached.orElse(null);
    }

    private static Method find(Class<?> type, String name) {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
        for (Method candidate : current.getDeclaredMethods()) {
          if (candidate.getName().equals(name) && candidate.getParameterCount() == 0) {
            candidate.setAccessible(true);
            return candidate;
          }
        }
      }
      return null;
    }

    private Object field(Object target, String name) throws ReflectiveOperationException {
      String cacheKey = target.getClass().getName() + '#' + name;
      Field cached = fields.get(cacheKey);
      if (cached == null) {
        for (Class<?> current = target.getClass(); current != null && cached == null; current = current.getSuperclass()) {
          for (Field candidate : current.getDeclaredFields()) {
            if (candidate.getName().equals(name)) {
              candidate.setAccessible(true);
              cached = candidate;
              break;
            }
          }
        }
        if (cached == null) {
          throw new NoSuchFieldException(name);
        }
        fields.put(cacheKey, cached);
      }
      return cached.get(target);
    }
  }
}
