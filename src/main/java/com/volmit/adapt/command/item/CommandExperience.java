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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.AdaptSuggestionProvider;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.base.*;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "experience")
@ExecutionMeta(description = "Give yourself an experience orb", syntax = "<skillname> <amount> [player]", permission = "adapt.cheatitem")
public final class CommandExperience {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "skillname") @Suggest(provider = AdaptSuggestionProvider.class) String skillName,
                        @Arg(id = "amount") @Range int amount,
                        @Arg(id = "player", optional = true) @Nullable Player player) {

        Player targetPlayer = player;

        if (targetPlayer == null) {
            if (sender instanceof Player p) {
                targetPlayer = p;
            } else {
                FConst.error("You must be a player to use this command, or Reference a player").send(sender);
                return;
            }
        }

        if (skillName.equals("[all]")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                targetPlayer.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
            }
            FConst.success("Giving all orbs").send(sender);
            return;
        }

        if (skillName.equals("[random]")) {
            targetPlayer.getInventory().addItem(ExperienceOrb.with(SkillRegistry.skills.sortV().getRandom().getName(), amount));
            FConst.success("Giving random orb").send(sender);
            return;
        }

        Skill<?> skill = SkillRegistry.skills.get(skillName);
        if (skill != null) {
            targetPlayer.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
            FConst.success("Giving " + skill.getName() + " orb").send(sender);
        }
    }

    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Item Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt item (this command)").send(sender);
        FConst.info("/adapt item experience <Skill> <Amount> [Player]").send(sender);
        FConst.info("/adapt item knowledge <Skill> <Amount> [Player]").send(sender);
    }
}

