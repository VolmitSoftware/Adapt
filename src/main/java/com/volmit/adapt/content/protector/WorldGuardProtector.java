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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

public class WorldGuardProtector implements Protector {

    private final RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    private final StateFlag flag;

    public WorldGuardProtector() {
        StateFlag flag = new StateFlag("use-adaptations", false);
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            registry.register(flag);
        } catch (FlagConflictException e) {
            flag = (StateFlag) registry.get("use-adaptations");
        } catch (IllegalStateException ignored) {
            try {
                // Access the flags field of the registry
                Field field = registry.getClass().getDeclaredField("flags");
                // This line makes the private field accessible
                field.setAccessible(true);
                // Get the flags from the registry
                ConcurrentMap<String, Flag<?>> flags = (ConcurrentMap<String, Flag<?>>) field.get(registry);
                // Add it to the registry
                flags.put(flag.getName().toLowerCase(), flag);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        this.flag = flag;
    }


    @Override
    public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
        return checkPerm(player, location, flag);
    }

    @Override
    public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.BLOCK_BREAK);
    }

    @Override
    public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.BLOCK_PLACE);
    }

    @Override
    public boolean canPVP(Player player, Location entityLocation, Adaptation<?> adaptation) {
        return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.PVP);
    }

    @Override
    public boolean canPVE(Player player, Location entityLocation, Adaptation<?> adaptation) {
        return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.DAMAGE_ANIMALS);
    }

    @Override
    public boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation) {
        return checkRegion(player, targetLocation, adaptation) && checkPerm(player, targetLocation, Flags.INTERACT);
    }

    @Override
    public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
        return checkRegion(player, chestLocation, adaptation) && checkPerm(player, chestLocation, Flags.CHEST_ACCESS);
    }

    private boolean checkPerm(Player player, Location location, StateFlag flag) {
        RegionQuery regionQuery = container.createQuery();
        com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(location);
        if (!hasBypass(player, location))
            return regionQuery.queryState(loc, WorldGuardPlugin.inst().wrapPlayer(player), flag) != StateFlag.State.DENY;
        return true;
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isWorldguard();
    }

    private boolean hasBypass(Player p, Location l) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(l.getWorld());
        return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, world);
    }
}
