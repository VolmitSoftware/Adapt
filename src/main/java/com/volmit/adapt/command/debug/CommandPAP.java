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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.base.Default;
import io.github.mqzn.commands.annotations.base.ExecutionMeta;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;

@SubCommandInfo(name = "pap", parent = CommandDebug.class)
@ExecutionMeta(description = "Generate Perms for Adaptations!")
public final class CommandPAP {

    @Default
    public void execute(CommandSender sender) {
        StringBuilder builder = new StringBuilder();
        Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> builder.append("adapt.blacklist.").append(adaptation.getName().replaceAll("-", "")).append("\n")));
        Adapt.info("Permissions: \n" + builder);
        FConst.success("Permissions have been printed to console.").send(sender);
    }
}
