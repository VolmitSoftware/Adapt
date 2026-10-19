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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.perms.PermissibleActions;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class FactionsClaimProtector implements Protector {

  @Override
  public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
    Faction f = factionAt(location);
    return checkPerm(player, f) || f.isWilderness();
  }

  @Override
  public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return hasAccess(player, blockLocation, PermissibleActions.DESTROY);
  }

  @Override
  public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return hasAccess(player, blockLocation, PermissibleActions.BUILD);
  }

  @Override
  public boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation) {
    Faction f = factionAt(victimLocation);
    return checkPerm(player, f) || !f.noPvPInTerritory();
  }

  @Override
  public boolean canPVE(Player player, Location victimLocation, Adaptation<?> adaptation) {
    Faction f = factionAt(victimLocation);
    return checkPerm(player, f) || f.isWilderness();
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return hasAccess(player, chestLocation, PermissibleActions.CONTAINER);
  }

  @Override
  public String getName() {
    return "Factions";
  }

  @Override
  public boolean isEnabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isFactionsClaim();
  }

  private boolean checkPerm(Player player, Faction f) {
    FPlayer fp = FPlayers.getInstance().getByPlayer(player);
    return f == null
        || fp.getFaction() == f
        || fp.isAdminBypassing();
  }

  private boolean hasAccess(Player player, Location location, PermissibleAction action) {
    FPlayer fp = FPlayers.getInstance().getByPlayer(player);
    if (fp.isAdminBypassing()) {
      return true;
    }
    FLocation at = new FLocation(location);
    Faction f = Board.getInstance().getFactionAt(at);
    return f == null || f.isWilderness() || f.hasAccess(fp, action, at);
  }

  private static Faction factionAt(Location location) {
    return Board.getInstance().getFactionAt(new FLocation(location));
  }
}
