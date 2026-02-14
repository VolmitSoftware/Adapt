/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.adapt.util.decree.handlers;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.decree.handlers.base.PlayerHandlerBase;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PlayerHandler extends PlayerHandlerBase implements DecreeParameterHandler<Player> {
    @Override
    protected List<Player> playerOptions() {
        if (Adapt.instance != null && Adapt.instance.getAdaptServer() != null) {
            return new ArrayList<>(Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot());
        }
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }
}
