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
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

import java.util.List;

public class CommandBoostGlobal extends MortarCommand {
    public CommandBoostGlobal() {
        super("global", "g");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (!sender.hasPermission("adapt.boost")) {
            sender.sendMessage("You do not have permission to use this command.");
            Adapt.info("Player: " + sender.getName() + " attempted to use command " + this + " without permission.");
            return true;
        }

        try {
            AdaptServer as = Adapt.instance.getAdaptServer();

            as.boostXP(Double.parseDouble(args[0]), Integer.parseInt(args[1]));
            Adapt.info("BOOSTED " + args[0] + " XP TO " + args[1] + " ALL SKILL GAINS");
            sender.sendMessage("BOOSTED " + args[0] + " XP TO " + args[1] + " ALL SKILL GAINS");

            return true;
        } catch (Exception ignored) {
            Adapt.verbose("BOOST FAILED");
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
