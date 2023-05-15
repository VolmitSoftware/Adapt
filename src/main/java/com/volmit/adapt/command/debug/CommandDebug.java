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

import com.volmit.adapt.command.CommandAdapt;
import io.github.mqzn.commands.annotations.Syntax;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;

@SubCommandInfo(name = "debug", parent = CommandAdapt.class ,children = {CommandParticle.class, CommandPGS.class, CommandPGA.class, CommandSound.class, CommandVerbose.class})
@Syntax(syntax = "")
public final class CommandDebug {
//todo: Default Executor

//    @Default
//    public static void info(CommandSender sender) {
//        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Debugger Help" + C.GRAY + "]: " + " === ---");

//    }

}
