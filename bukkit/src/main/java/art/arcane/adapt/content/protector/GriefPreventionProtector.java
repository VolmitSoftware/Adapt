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
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.function.Function;

public class GriefPreventionProtector extends BukkitProtector {


  @Override
  protected boolean canBlockBreak(Player player, Location location) {
    return canEditClaim(player, location);
  }

  @Override
  protected boolean canBlockPlace(Player player, Location location) {
    return canEditClaim(player, location);
  }

  @Override
  protected boolean canPVE(Player player, Entity victim) {
    if (!GriefPrevention.instance.config_claims_protectCreatures || !ClaimCreatures.protectedCreature(victim, GriefPreventionProtector::lured)) {
      return true;
    }
    return allowed(victim.getLocation(), false, claim -> claim.allowContainers(player));
  }

  @Override
  protected boolean canAccessChest(Player player, Location chestLocation) {
    return allowed(chestLocation, true, claim -> claim.allowContainers(player));
  }

  @Override
  public String name() {
    return "GriefPrevention";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isGriefprevention();
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
    } else if (claim.getPermission(player.getUniqueId().toString()) == ClaimPermission.Build) {
      return true;
    }

    return playerData.ignoreClaims || claim.isAdminClaim() && player.hasPermission("griefprevention.adminclaims");

  }

  private static boolean allowed(Location location, boolean ignoreHeight, Function<Claim, String> denial) {
    Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, ignoreHeight, null);
    return claim == null || denial.apply(claim) == null;
  }

  private static boolean lured(Entity entity) {
    return entity.getPersistentDataContainer().has(new NamespacedKey(GriefPrevention.instance, "lured_by_player"), PersistentDataType.BYTE);
  }


}
