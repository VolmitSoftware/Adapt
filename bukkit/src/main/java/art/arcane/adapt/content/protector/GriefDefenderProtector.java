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
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class GriefDefenderProtector extends BukkitProtector {
  /**
   * This api is garbage, and obfuscated. If i can get a jar ill improve it, but
   * for now this is the best i can do. Or if someone wants to make a PR feel
   * free.
   * <p>
   * I as an author do not support this api, and do not recommend it, as they
   * are making ME pay $15(spigot) + $5(patreon) per month to be able to ask
   * questions in their discord, and get unobfuscated jars.
   *
   */

  @Override
  protected boolean checkRegion(Player player, Location location) {
    final Claim claim = GriefDefender.getCore().getClaimAt(location);
    return checkPerm(player, claim) || claim.isWilderness();
  }

  @Override
  protected boolean canPVP(Player player, Location entityLocation) {
    final Claim claim = GriefDefender.getCore().getClaimAt(entityLocation);
    if (checkPerm(player, claim)) {
      return claim.isPvpAllowed();
    }
    return false;
  }

  private boolean checkPerm(Player player, Claim claim) {
    if (claim == null) {
      return true;
    }
    UUID uuid = player.getUniqueId();
    return claim.isWilderness()
        || claim.getOwnerUniqueId().equals(uuid)
        || claim.getUserTrusts().contains(uuid);
  }

  @Override
  protected boolean canPVE(Player player, Location entityLocation) {
    return checkPerm(player, GriefDefender.getCore().getClaimAt(entityLocation));
  }

  @Override
  protected boolean canInteract(Player player, Location targetLocation) {
    return checkPerm(player, GriefDefender.getCore().getClaimAt(targetLocation));
  }

  @Override
  protected boolean canAccessChest(Player player, Location chestLocation) {
    return checkPerm(player, GriefDefender.getCore().getClaimAt(chestLocation));
  }

  @Override
  protected boolean canBlockBreak(Player player, Location blockLocation) {
    return checkPerm(player, GriefDefender.getCore().getClaimAt(blockLocation));
  }

  @Override
  protected boolean canBlockPlace(Player player, Location blockLocation) {
    return checkPerm(player, GriefDefender.getCore().getClaimAt(blockLocation));
  }

  @Override
  public String name() {
    return "GriefDefender";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isGriefdefender();
  }
}
