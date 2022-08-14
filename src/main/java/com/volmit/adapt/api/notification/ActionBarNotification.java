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

package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.M;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionBarNotification implements Notification {
    @Builder.Default
    private final long duration = 750;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String group = "default";
    @Builder.Default
    private final long maxTTL = Long.MAX_VALUE;

    @Override
    public long getTotalDuration() {
        if(M.ms() > maxTTL) {
            return 0;
        }
        return duration;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        if(M.ms() > maxTTL) {
            return;
        }

        Adapt.actionbar(p.getPlayer(), title);
    }
}
