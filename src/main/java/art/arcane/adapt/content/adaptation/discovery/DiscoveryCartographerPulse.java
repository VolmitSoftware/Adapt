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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class DiscoveryCartographerPulse extends SimpleAdaptation<DiscoveryCartographerPulse.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private volatile Method cachedLocateMethod;
  private volatile Object cachedStructure;
  private volatile boolean locateResolved;

  public DiscoveryCartographerPulse() {
    super("discovery-cartographer-pulse");
    registerConfiguration(Config.class);
    setIcon(Material.COMPASS);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS)
        .key("challenge_discovery_cartographer_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.FILLED_MAP)
            .key("challenge_discovery_cartographer_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_cartographer_100", "discovery.cartographer-pulse.pulses", 100, 300);
    registerMilestone("challenge_discovery_cartographer_1k", "discovery.cartographer-pulse.pulses", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSearchRange(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    if (getConfig().hungerCost > 0) {
      v.addLore(C.RED + "* " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("discovery.cartographer_pulse.lore_cost_hunger"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || p.getInventory().getItemInMainHand().getType() != Material.COMPASS) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    Location target = locateNearestStructureFallback(p.getWorld(), p.getLocation(), getSearchRange(level));
    boolean found = target != null;
    if (target == null) {
      target = p.getWorld().getSpawnLocation();
    }

    p.setCompassTarget(target);
    p.sendMessage(C.AQUA + "Compass pulse: " + C.WHITE + Form.f(target.getBlockX()) + ", " + Form.f(target.getBlockZ()));
    cooldowns.mark(p.getUniqueId());
    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    }

    timeline(p)
        .duration(20)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.ring(Particle.ELECTRIC_SPARK, 0.5D + (progress * 4.0D), 12, 0.1D);
          if (tick == 0) {
            f.chord(Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8F, 1.3F, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
          }
        })
        .start();

    if (found) {
      Location eye = p.getEyeLocation();
      fx(eye, FxPriority.GAMEPLAY)
          .trail(Particles.END_ROD, target.getX() - eye.getX(), target.getY() - eye.getY(), target.getZ() - eye.getZ(), 8.0D, 10);
    }

    xp(p, getConfig().xpPerPulse);
    addStat(p, "discovery.cartographer-pulse.pulses", 1);
  }

  private Location locateNearestStructureFallback(World world, Location from, int range) {
    if (!locateResolved) {
      synchronized (this) {
        if (!locateResolved) {
          resolveLocateMethod(world);
          locateResolved = true;
        }
      }
    }

    Method method = cachedLocateMethod;
    Object structure = cachedStructure;
    if (method == null || structure == null) {
      return null;
    }

    try {
      Class<?>[] p = method.getParameterTypes();
      Object out;
      if (p.length == 4) {
        out = method.invoke(world, from, structure, range, false);
      } else if (p.length == 5 && p[4] == boolean.class) {
        out = method.invoke(world, from, structure, range, false, false);
      } else {
        return null;
      }

      return extractLocation(out);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private void resolveLocateMethod(World world) {
    for (Method m : world.getClass().getMethods()) {
      if (!m.getName().equals("locateNearestStructure")) {
        continue;
      }

      Class<?>[] p = m.getParameterTypes();
      if (p.length < 4 || p[0] != Location.class || p[2] != int.class || p[3] != boolean.class) {
        continue;
      }

      if (p.length != 4 && !(p.length == 5 && p[4] == boolean.class)) {
        continue;
      }

      Object structure = resolvePreferredStructureType(p[1]);
      if (structure == null) {
        continue;
      }

      cachedLocateMethod = m;
      cachedStructure = structure;
      return;
    }
  }

  private Object resolvePreferredStructureType(Class<?> structureTypeClass) {
    String[] preferred = {"VILLAGE", "STRONGHOLD", "RUINED_PORTAL", "MINESHAFT", "SHIPWRECK", "TRAIL_RUINS"};

    if (structureTypeClass.isEnum()) {
      Object[] constants = structureTypeClass.getEnumConstants();
      if (constants == null || constants.length == 0) {
        return null;
      }

      for (String name : preferred) {
        Object c = Arrays.stream(constants).filter(i -> ((Enum<?>) i).name().equals(name)).findFirst().orElse(null);
        if (c != null) {
          return c;
        }
      }

      return constants[0];
    }

    for (String name : preferred) {
      try {
        Field f = structureTypeClass.getField(name);
        Object value = f.get(null);
        if (value != null) {
          return value;
        }
      } catch (Throwable ignored) {
      }
    }

    try {
      Method values = structureTypeClass.getMethod("values");
      Object out = values.invoke(null);
      if (out instanceof Object[] a && a.length > 0) {
        return a[0];
      }
    } catch (Throwable ignored) {
    }

    return null;
  }

  private Location extractLocation(Object out) {
    if (out instanceof Location location) {
      return location;
    }

    if (out == null) {
      return null;
    }

    try {
      Method getter = out.getClass().getMethod("getLocation");
      Object loc = getter.invoke(out);
      if (loc instanceof Location location) {
        return location;
      }
    } catch (Throwable ignored) {
    }

    return null;
  }

  private int getSearchRange(int level) {
    return Math.max(128, (int) Math.round(getConfig().searchRangeBase + (getLevelPercent(level) * getConfig().searchRangeFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(1500L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Sneak-right-click with a compass to pulse toward a nearby structure target.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Range Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRangeBase = 640;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Search Range Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double searchRangeFactor = 768;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 26000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 14000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Pulse for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPulse = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed per compass pulse.", impact = "Higher values make each pulse cost more hunger; 0 disables the cost.")
    int hungerCost = 2;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
