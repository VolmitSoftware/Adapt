package com.volmit.adapt.api.notification;

import com.volmit.adapt.util.J;
import lombok.Builder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Builder
public class SoundNotification implements Notification
{
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

    public SoundNotification withXP(double xp)
    {
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

    public void play(Player p)
    {
        J.s(() -> p.playSound(p.getLocation(), sound, volume, pitch));
    }
}
