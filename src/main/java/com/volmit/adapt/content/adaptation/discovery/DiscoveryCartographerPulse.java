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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscoveryCartographerPulse extends SimpleAdaptation<DiscoveryCartographerPulse.Config> {
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public DiscoveryCartographerPulse() {
        super("discovery-cartographer-pulse");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.cartographer_pulse.description"));
        setDisplayName(Localizer.dLocalize("discovery.cartographer_pulse.name"));
        setIcon(Material.COMPASS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COMPASS)
                .key("challenge_discovery_cartographer_100")
                .title(Localizer.dLocalize("advancement.challenge_discovery_cartographer_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_cartographer_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FILLED_MAP)
                        .key("challenge_discovery_cartographer_1k")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_cartographer_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_cartographer_1k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_cartographer_100")
                .goal(100)
                .stat("discovery.cartographer-pulse.pulses")
                .reward(300)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_cartographer_1k")
                .goal(1000)
                .stat("discovery.cartographer-pulse.pulses")
                .reward(1000)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getSearchRange(level)) + C.GRAY + " " + Localizer.dLocalize("discovery.cartographer_pulse.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("discovery.cartographer_pulse.lore2"));
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
        if (!hasAdaptation(p) || !p.isSneaking() || p.getInventory().getItemInMainHand().getType() != Material.COMPASS) {
            return;
        }

        int level = getLevel(p);
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(p.getUniqueId(), 0L)) {
            return;
        }

        Location target = locateNearestStructureFallback(p.getWorld(), p.getLocation(), getSearchRange(level));
        if (target == null) {
            target = p.getWorld().getSpawnLocation();
        }

        p.setCompassTarget(target);
        p.sendMessage(C.AQUA + "Compass pulse: " + C.WHITE + Form.f(target.getBlockX()) + ", " + Form.f(target.getBlockZ()));
        cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 1.3f);
        xp(p, getConfig().xpPerPulse);
        getPlayer(p).getData().addStat("discovery.cartographer-pulse.pulses", 1);
    }

    private Location locateNearestStructureFallback(World world, Location from, int range) {
        try {
            for (Method m : world.getClass().getMethods()) {
                if (!m.getName().equals("locateNearestStructure")) {
                    continue;
                }

                Class<?>[] p = m.getParameterTypes();
                if (p.length < 4 || p[0] != Location.class || p[2] != int.class || p[3] != boolean.class) {
                    continue;
                }

                Object structure = resolvePreferredStructureType(p[1]);
                if (structure == null) {
                    continue;
                }

                Object out;
                if (p.length == 4) {
                    out = m.invoke(world, from, structure, range, false);
                } else if (p.length == 5 && p[4] == boolean.class) {
                    out = m.invoke(world, from, structure, range, false, false);
                } else {
                    continue;
                }

                Location location = extractLocation(out);
                if (location != null) {
                    return location;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
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

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Sneak-right-click with a compass to pulse toward a nearby structure target.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Search Range Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double searchRangeBase = 640;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Search Range Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double searchRangeFactor = 768;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 26000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 14000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Pulse for the Discovery Cartographer Pulse adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerPulse = 25;
    }
}
