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

package com.volmit.adapt.command.boost;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.command.CommandAdapt;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.Arg;
import io.github.mqzn.commands.annotations.Default;
import io.github.mqzn.commands.annotations.Syntax;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "boost", parent = CommandAdapt.class)
@Syntax(syntax = "<seconds> <multiplier> [player]")
public final class CommandBoost {

    @Default
    public static void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Boost Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt boost(this command)").send(sender);
        FConst.info("/adapt boost <Seconds for Boost> <Boost amount (double multiplier)> (GLOBAL))").send(sender);
        FConst.info("/adapt boost <Seconds for Boost> <Boost amount (double multiplier)> <Player> (PLAYER SPECIFIC)").send(sender);
    }

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "seconds") int seconds,
                        @Arg(id = "multiplier") double multiplier,
                        @Arg(id = "player", optional = true) @Nullable Player player) {

        AdaptServer adaptServer = Adapt.instance.getAdaptServer();
        if (player == null) {
            adaptServer.boostXP(multiplier, seconds * 1000);
        } else {
            AdaptPlayer adaptPlayer = adaptServer.getPlayer(player);
            adaptPlayer.boostXPToRecents(multiplier, seconds * 1000);
        }
        FConst.success("Boosted XP by " + multiplier + " for " + seconds + " seconds").send(sender);
    }
}

