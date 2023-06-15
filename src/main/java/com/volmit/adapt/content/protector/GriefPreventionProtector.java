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

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

public class GriefPreventionProtector implements Protector {


    @Override
    public boolean canBlockBreak(Player player, Location location, Adaptation<?> adaptation) {
        return canEditClaim(player, location);
    }

    @Override
    public boolean canBlockPlace(Player player, Location location, Adaptation<?> adaptation) {
        return canEditClaim(player, location);
    }

    @Override
    public String getName() {
        return "GriefPrevention";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isGriefprevention();
    }

    @Override
    public void unregister() {
        Protector.super.unregister();
    }



    private boolean canEditClaim(Player player, Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

        if (claim == null) {
            return true;
        }
        //If doesn't check is adminclaim getting ownerid return null
        if (!claim.isAdminClaim() && Objects.equals(claim.getOwnerID(), player.getUniqueId())) {
            return true;
        }
        else if (claim.getPermission(player.getUniqueId().toString()) == ClaimPermission.Build) {
            return true;
        }

        return playerData.ignoreClaims || claim.isAdminClaim() && player.hasPermission("griefprevention.adminclaims");

    }


}

