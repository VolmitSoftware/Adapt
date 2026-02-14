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

package art.arcane.adapt.api.notification;

import art.arcane.adapt.api.world.AdaptPlayer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TitleNotification implements Notification {
    @Builder.Default
    private final long in = 250;
    @Builder.Default
    private final long stay = 1450;
    @Builder.Default
    private final long out = 750;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String subtitle = " ";
    @Builder.Default
    private final String group = "default";

    @Override
    public long getTotalDuration() {
        return in + out + stay;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        p.getPlayer().sendTitle(title.isEmpty() ? " " : title, subtitle, (int) (in / 50D), (int) (stay / 50D), (int) (out / 50D));
    }
}
