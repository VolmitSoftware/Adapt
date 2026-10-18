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

import java.util.Objects;

public class FactionsClaimProtector implements Protector {
  private final Territory territory;

  public FactionsClaimProtector() {
    this(new FactionsTerritory());
  }

  FactionsClaimProtector(Territory territory) {
    this.territory = Objects.requireNonNull(territory);
  }

  @Override
  public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
    Faction f = territory.factionAt(location);
    return checkPerm(player, f) || f.isWilderness();
  }

  @Override
  public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return hasAccess(player, blockLocation, Permission.DESTROY);
  }

  @Override
  public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return hasAccess(player, blockLocation, Permission.BUILD);
  }

  @Override
  public boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation) {
    Faction f = territory.factionAt(victimLocation);
    return checkPerm(player, f) || !f.noPvPInTerritory();
  }

  @Override
  public boolean canPVE(Player player, Location victimLocation, Adaptation<?> adaptation) {
    Faction f = territory.factionAt(victimLocation);
    return checkPerm(player, f) || f.isWilderness();
  }

  @Override
  public boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation) {
    return hasAccess(player, targetLocation, Permission.ITEM);
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return hasAccess(player, chestLocation, Permission.CONTAINER);
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
    FPlayer fp = territory.player(player);
    return f == null
        || fp.getFaction() == f
        || fp.isAdminBypassing();
  }

  private boolean hasAccess(Player player, Location location, Permission permission) {
    FPlayer fp = territory.player(player);
    if (fp.isAdminBypassing()) {
      return true;
    }
    Faction f = territory.factionAt(location);
    return f == null || f.isWilderness() || territory.hasAccess(f, fp, permission, location);
  }

  enum Permission {
    BUILD,
    DESTROY,
    ITEM,
    CONTAINER
  }

  interface Territory {
    Faction factionAt(Location location);

    FPlayer player(Player player);

    boolean hasAccess(Faction faction, FPlayer player, Permission permission, Location location);
  }

  private static final class FactionsTerritory implements Territory {
    @Override
    public Faction factionAt(Location location) {
      return Board.getInstance().getFactionAt(new FLocation(location));
    }

    @Override
    public FPlayer player(Player player) {
      return FPlayers.getInstance().getByPlayer(player);
    }

    @Override
    public boolean hasAccess(Faction faction, FPlayer player, Permission permission, Location location) {
      return faction.hasAccess(player, action(permission), new FLocation(location));
    }

    private static PermissibleAction action(Permission permission) {
      return switch (permission) {
        case BUILD -> PermissibleActions.BUILD;
        case DESTROY -> PermissibleActions.DESTROY;
        case ITEM -> PermissibleActions.ITEM;
        case CONTAINER -> PermissibleActions.CONTAINER;
      };
    }
  }
}
