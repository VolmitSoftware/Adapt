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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

public final class ChronosSoundFX {
  private ChronosSoundFX() {
  }

  private static void play(Loc location, Id sound, float volume, float pitch) {
    if (location == null || location.world() == null) {
      return;
    }
    if (!areSoundsEnabled()) {
      return;
    }

    Loc at = location;
    Runnable playTask = () -> emit(at, sound, volume, pitch);

    if (AdaptApi.scheduler().isRegionized()) {
      AdaptApi.scheduler().runAt(at, playTask);
    } else if (AdaptApi.scheduler().isTickThread()) {
      playTask.run();
    } else {
      AdaptApi.scheduler().runAt(at, playTask);
    }
  }

  private static void playLater(Loc location, Id sound, float volume, float pitch, int delayTicks) {
    if (location == null || location.world() == null) {
      return;
    }
    if (!areSoundsEnabled()) {
      return;
    }

    Loc at = location;
    Runnable playTask = () -> emit(at, sound, volume, pitch);

    if (!AdaptApi.scheduler().isRegionized() && delayTicks <= 0 && AdaptApi.scheduler().isTickThread()) {
      playTask.run();
      return;
    }

    AdaptApi.scheduler().runAt(at, playTask, Math.max(0, delayTicks));
  }

  private static void playOnPlayer(PlayerRef player, Id sound, float volume, float pitch) {
    if (player == null) {
      return;
    }
    if (!areSoundsEnabled()) {
      return;
    }

    Runnable playTask = () -> {
      if (!player.isOnline()) {
        return;
      }
      Loc at = player.location();
      emit(at, sound, volume, pitch);
    };

    if (AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().owns(player)) {
      playTask.run();
    } else if (!AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().isTickThread()) {
      playTask.run();
    } else {
      AdaptApi.scheduler().runEntity(player, playTask);
    }
  }

  private static void playOnPlayerLater(PlayerRef player, Id sound, float volume, float pitch, int delayTicks) {
    if (player == null) {
      return;
    }
    if (!areSoundsEnabled()) {
      return;
    }

    Runnable playTask = () -> {
      if (!player.isOnline()) {
        return;
      }
      Loc at = player.location();
      emit(at, sound, volume, pitch);
    };

    if (delayTicks <= 0
        && ((AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().owns(player))
        || (!AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().isTickThread()))) {
      playTask.run();
      return;
    }

    AdaptApi.scheduler().runEntity(player, playTask, Math.max(0, delayTicks));
  }

  public static void playClockReject(PlayerRef p) {
    playOnPlayer(p, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.42f, 0.58f);
    playOnPlayer(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.32f, 0.62f);
  }

  public static void playCooldownReady(PlayerRef p) {
    playOnPlayer(p, Sounds.BLOCK_LEVER_CLICK, 0.35f, 1.75f);
    playOnPlayerLater(p, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.28f, 1.93f, 1);
  }

  public static void playBottleUse(PlayerRef p, Loc at, int advanceTicks) {
    float pitch = Math.min(1.95f, 0.8f + (advanceTicks / 175f));
    play(at, Sounds.BLOCK_LEVER_CLICK, 0.55f, pitch);
    play(at, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.35f, Math.min(2f, pitch + 0.24f));
    playLater(at, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.62f, Math.min(2f, pitch + 0.12f), 2);
  }

  public static void playRewindStart(PlayerRef p) {
    playOnPlayer(p, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.82f);
    playOnPlayer(p, Sounds.BLOCK_LEVER_CLICK, 0.5f, 0.75f);
  }

  public static void playRewindStep(PlayerRef p, float progress) {
    float clamped = Math.max(0f, Math.min(1f, progress));
    float pitch = 0.74f + (clamped * 0.95f);
    playOnPlayer(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.2f, Math.min(2f, pitch + 0.1f));
    playOnPlayer(p, Sounds.BLOCK_LEVER_CLICK, 0.24f, pitch);
  }

  public static void playRewindFinish(PlayerRef p) {
    // Reverse wind-up + 3 tines with varied tone/volume, all following the caster.
    playOnPlayer(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.68f, 0.9f);
    playOnPlayerLater(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.76f, 1.16f, 1);
    playOnPlayerLater(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.84f, 1.42f, 2);

    // Low tine
    playOnPlayerLater(p, Sounds.BLOCK_BELL_RESONATE, 1.70f, 0.78f, 3);
    playOnPlayerLater(p, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.88f, 1.28f, 4);

    // Mid tine
    playOnPlayerLater(p, Sounds.BLOCK_BELL_RESONATE, 1.44f, 0.98f, 7);
    playOnPlayerLater(p, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.80f, 1.5f, 8);

    // High tine + trailing echo
    playOnPlayerLater(p, Sounds.BLOCK_BELL_RESONATE, 1.16f, 1.2f, 11);
    playOnPlayerLater(p, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.72f, 1.74f, 12);
    playOnPlayerLater(p, Sounds.BLOCK_BELL_RESONATE, 0.56f, 1.34f, 15);
  }

  public static void playTouchProc(PlayerRef p, Loc target) {
    if (target == null) {
      playOnPlayer(p, Sounds.BLOCK_LEVER_CLICK, 0.34f, 1.7f);
      playOnPlayer(p, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.26f, 1.18f);
      return;
    }
    play(target, Sounds.BLOCK_LEVER_CLICK, 0.34f, 1.7f);
    play(target, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.26f, 1.18f);
  }

  public static void playTimeBombArm(PlayerRef p) {
    playOnPlayer(p, Sounds.BLOCK_LEVER_CLICK, 0.46f, 1.35f);
    playOnPlayer(p, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.35f, 1.1f);
  }

  public static void playTimeBombDetonate(Loc center) {
    play(center, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
    play(center, Sounds.BLOCK_LEVER_CLICK, 0.8f, 0.68f);
    playLater(center, Sounds.BLOCK_BELL_RESONATE, 0.55f, 1.05f, 2);
  }

  public static void playTimeFieldTick(Loc center, float pitch) {
    float clamped = Math.max(0.35f, Math.min(2f, pitch));
    play(center, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.14f, Math.max(0.3f, Math.min(2f, clamped * 0.78f)));
    play(center, Sounds.BLOCK_LEVER_CLICK, 0.22f, clamped);
    play(center, Sounds.ENTITY_ITEM_FRAME_ROTATE_ITEM, 0.15f, Math.min(2f, clamped + 0.18f));
  }

  private static void emit(Loc at, Id sound, float volume, float pitch) {
    AdaptApi.platform().fx().sound(at.world(), at.x(), at.y(), at.z(), sound, SoundCategory.MASTER, volume, pitch);
  }

  private static boolean areSoundsEnabled() {
    return AdaptApi.settings().sounds();
  }
}
