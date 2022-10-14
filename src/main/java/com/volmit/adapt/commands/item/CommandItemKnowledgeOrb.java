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

package com.volmit.adapt.commands.item;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.content.item.KnowledgeOrb;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Objects;

public class CommandItemKnowledgeOrb extends MortarCommand {
    public CommandItemKnowledgeOrb() {
        super("knowledge", "k");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (Objects.equals(args[0], "[all]")) {
            for (Skill skill : SkillRegistry.skills.sortV()) {
                args.toList().set(0, skill.getName());
                giveOrb(sender, args);
            }
        } else {
            giveOrb(sender, args);
        }
        return true;
    }

    private boolean giveOrb(MortarSender sender, String[] args) {
        try {
            if (args.toList().size() > 2) {
                if (Bukkit.getPlayer(args[2]) != null && Bukkit.getPlayer(args[2]).getPlayer() != null) {
                    Bukkit.getPlayer(args[2]).getPlayer().getInventory().addItem(KnowledgeOrb.with(args[0], Integer.parseInt(args[1])));
                }
            } else if (args.toList().size() == 2) {
                sender.player().getInventory().addItem(KnowledgeOrb.with(args[0], Integer.parseInt(args[1])));
            }
            return true;
        } catch (Exception ignored) {
            printHelp(sender);
            Adapt.msgp(sender.player(),C.RED + "Invalid arguments!" + C.GRAY + " Command: /adapt item knowledge <Skill> <Knowledge Amount>");
            return true;
        }
    }
    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {
        if (args.length == 0) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                list.add(skill.getName());
            }
            list.add("[all]");
        }
        if (args.length == 1) {
            list.add(List.of("1", "10", "100", "1000", "10000", "100000", "1000000"));
        }
    }

    @Override
    protected String getArgsUsage() {
        return "/adapt item experience <Skill> <Knowledge Amount>";
    }
}
