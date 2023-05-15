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

package com.volmit.adapt.command.item;

import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.content.item.ExperienceOrb;
import com.volmit.adapt.util.command.AdaptSuggestionProvider;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.Arg;
import io.github.mqzn.commands.annotations.Greedy;
import io.github.mqzn.commands.annotations.Suggest;
import io.github.mqzn.commands.annotations.Syntax;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "experience", parent = CommandItem.class)
@Syntax(syntax = "<skillName> <amount> [player]")
public final class CommandExperience {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "skillamount") @Suggest(provider = AdaptSuggestionProvider.class) @Greedy String skillName,
                        @Arg(id = "amount") int amount,
                        @Arg(id = "player", optional = true) @Nullable Player player) {

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            if (skillName.equals(skill.getName())) {
                if (player != null) {
                    player.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
                } else {
                    if (!(sender instanceof Player p)) {
                        FConst.error("You must be a player to use this command").send(sender);
                        return;
                    } else {
                        p.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
                    }
                }
                FConst.success("Giving " + skill.getName() + " orb").send(sender);
            }
        }
    }
}

