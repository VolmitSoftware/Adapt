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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StealthGlowCoordinator {
  private final Map<GlowKey, GlowState> glows = new HashMap<>();
  private final AtomicBoolean failureLogged = new AtomicBoolean();

  public StealthGlowCoordinator() {
  }

  synchronized boolean isAvailable() {
    return Adapt.instance != null && Adapt.instance.getGlowingEntities() != null;
  }

  synchronized boolean set(Layer layer, Entity entity, Player viewer, ChatColor color) {
    if (layer == null || entity == null || viewer == null || color == null) {
      return false;
    }
    GlowingEntities glowingEntities = resolveGlowingEntities();
    if (glowingEntities == null) {
      return false;
    }

    GlowKey key = new GlowKey(viewer.getUniqueId(), entity.getUniqueId());
    GlowState state = glows.get(key);
    int runtimeEntityId = entity.getEntityId();
    if (state != null && (state.runtimeEntityId != runtimeEntityId || state.entity != entity)) {
      if (!unsetPacket(glowingEntities, state.runtimeEntityId, viewer)) {
        return false;
      }
      glows.remove(key);
      state = null;
    }
    if (state == null) {
      state = new GlowState(entity, runtimeEntityId);
      glows.put(key, state);
    }

    ChatColor previous = state.visibleColor();
    state.colors.put(layer, color);
    ChatColor desired = state.visibleColor();
    if (previous == desired) {
      return true;
    }
    if (!setPacket(glowingEntities, entity, viewer, desired)) {
      state.colors.remove(layer);
      if (state.colors.isEmpty()) {
        glows.remove(key);
      }
      return false;
    }
    return true;
  }

  synchronized boolean unset(Layer layer, UUID targetId, int runtimeEntityId, Player viewer) {
    if (layer == null || targetId == null || viewer == null) {
      return false;
    }
    GlowKey key = new GlowKey(viewer.getUniqueId(), targetId);
    GlowState state = glows.get(key);
    if (state == null || state.runtimeEntityId != runtimeEntityId || state.colors.remove(layer) == null) {
      return true;
    }

    GlowingEntities glowingEntities = resolveGlowingEntities();
    if (glowingEntities == null) {
      glows.remove(key);
      return false;
    }
    if (state.colors.isEmpty()) {
      glows.remove(key);
      return unsetPacket(glowingEntities, state.runtimeEntityId, viewer);
    }
    return setPacket(glowingEntities, state.entity, viewer, state.visibleColor());
  }

  synchronized void discardViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    glows.keySet().removeIf(key -> key.viewerId().equals(viewerId));
  }

  private GlowingEntities resolveGlowingEntities() {
    return Adapt.instance == null ? null : Adapt.instance.getGlowingEntities();
  }

  private boolean setPacket(GlowingEntities glowingEntities, Entity entity, Player viewer, ChatColor color) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.setGlowing(entity, viewer, color);
      }
      return true;
    } catch (ReflectiveOperationException | IllegalStateException error) {
      reportFailure(error);
      return false;
    }
  }

  private boolean unsetPacket(GlowingEntities glowingEntities, int runtimeEntityId, Player viewer) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.unsetGlowing(runtimeEntityId, viewer);
      }
      return true;
    } catch (ReflectiveOperationException | IllegalStateException error) {
      reportFailure(error);
      return false;
    }
  }

  private void reportFailure(Exception error) {
    if (!failureLogged.compareAndSet(false, true)) {
      return;
    }
    Adapt.error("Failed to update a private Stealth glow.");
    error.printStackTrace();
  }

  enum Layer {
    SIGHT,
    THREAT
  }

  private record GlowKey(UUID viewerId, UUID targetId) {
  }

  private static final class GlowState {
    private final Entity entity;
    private final int runtimeEntityId;
    private final EnumMap<Layer, ChatColor> colors = new EnumMap<>(Layer.class);

    private GlowState(Entity entity, int runtimeEntityId) {
      this.entity = entity;
      this.runtimeEntityId = runtimeEntityId;
    }

    private ChatColor visibleColor() {
      ChatColor threat = colors.get(Layer.THREAT);
      return threat == null ? colors.get(Layer.SIGHT) : threat;
    }
  }
}
