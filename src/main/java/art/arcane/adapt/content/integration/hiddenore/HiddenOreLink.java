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

package art.arcane.adapt.content.integration.hiddenore;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;

public final class HiddenOreLink {
  private static volatile boolean active = false;

  private HiddenOreLink() {
  }

  public record VeinTarget(Location location, Material display) {
  }

  public static void activate(Adapt plugin) {
    plugin.registerListener(new HiddenOreBridge());
    active = true;
    Adapt.info("HiddenOre link active: seismic ping, quarry sense, veinminer + drop transforms wired to hidden veins");
  }

  public static boolean isActive() {
    return active;
  }

  public static VeinTarget nearestVein(Location origin, int range) {
    if (!active) {
      return null;
    }
    return HiddenOreBridge.nearestVein(origin, range);
  }

  public static List<VeinTarget> veins(Location origin, int radius) {
    if (!active) {
      return List.of();
    }
    return HiddenOreBridge.veins(origin, radius);
  }

  public static List<Block> veinSiblings(Block block) {
    if (!active) {
      return List.of();
    }
    return HiddenOreBridge.veinSiblings(block);
  }
}
