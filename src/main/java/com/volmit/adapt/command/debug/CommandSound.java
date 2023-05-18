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

package com.volmit.adapt.command.debug;

import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.command.SoundSuggestionProvider;
import io.github.mqzn.commands.annotations.base.Arg;
import io.github.mqzn.commands.annotations.base.Default;
import io.github.mqzn.commands.annotations.base.ExecutionMeta;
import io.github.mqzn.commands.annotations.base.Suggest;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SubCommandInfo(name = "sound", parent = CommandDebug.class)
@ExecutionMeta(description = "Play a sound!", syntax = "<sound>")
public final class CommandSound {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "sound") @Suggest(provider = SoundSuggestionProvider.class) Sound sound) {
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), sound, 1, 1);
        }
    }

    @Default
    public void info(CommandSender sender) {
        FConst.info("Sounds: " + Sound.values().length);
    }
}