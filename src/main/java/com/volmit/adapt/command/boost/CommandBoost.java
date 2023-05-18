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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.base.Arg;
import io.github.mqzn.commands.annotations.base.Default;
import io.github.mqzn.commands.annotations.base.ExecutionMeta;
import io.github.mqzn.commands.annotations.base.Range;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "boost")
@ExecutionMeta(description = "Boost Target player, or Global Experience gain.", syntax = "<seconds> <multiplier> [player]", permission = "adapt.boost")
public final class CommandBoost {

    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Boost Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt boost(this command)").send(sender);
        FConst.info("/adapt boost <Seconds> <Multiplier> [player])").send(sender);
        FConst.info(C.ITALIC + "The Multiplier is a Double, and if you dont specify a player its global").send(sender);
    }

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "seconds") @Range(min = "1", max = "100000") int seconds,
                        @Arg(id = "multiplier") @Range(min = "0.0", max = "100.0") double multiplier,
                        @Arg(id = "player", optional = true)  @Nullable Player player) {

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

