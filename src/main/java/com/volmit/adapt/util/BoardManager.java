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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DontObfuscate
public class BoardManager {
    @DontObfuscate
    private final JavaPlugin plugin;
    @DontObfuscate
    private final Map<UUID, Board> scoreboards;
    @DontObfuscate
    private final BukkitTask updateTask;
    @DontObfuscate
    private BoardSettings boardSettings;

    @DontObfuscate
    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.scoreboards = new ConcurrentHashMap<>();
        this.updateTask = new BoardUpdateTask(this).runTaskTimer(plugin, 2L, 2L);
        plugin.getServer().getOnlinePlayers().forEach(this::setup);
    }

    @DontObfuscate
    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
        scoreboards.values().forEach(board -> board.setBoardSettings(boardSettings));
    }

    @DontObfuscate
    public boolean hasBoard(Player player) {
        return scoreboards.containsKey(player.getUniqueId());
    }

    @DontObfuscate
    public Optional<Board> getBoard(Player player) {
        return Optional.ofNullable(scoreboards.get(player.getUniqueId()));
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

    @DontObfuscate
    public void onDisable() {
        updateTask.cancel();
        plugin.getServer().getOnlinePlayers().forEach(this::remove);
        scoreboards.clear();
    }
}
