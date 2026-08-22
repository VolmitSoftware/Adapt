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
import art.arcane.adapt.api.protection.RegionPolicy;
import art.arcane.adapt.api.protection.RegionPolicySource;
import art.arcane.adapt.util.common.scheduling.J;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DoubleFlag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class WorldGuardRegionPolicySource implements RegionPolicySource {
  private final RegionContainer container;

  public WorldGuardRegionPolicySource() {
    container = WorldGuard.getInstance().getPlatform().getRegionContainer();
  }

  @Override
  public String getName() {
    return "WorldGuard";
  }

  @Override
  public RegionPolicy resolve(Player player, Location location) {
    if (!AdaptConfig.get().getProtectorSupport().isWorldguard()) {
      return RegionPolicy.DEFAULT;
    }

    World world = location.getWorld();
    if (world == null) {
      return RegionPolicy.DEFAULT;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
      return RegionPolicy.DEFAULT;
    }

    LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
    ApplicableRegionSet regions = container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));
    boolean xpAllowed = resolveXpAllowed(regions, localPlayer, world);
    double xpMultiplier = resolveXpMultiplier(regions, localPlayer);
    int powerBonus = resolvePowerBonus(regions, localPlayer);
    Set<String> unlocks = resolveUnlocks(regions, localPlayer);

    if (xpAllowed && xpMultiplier == 1D && powerBonus == 0 && unlocks.isEmpty()) {
      return RegionPolicy.DEFAULT;
    }

    return new RegionPolicy(xpAllowed, xpMultiplier, powerBonus, unlocks);
  }

  private boolean resolveXpAllowed(ApplicableRegionSet regions, LocalPlayer localPlayer, World world) {
    StateFlag flag = WorldGuardFlags.adaptXp();
    if (flag == null || hasBypass(localPlayer, world)) {
      return true;
    }

    return regions.queryState(localPlayer, flag) != StateFlag.State.DENY;
  }

  private double resolveXpMultiplier(ApplicableRegionSet regions, LocalPlayer localPlayer) {
    DoubleFlag flag = WorldGuardFlags.adaptXpMultiplier();
    if (flag == null) {
      return 1D;
    }

    Double value = regions.queryValue(localPlayer, flag);
    return value == null ? 1D : value;
  }

  private int resolvePowerBonus(ApplicableRegionSet regions, LocalPlayer localPlayer) {
    IntegerFlag flag = WorldGuardFlags.adaptPowerBonus();
    if (flag == null) {
      return 0;
    }

    Integer value = regions.queryValue(localPlayer, flag);
    return value == null ? 0 : value;
  }

  private Set<String> resolveUnlocks(ApplicableRegionSet regions, LocalPlayer localPlayer) {
    SetFlag<String> flag = WorldGuardFlags.adaptUnlockAdaptations();
    if (flag == null) {
      return Set.of();
    }

    Collection<Set<String>> values = regions.queryAllValues(localPlayer, flag);
    if (values == null || values.isEmpty()) {
      return Set.of();
    }

    Set<String> union = new LinkedHashSet<>();
    for (Set<String> value : values) {
      if (value != null) {
        union.addAll(value);
      }
    }
    return union;
  }

  private boolean hasBypass(LocalPlayer localPlayer, World world) {
    return WorldGuard.getInstance().getPlatform().getSessionManager()
        .hasBypass(localPlayer, BukkitAdapter.adapt(world));
  }
}
