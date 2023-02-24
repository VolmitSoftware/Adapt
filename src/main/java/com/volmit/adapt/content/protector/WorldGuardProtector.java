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

package com.volmit.adapt.content.protector;

import art.arcane.curse.Curse;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentMap;

public class WorldGuardProtector implements Protector {
    private final StateFlag flag;

    public WorldGuardProtector() {
        this.flag = new StateFlag("use-adaptations", false);
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        ConcurrentMap<String, Flag<?>> flags = Curse.on(registry).field("flags").get(); // this is black magic
        flags.put(flag.getName().toLowerCase(), flag); // add it to the registry
    }

    @Override
    public void unregister() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        ConcurrentMap<String, Flag<?>> flags = Curse.on(registry).field("flags").get(); // this is black magic
        flags.remove(flag.getName().toLowerCase()); // remove it from the registry
    }

    @Override
    public boolean canBuild(Player p, Location l, Adaptation<?> adaptation) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(l);
        if (!hasBypass(p, l)) {
            return query.testBuild(loc, WorldGuardPlugin.inst().wrapPlayer(p), flag);
        } else {
            return true;
        }
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().isRequireWorldguardBuildPermToUseAdaptations();
    }

    private boolean hasBypass(Player p, Location l) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(l.getWorld());
        return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, world);
    }
}
