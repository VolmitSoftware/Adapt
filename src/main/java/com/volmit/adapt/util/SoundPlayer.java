package com.volmit.adapt.util;

import com.volmit.adapt.AdaptConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SoundPlayer {

    private final Collection<Player> players;

    public static SoundPlayer of(Collection<Player> players) {
        return new SoundPlayer(players);
    }

    public static SoundPlayer of(Player player) {
        return new SoundPlayer(List.of(player));
    }

    public static SoundPlayer of(World world) {
        return new SoundPlayer(world.getPlayers());
    }

    public void play(@NotNull Location location, @NotNull Sound sound) {
        play(location, sound, 1.0f, 1.0f);
    }

    public void play(@NotNull Entity entity, @NotNull Sound sound, float volume, float pitch) {
        play(entity.getLocation(), sound, volume, pitch);
    }

    public void play(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
        if (!areSoundsEnabled()) {
            return;
        }
        players.forEach(player -> player.playSound(location, sound, volume, pitch));
        //J.s(() -> Objects.requireNonNull(location.getWorld()).playSound(location, sound, volume, pitch));
    }

    public void play(@NotNull Location location, @NotNull Sound sound, SoundCategory category, float volume, float pitch) {
        if (!areSoundsEnabled()) {
            return;
        }
        players.forEach(player -> player.playSound(location, sound, category, volume, pitch));
        //J.s(() -> Objects.requireNonNull(location.getWorld()).playSound(location, sound, volume, pitch));
    }

    private boolean areSoundsEnabled() {
        AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
        return effects == null || effects.isSoundsEnabled();
    }

}
