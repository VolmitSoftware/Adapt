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

package art.arcane.adapt.content.protector;

import art.arcane.adapt.Adapt;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.DoubleFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;

public final class WorldGuardFlags {
  public static final String USE_ADAPTATIONS = "use-adaptations";
  public static final String ADAPT_XP = "adapt-xp";
  public static final String ADAPT_XP_MULTIPLIER = "adapt-xp-multiplier";
  public static final String ADAPT_POWER_BONUS = "adapt-power-bonus";
  public static final String ADAPT_UNLOCK_ADAPTATIONS = "adapt-unlock-adaptations";

  private static volatile boolean registered;
  private static volatile StateFlag useAdaptations;
  private static volatile StateFlag adaptXp;
  private static volatile DoubleFlag adaptXpMultiplier;
  private static volatile IntegerFlag adaptPowerBonus;
  private static volatile SetFlag<String> adaptUnlockAdaptations;

  private WorldGuardFlags() {
  }

  public static synchronized void register() {
    if (registered) {
      return;
    }

    registered = true;
    FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
    useAdaptations = claim(registry, new StateFlag(USE_ADAPTATIONS, false), StateFlag.class);
    adaptXp = claim(registry, new StateFlag(ADAPT_XP, true), StateFlag.class);
    adaptXpMultiplier = claim(registry, new DoubleFlag(ADAPT_XP_MULTIPLIER), DoubleFlag.class);
    adaptPowerBonus = claim(registry, new IntegerFlag(ADAPT_POWER_BONUS), IntegerFlag.class);
    adaptUnlockAdaptations = claimUnlockAdaptations(registry);
  }

  public static StateFlag useAdaptations() {
    register();
    return useAdaptations;
  }

  public static StateFlag adaptXp() {
    register();
    return adaptXp;
  }

  public static DoubleFlag adaptXpMultiplier() {
    register();
    return adaptXpMultiplier;
  }

  public static IntegerFlag adaptPowerBonus() {
    register();
    return adaptPowerBonus;
  }

  public static SetFlag<String> adaptUnlockAdaptations() {
    register();
    return adaptUnlockAdaptations;
  }

  @SuppressWarnings("unchecked")
  private static SetFlag<String> claimUnlockAdaptations(FlagRegistry registry) {
    Flag<?> existing = registry.get(ADAPT_UNLOCK_ADAPTATIONS);
    if (existing instanceof SetFlag<?> setFlag && setFlag.getType() instanceof StringFlag) {
      return (SetFlag<String>) setFlag;
    }

    SetFlag<String> flag = new SetFlag<>(ADAPT_UNLOCK_ADAPTATIONS, new StringFlag(null));
    return publish(registry, flag, SetFlag.class) ? flag : null;
  }

  private static <T extends Flag<?>> T claim(FlagRegistry registry, T flag, Class<T> type) {
    Flag<?> existing = registry.get(flag.getName());
    if (type.isInstance(existing)) {
      return type.cast(existing);
    }

    return publish(registry, flag, type) ? flag : null;
  }

  private static boolean publish(FlagRegistry registry, Flag<?> flag, Class<?> type) {
    try {
      registry.register(flag);
      return true;
    } catch (FlagConflictException conflict) {
      Adapt.warn("WorldGuard flag " + flag.getName() + " is owned by another plugin with a different type; Adapt will not use it.");
      return false;
    } catch (IllegalStateException late) {
      Adapt.warn("WorldGuard flag " + flag.getName() + " was not registered in time. Injecting it now...");
      return inject(registry, flag, type);
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean inject(FlagRegistry registry, Flag<?> flag, Class<?> type) {
    try {
      Field field = registry.getClass().getDeclaredField("flags");
      field.setAccessible(true);
      ConcurrentMap<String, Flag<?>> flags = (ConcurrentMap<String, Flag<?>>) field.get(registry);
      String key = flag.getName().toLowerCase(Locale.ROOT);
      Flag<?> present = flags.get(key);
      if (present != null) {
        return type.isInstance(present);
      }

      flags.put(key, flag);
      return true;
    } catch (NoSuchFieldException | IllegalAccessException | RuntimeException error) {
      Adapt.warn("Failed to inject WorldGuard flag " + flag.getName() + ": " + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      error.printStackTrace();
      return false;
    }
  }
}
