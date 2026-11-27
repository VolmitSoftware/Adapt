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
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardProtector extends BukkitProtector {

  private final RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
  private final StateFlag flag = WorldGuardFlags.useAdaptations();

  @Override
  protected boolean checkRegion(Player player, Location location) {
    return checkPerm(player, location, flag);
  }

  @Override
  protected boolean canBlockBreak(Player player, Location blockLocation) {
    return checkRegion(player, blockLocation) && checkPerm(player, blockLocation, Flags.BLOCK_BREAK);
  }

  @Override
  protected boolean canBlockPlace(Player player, Location blockLocation) {
    return checkRegion(player, blockLocation) && checkPerm(player, blockLocation, Flags.BLOCK_PLACE);
  }

  @Override
  protected boolean canPVP(Player player, Location entityLocation) {
    return checkRegion(player, entityLocation) && checkPerm(player, entityLocation, Flags.PVP);
  }

  @Override
  protected boolean canPVE(Player player, Location entityLocation) {
    return checkRegion(player, entityLocation) && checkPerm(player, entityLocation, Flags.DAMAGE_ANIMALS);
  }

  @Override
  protected boolean canInteract(Player player, Location targetLocation) {
    return checkRegion(player, targetLocation) && checkPerm(player, targetLocation, Flags.INTERACT);
  }

  @Override
  protected boolean canAccessChest(Player player, Location chestLocation) {
    return checkRegion(player, chestLocation) && checkPerm(player, chestLocation, Flags.CHEST_ACCESS);
  }

  private boolean checkPerm(Player player, Location location, StateFlag flag) {
    if (flag == null) {
      return true;
    }

    RegionQuery regionQuery = container.createQuery();
    com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(location);
    if (!hasBypass(player, location))
      return regionQuery.queryState(loc, WorldGuardPlugin.inst().wrapPlayer(player), flag) != StateFlag.State.DENY;
    return true;
  }

  @Override
  public String name() {
    return "WorldGuard";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isWorldguard();
  }

  private boolean hasBypass(Player p, Location l) {
    LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
    com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(l.getWorld());
    return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, world);
  }
}
