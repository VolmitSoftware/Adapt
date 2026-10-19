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
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.WaterMob;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.function.Function;

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
  public boolean canPVE(Player player, Entity victim, Adaptation<?> adaptation) {
    if (!GriefPrevention.instance.config_claims_protectCreatures || !protectedCreature(victim)) {
      return true;
    }
    return allowed(victim.getLocation(), claim -> claim.allowContainers(player));
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return allowed(chestLocation, claim -> claim.allowContainers(player));
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
    } else if (claim.getPermission(player.getUniqueId().toString()) == ClaimPermission.Build) {
      return true;
    }

    return playerData.ignoreClaims || claim.isAdminClaim() && player.hasPermission("griefprevention.adminclaims");

  }

  private static boolean allowed(Location location, Function<Claim, String> denial) {
    Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
    return claim == null || denial.apply(claim) == null;
  }

  private static boolean protectedCreature(Entity entity) {
    return (entity instanceof Creature || entity instanceof WaterMob) && !monster(entity);
  }

  private static boolean monster(Entity entity) {
    if (entity instanceof Monster || entity instanceof Shulker) {
      return true;
    }
    if (entity instanceof Rabbit rabbit) {
      return rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY;
    }
    if (entity instanceof Panda panda) {
      return panda.getMainGene() == Panda.Gene.AGGRESSIVE;
    }
    if ((entity instanceof Hoglin || entity instanceof PolarBear) && entity instanceof Mob mob) {
      return !lured(entity) && mob.getTarget() != null;
    }
    return false;
  }

  private static boolean lured(Entity entity) {
    return entity.getPersistentDataContainer().has(new NamespacedKey(GriefPrevention.instance, "lured_by_player"), PersistentDataType.BYTE);
  }


}
