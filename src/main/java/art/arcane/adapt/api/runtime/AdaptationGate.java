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
import art.arcane.adapt.api.skill.Skill;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class AdaptationGate {
    private AdaptationGate() {
    }

    public static boolean shouldSkipPlayer(Player player, Skill<?> skill, boolean hasAdaptPlayer) {
        if (player == null || skill == null) {
            return true;
        }

        if (!player.getClass().getSimpleName().equals("CraftPlayer")) {
            return true;
        }

        return !skill.isEnabled()
                || skill.hasBlacklistPermission(player, skill)
                || isWorldBlacklisted(player)
                || isInCreativeOrSpectator(player)
                || !hasAdaptPlayer;
    }

    public static boolean shouldSkipWorld(World world, Skill<?> skill) {
        if (world == null || skill == null) {
            return true;
        }

        return !skill.isEnabled() || AdaptConfig.get().blacklistedWorlds.contains(world.getName());
    }

    public static boolean isWorldBlacklisted(Player player) {
        if (player == null) {
            return true;
        }
        return AdaptConfig.get().blacklistedWorlds.contains(player.getWorld().getName());
    }

    public static boolean isInCreativeOrSpectator(Player player) {
        if (player == null) {
            return true;
        }

        return !AdaptConfig.get().isXpInCreative()
                && (player.getGameMode().equals(GameMode.CREATIVE) || player.getGameMode().equals(GameMode.SPECTATOR));
    }
}
