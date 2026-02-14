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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ChronosSoundFX {
    private ChronosSoundFX() {
    }

    private static void play(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!areSoundsEnabled()) {
            return;
        }

        Location at = location.clone();
        Runnable playTask = () -> {
            World world = at.getWorld();
            if (world != null) {
                world.playSound(at, sound, volume, pitch);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            playTask.run();
        } else {
            J.s(playTask);
        }
    }

    private static void playLater(Location location, Sound sound, float volume, float pitch, int delayTicks) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!areSoundsEnabled()) {
            return;
        }

        Location at = location.clone();
        Runnable playTask = () -> {
            World world = at.getWorld();
            if (world != null) {
                world.playSound(at, sound, volume, pitch);
            }
        };

        if (delayTicks <= 0 && Bukkit.isPrimaryThread()) {
            playTask.run();
            return;
        }

        J.s(playTask, Math.max(0, delayTicks));
    }

    private static void playOnPlayer(Player player, Sound sound, float volume, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!areSoundsEnabled()) {
            return;
        }

        Runnable playTask = () -> {
            if (!player.isOnline()) {
                return;
            }
            Location at = player.getLocation();
            World world = at.getWorld();
            if (world != null) {
                world.playSound(at, sound, volume, pitch);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            playTask.run();
        } else {
            J.s(playTask);
        }
    }

    private static void playOnPlayerLater(Player player, Sound sound, float volume, float pitch, int delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!areSoundsEnabled()) {
            return;
        }

        Runnable playTask = () -> {
            if (!player.isOnline()) {
                return;
            }
            Location at = player.getLocation();
            World world = at.getWorld();
            if (world != null) {
                world.playSound(at, sound, volume, pitch);
            }
        };

        if (delayTicks <= 0 && Bukkit.isPrimaryThread()) {
            playTask.run();
            return;
        }

        J.s(playTask, Math.max(0, delayTicks));
    }

    public static void playClockReject(Player p) {
        Location l = p.getLocation();
        play(l, Sound.BLOCK_NOTE_BLOCK_BASS, 0.42f, 0.58f);
        play(l, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.32f, 0.62f);
    }

    public static void playCooldownReady(Player p) {
        Location l = p.getLocation();
        play(l, Sound.BLOCK_LEVER_CLICK, 0.35f, 1.75f);
        playLater(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.28f, 1.93f, 1);
    }

    public static void playBottleUse(Player p, Location at, int advanceTicks) {
        float pitch = Math.min(1.95f, 0.8f + (advanceTicks / 175f));
        play(at, Sound.BLOCK_LEVER_CLICK, 0.55f, pitch);
        play(at, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.35f, Math.min(2f, pitch + 0.24f));
        playLater(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.62f, Math.min(2f, pitch + 0.12f), 2);
    }

    public static void playRewindStart(Player p) {
        Location l = p.getLocation();
        play(l, Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.82f);
        play(l, Sound.BLOCK_LEVER_CLICK, 0.5f, 0.75f);
    }

    public static void playRewindStep(Player p, float progress) {
        Location l = p.getLocation();
        float clamped = Math.max(0f, Math.min(1f, progress));
        float pitch = 0.74f + (clamped * 0.95f);
        play(l, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.2f, Math.min(2f, pitch + 0.1f));
        play(l, Sound.BLOCK_LEVER_CLICK, 0.24f, pitch);
    }

    public static void playRewindFinish(Player p) {
        // Reverse wind-up + 3 tines with varied tone/volume, all following the caster.
        playOnPlayer(p, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.68f, 0.9f);
        playOnPlayerLater(p, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.76f, 1.16f, 1);
        playOnPlayerLater(p, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.84f, 1.42f, 2);

        // Low tine
        playOnPlayerLater(p, Sound.BLOCK_BELL_RESONATE, 1.70f, 0.78f, 3);
        playOnPlayerLater(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.88f, 1.28f, 4);

        // Mid tine
        playOnPlayerLater(p, Sound.BLOCK_BELL_RESONATE, 1.44f, 0.98f, 7);
        playOnPlayerLater(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.80f, 1.5f, 8);

        // High tine + trailing echo
        playOnPlayerLater(p, Sound.BLOCK_BELL_RESONATE, 1.16f, 1.2f, 11);
        playOnPlayerLater(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.72f, 1.74f, 12);
        playOnPlayerLater(p, Sound.BLOCK_BELL_RESONATE, 0.56f, 1.34f, 15);
    }

    public static void playTouchProc(Player p, Location target) {
        Location l = target == null ? p.getLocation() : target;
        play(l, Sound.BLOCK_LEVER_CLICK, 0.34f, 1.7f);
        play(l, Sound.BLOCK_NOTE_BLOCK_BASS, 0.26f, 1.18f);
    }

    public static void playTimeBombArm(Player p) {
        Location l = p.getLocation();
        play(l, Sound.BLOCK_LEVER_CLICK, 0.46f, 1.35f);
        play(l, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.35f, 1.1f);
    }

    public static void playTimeBombDetonate(Location center) {
        play(center, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        play(center, Sound.BLOCK_LEVER_CLICK, 0.8f, 0.68f);
        playLater(center, Sound.BLOCK_BELL_RESONATE, 0.55f, 1.05f, 2);
    }

    public static void playTimeFieldTick(Location center, float pitch) {
        float clamped = Math.max(0.35f, Math.min(2f, pitch));
        play(center, Sound.BLOCK_NOTE_BLOCK_BASS, 0.14f, Math.max(0.3f, Math.min(2f, clamped * 0.78f)));
        play(center, Sound.BLOCK_LEVER_CLICK, 0.22f, clamped);
        play(center, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.15f, Math.min(2f, clamped + 0.18f));
    }

    private static boolean areSoundsEnabled() {
        AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
        return effects == null || effects.isSoundsEnabled();
    }
}
