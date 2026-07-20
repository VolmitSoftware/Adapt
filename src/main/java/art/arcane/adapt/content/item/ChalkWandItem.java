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

package art.arcane.adapt.content.item;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ChalkWandItem {
  private static final NamespacedKey TOOL_KEY = new NamespacedKey("adapt", "architect_chalk_tool");
  private static final NamespacedKey WORLD_KEY = new NamespacedKey("adapt", "architect_chalk_world");
  private static final NamespacedKey POINTS_KEY = new NamespacedKey("adapt", "architect_chalk_points");
  private static final NamespacedKey PLANE_KEY = new NamespacedKey("adapt", "architect_chalk_plane");
  private static final int MAX_CONTROL_POINTS = 32;
  private static final int MAX_HORIZONTAL_COORDINATE = 30_000_000;
  private static final int MAX_VERTICAL_COORDINATE = 4_096;

  private ChalkWandItem() {
  }

  public static ItemStack create(Tool tool) {
    ItemStack item = new ItemStack(Material.STICK);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }

    meta.setDisplayName(C.AQUA + Localizer.dLocalize(tool.localizationKey() + ".name"));
    meta.setLore(List.of(
        C.GRAY + Localizer.dLocalize(tool.localizationKey() + ".usage"),
        C.GRAY + Localizer.dLocalize("architect.chalk_line.tool.clear"),
        C.DARK_GRAY + Localizer.dLocalize("architect.chalk_line.tool.persistent")
    ));
    meta.setMaxStackSize(1);
    meta.setEnchantmentGlintOverride(true);
    meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.STRING, tool.id());
    item.setItemMeta(meta);
    return item;
  }

  public static WandData read(ItemStack item) {
    if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
      return null;
    }

    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return null;
    }

    PersistentDataContainer data = meta.getPersistentDataContainer();
    Tool tool = Tool.fromId(data.get(TOOL_KEY, PersistentDataType.STRING));
    if (tool == null) {
      return null;
    }

    String worldValue = data.get(WORLD_KEY, PersistentDataType.STRING);
    int[] encodedPoints = data.get(POINTS_KEY, PersistentDataType.INTEGER_ARRAY);
    if (worldValue == null || encodedPoints == null) {
      return new WandData(tool, Plan.empty());
    }

    UUID worldId = parseWorldId(worldValue);
    List<Point> points = decodePoints(encodedPoints);
    if (worldId == null || points == null || points.isEmpty()) {
      return new WandData(tool, Plan.empty());
    }

    Plane plane = Plane.fromId(data.get(PLANE_KEY, PersistentDataType.STRING));
    return new WandData(tool, new Plan(worldId, points, plane == null ? Plane.XZ : plane));
  }

  public static boolean writePlan(ItemStack item, Plan plan) {
    if (item == null || plan == null || read(item) == null) {
      return false;
    }

    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }

    PersistentDataContainer data = meta.getPersistentDataContainer();
    if (plan.isEmpty()) {
      data.remove(WORLD_KEY);
      data.remove(POINTS_KEY);
      data.remove(PLANE_KEY);
    } else {
      if (plan.worldId() == null || plan.points().size() > MAX_CONTROL_POINTS) {
        return false;
      }
      for (Point point : plan.points()) {
        if (!isValid(point)) {
          return false;
        }
      }
      data.set(WORLD_KEY, PersistentDataType.STRING, plan.worldId().toString());
      data.set(POINTS_KEY, PersistentDataType.INTEGER_ARRAY, encodePoints(plan.points()));
      data.set(PLANE_KEY, PersistentDataType.STRING, plan.plane().id());
    }
    item.setItemMeta(meta);
    return true;
  }

  static int[] encodePoints(List<Point> points) {
    int[] encoded = new int[points.size() * 3];
    for (int index = 0; index < points.size(); index++) {
      Point point = points.get(index);
      int offset = index * 3;
      encoded[offset] = point.x();
      encoded[offset + 1] = point.y();
      encoded[offset + 2] = point.z();
    }
    return encoded;
  }

  static List<Point> decodePoints(int[] encoded) {
    if (encoded == null || encoded.length == 0 || encoded.length % 3 != 0
        || encoded.length / 3 > MAX_CONTROL_POINTS) {
      return null;
    }

    List<Point> points = new ArrayList<>(encoded.length / 3);
    for (int offset = 0; offset < encoded.length; offset += 3) {
      Point point = new Point(encoded[offset], encoded[offset + 1], encoded[offset + 2]);
      if (!isValid(point)) {
        return null;
      }
      points.add(point);
    }
    return List.copyOf(points);
  }

  private static UUID parseWorldId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static boolean isValid(Point point) {
    return Math.abs((long) point.x()) <= MAX_HORIZONTAL_COORDINATE
        && Math.abs((long) point.z()) <= MAX_HORIZONTAL_COORDINATE
        && Math.abs((long) point.y()) <= MAX_VERTICAL_COORDINATE;
  }

  public enum Tool {
    STRAIGHTEDGE("straightedge", "architect-chalk-straightedge", 1),
    POLYLINE("polyline", "architect-chalk-polyline", 2),
    COMPASS("compass", "architect-chalk-compass", 3),
    ARC_BOW("arc_bow", "architect-chalk-arc-bow", 4);

    private final String id;
    private final String recipeKey;
    private final int requiredLevel;

    Tool(String id, String recipeKey, int requiredLevel) {
      this.id = id;
      this.recipeKey = recipeKey;
      this.requiredLevel = requiredLevel;
    }

    public String id() {
      return id;
    }

    public String recipeKey() {
      return recipeKey;
    }

    public int requiredLevel() {
      return requiredLevel;
    }

    public String localizationKey() {
      return "architect.chalk_line.tool." + id;
    }

    public static Tool fromId(String id) {
      if (id == null) {
        return null;
      }
      for (Tool tool : values()) {
        if (tool.id.equals(id)) {
          return tool;
        }
      }
      return null;
    }

    public static Tool fromRecipeKey(String recipeKey) {
      if (recipeKey == null) {
        return null;
      }
      for (Tool tool : values()) {
        if (tool.recipeKey.equals(recipeKey)) {
          return tool;
        }
      }
      return null;
    }
  }

  public enum Plane {
    XY,
    XZ,
    YZ;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Plane fromId(String id) {
      if (id == null) {
        return null;
      }
      try {
        return valueOf(id.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }

    public static Plane fromFace(BlockFace face) {
      if (face == BlockFace.EAST || face == BlockFace.WEST) {
        return YZ;
      }
      if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
        return XY;
      }
      return XZ;
    }
  }

  public record Point(int x, int y, int z) {
  }

  public record Plan(UUID worldId, List<Point> points, Plane plane) {
    public Plan {
      points = points == null ? List.of() : List.copyOf(points);
      plane = plane == null ? Plane.XZ : plane;
    }

    public static Plan empty() {
      return new Plan(null, List.of(), Plane.XZ);
    }

    public boolean isEmpty() {
      return worldId == null || points.isEmpty();
    }
  }

  public record WandData(Tool tool, Plan plan) {
  }
}
