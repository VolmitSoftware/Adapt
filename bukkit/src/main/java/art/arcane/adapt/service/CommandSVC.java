/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.command.BukkitCommandSource;
import art.arcane.adapt.command.AdaptCommands;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CommandSVC implements AdaptService, CommandExecutor, TabCompleter {
  private final AdaptCommands commands = new AdaptCommands();

  @Override
  public void onEnable() {
    Adapt.verbose("Initializing Commands...");
    PluginCommand command = Adapt.instance.getCommand(AdaptCommands.ROOT_COMMAND);
    if (command == null) {
      Adapt.warn("Failed to find command '" + AdaptCommands.ROOT_COMMAND + "'");
      return;
    }

    command.setExecutor(this);
    command.setTabCompleter(this);
    J.a(commands::engine);
  }

  @Override
  public void onDisable() {

  }

  public AdaptCommands commands() {
    return commands;
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(AdaptCommands.ROOT_COMMAND)) {
      return List.of();
    }
    return commands.complete(new BukkitCommandSource(sender), alias, args);
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(AdaptCommands.ROOT_COMMAND)) {
      return false;
    }
    commands.execute(new BukkitCommandSource(sender), label, args);
    return true;
  }
}
