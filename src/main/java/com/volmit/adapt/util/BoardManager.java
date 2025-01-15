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

package com.volmit.adapt.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DontObfuscate
public class BoardManager {
    @DontObfuscate
    private final Map<UUID, Board> scoreboards;
    @DontObfuscate
    private final BoardSettings boardSettings;

    @DontObfuscate
    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
        this.scoreboards = new ConcurrentHashMap<>();
        plugin.getServer().getOnlinePlayers().forEach(this::setup);
    }

    @DontObfuscate
    public void setup(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::resetScoreboard);
        if (player.getScoreboard().equals(Bukkit.getScoreboardManager() != null && player.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard()))) {
            player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard());
        }
        scoreboards.put(player.getUniqueId(), new Board(player, boardSettings));
    }

    @DontObfuscate
    public void remove(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
    }

    @DontObfuscate
    public Map<UUID, Board> getScoreboards() {
        return Collections.unmodifiableMap(scoreboards);
    }

}
