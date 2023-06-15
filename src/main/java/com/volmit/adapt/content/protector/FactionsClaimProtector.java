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

import com.massivecraft.factions.*;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class FactionsClaimProtector implements Protector {

    @Override
    public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
        Faction f = Board.getInstance().getFactionAt(new FLocation(player.getLocation()));
        return checkPerm(player, f, adaptation) || f.isWilderness();
    }

    @Override
    public boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation) {
        Faction f = Board.getInstance().getFactionAt(new FLocation(victimLocation));
        return checkPerm(player, f, adaptation) || !f.noPvPInTerritory();
    }

    private boolean checkPerm(Player player, Faction f, Adaptation<?> adaptation) {
        FPlayer fp = FPlayers.getInstance().getByPlayer(player);
        return f == null
                || fp.getFaction() == f
                || fp.isAdminBypassing();
    }

    @Override
    public String getName() {
        return "Factions";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isFactionsClaim();
    }
}
