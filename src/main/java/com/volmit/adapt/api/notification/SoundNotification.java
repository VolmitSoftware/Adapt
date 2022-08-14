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

import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Sound;

@Data
@Builder
public class SoundNotification implements Notification {
    @Builder.Default
    private final long isolation = 0;
    @Builder.Default
    private final long predelay = 0;
    @Builder.Default
    private final Sound sound = Sound.BLOCK_LEVER_CLICK;
    @Builder.Default
    private final float volume = 1F;
    @Builder.Default
    private final float pitch = 1F;
    @Builder.Default
    private final String group = "default";

    public SoundNotification withXP(double xp) {
        double sig = xp / 1000D;
        float pitch = this.pitch;
        float volume = this.volume;
        pitch -= sig / 6.6;
        pitch = pitch < 0.1 ? (float) 0.1 : pitch;
        double vp = sig / 5;
        vp = Math.min(vp, 0.8);
        volume += vp;
        pitch = pitch < 0.1 ? (float) 0.1 : pitch;

        return SoundNotification.builder()
            .sound(sound)
            .isolation(isolation)
            .predelay(predelay)
            .volume(volume)
            .pitch(pitch)
            .build();
    }

    @Override
    public long getTotalDuration() {
        return isolation;
    }

    @Override
    public String getGroup() {
        return group;
    }

    public void play(AdaptPlayer p) {
        J.s(() -> p.getPlayer().playSound(p.getPlayer().getLocation(), sound, volume, pitch));
    }
}
