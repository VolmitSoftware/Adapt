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

package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import lombok.Data;

import static art.arcane.adapt.Adapt.instance;

@Data
public class AdvancementHandler {
    private AdaptPlayer player;
    private boolean ready;

    public AdvancementHandler(AdaptPlayer player) {
        this.player = player;
        instance.getManager().unlockExisting(player);
    }

    public void grant(String key, boolean toast) {
        if (!AdaptConfig.get().isAdvancements()) return;
        instance.getManager().grant(getPlayer(), key, toast);
    }

    public void grant(String key) {
        grant(key, true);
    }
}
