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

package art.arcane.adapt.api.runtime;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.WorldRef;

public final class AdaptationGate {
  private AdaptationGate() {
  }

  public static boolean shouldSkipPlayer(PlayerRef player, Skill<?> skill, boolean hasAdaptPlayer) {
    if (player == null || skill == null) {
      return true;
    }

    if (!isServerPlayer(player)) {
      return true;
    }

    return !skill.isEnabled()
        || !skill.hasUsePermission(player, skill)
        || isWorldBlacklisted(player)
        || isInCreativeOrSpectator(player)
        || !hasAdaptPlayer;
  }

  public static boolean shouldSkipWorld(WorldRef world, Skill<?> skill) {
    if (world == null || skill == null) {
      return true;
    }

    return !skill.isEnabled() || isWorldBlacklisted(world);
  }

  public static boolean isWorldBlacklisted(PlayerRef player) {
    if (player == null) {
      return true;
    }

    return isWorldBlacklisted(player.world());
  }

  public static boolean isWorldBlacklisted(WorldRef world) {
    return world != null && AdaptConfig.get().isWorldBlacklisted(world.key().toString());
  }

  public static boolean isInCreativeOrSpectator(PlayerRef player) {
    if (player == null) {
      return true;
    }

    GameMode mode = player.gameMode();
    return !AdaptConfig.get().isXpInCreative()
        && (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
  }

  private static boolean isServerPlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    return core != null && player.isOnline() && core.getPlatform().host().player(player.uuid()) != null;
  }
}
