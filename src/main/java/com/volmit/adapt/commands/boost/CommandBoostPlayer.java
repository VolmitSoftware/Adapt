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

package com.volmit.adapt.commands.boost;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.List;

public class CommandBoostPlayer extends MortarCommand {
    public CommandBoostPlayer() {
        super("player", "p");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        try {
            if (Bukkit.getOnlinePlayers().stream().map(HumanEntity::getName).toList().contains(args[0])) {
                Player p = Bukkit.getPlayer(args[0]);
                AdaptPlayer ap = Adapt.instance.getAdaptServer().getPlayer(p);
                AdaptServer as = Adapt.instance.getAdaptServer();

                ap.boostXPToRecents(ap, Double.parseDouble(args[1]), Integer.parseInt(args[2])); // not working
                p.sendMessage("BOOSTED " + args[1] + " XP TO " + args[2] + " ALL RECENT SKILL GAINS");
                Adapt.info("BOOSTED " + args[1] + " XP TO " + args[2] + " ALL RECENT SKILL GAINS");
            }
            AdaptPlayer ap = Adapt.instance.getAdaptServer().getPlayer(sender.player());

            return true;
        }  catch (Exception ignored) {
            printHelp(sender);
            return true;
        }
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
