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

package art.arcane.adapt.util.common.input;

import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import org.bukkit.Input;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class DoubleJumpGesture {
  private final Map<UUID, State> states = PlayerStateRegistry.newPlayerMap();

  public boolean update(Player p) {
    State state = states.computeIfAbsent(p.getUniqueId(), unused -> new State());
    boolean jump = readJump(p);
    boolean onGround = p.isOnGround();
    boolean completed = false;

    if (onGround) {
      state.airbornePresses = 0;
    } else if (state.wasOnGround) {
      state.airbornePresses = jump ? 1 : 0;
    } else if (jump && !state.lastJump) {
      state.airbornePresses++;
      if (state.airbornePresses >= 2 && isJumpContext(p)) {
        state.airbornePresses = 0;
        completed = true;
      }
    }

    state.lastJump = jump;
    state.wasOnGround = onGround;
    return completed;
  }

  public void reset(Player p) {
    reset(p.getUniqueId());
  }

  public void reset(UUID id) {
    states.remove(id);
  }

  private boolean isJumpContext(Player p) {
    if (p.isGliding() || p.isInsideVehicle()) {
      return false;
    }

    Block feet = p.getLocation().getBlock();
    return !feet.isLiquid() && !Tag.CLIMBABLE.isTagged(feet.getType());
  }

  private boolean readJump(Player p) {
    try {
      Input input = p.getCurrentInput();
      return input != null && input.isJump();
    } catch (NoSuchMethodError ignored) {
      return false;
    }
  }

  private static final class State {
    private boolean lastJump;
    private boolean wasOnGround = true;
    private int airbornePresses;
  }
}
