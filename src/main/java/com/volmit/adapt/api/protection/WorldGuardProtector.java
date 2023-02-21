package com.volmit.adapt.api.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardProtector extends Protector {
    private final StateFlag flag;

    public WorldGuardProtector() {
        this.flag = new StateFlag("use-adaptations", !AdaptConfig.get().isRequireWorldguardBuildPermToUseAdaptations());
        WorldGuard.getInstance().getFlagRegistry().register(flag);
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

    private boolean hasBypass(Player p, Location l) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(l.getWorld());
        return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, world);
    }
}
