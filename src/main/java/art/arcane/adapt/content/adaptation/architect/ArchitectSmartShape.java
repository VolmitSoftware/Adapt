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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArchitectSmartShape extends SimpleAdaptation<ArchitectSmartShape.Config> {
  private static final List<BlockFace> YAW_ORDER = List.of(
      BlockFace.NORTH,
      BlockFace.EAST,
      BlockFace.SOUTH,
      BlockFace.WEST
  );
  private static final List<Axis> AXIS_ORDER = List.of(Axis.X, Axis.Y, Axis.Z);
  private static final List<BlockFace> ROTATION_ORDER = List.of(
      BlockFace.NORTH,
      BlockFace.NORTH_NORTH_EAST,
      BlockFace.NORTH_EAST,
      BlockFace.EAST_NORTH_EAST,
      BlockFace.EAST,
      BlockFace.EAST_SOUTH_EAST,
      BlockFace.SOUTH_EAST,
      BlockFace.SOUTH_SOUTH_EAST,
      BlockFace.SOUTH,
      BlockFace.SOUTH_SOUTH_WEST,
      BlockFace.SOUTH_WEST,
      BlockFace.WEST_SOUTH_WEST,
      BlockFace.WEST,
      BlockFace.WEST_NORTH_WEST,
      BlockFace.NORTH_WEST,
      BlockFace.NORTH_NORTH_WEST
  );

  public ArchitectSmartShape() {
    super("architect-smart-shape");
    registerConfiguration(Config.class);
    setIcon(Material.BRICKS);
    setInterval(800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.QUARTZ_STAIRS)
        .key("challenge_architect_smart_shape_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.QUARTZ_STAIRS)
            .key("challenge_architect_smart_shape_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_smart_shape_200", "architect.smart-shape.rotations", 200, 300);
    registerMilestone("challenge_architect_smart_shape_5k", "architect.smart-shape.rotations", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(ArchitectMessages.SMART_SHAPE_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(ArchitectMessages.SMART_SHAPE_LORE2));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, () -> {
      if (!p.isSneaking()) {
        return;
      }

      ItemStack hand = p.getInventory().getItemInMainHand();
      if (isItem(hand) && hand.getType() != Material.AIR) {
        return;
      }

      Block target = action == Action.LEFT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
      if (target == null) {
        return;
      }

      if (!canBlockPlace(p, target.getLocation())) {
        return;
      }

      BlockData data = target.getBlockData().clone();
      int options = rotateData(data);
      if (options <= 0) {
        fx(target.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.25f, 0.9f);
        return;
      }

      target.setBlockData(data, true);
      e.setCancelled(true);
      fx(target.getLocation().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
          .ring(Particles.CRIT_MAGIC, 0.55D, 8, 0.3D)
          .chord(Sound.ITEM_AXE_STRIP, 0.45f, 1.8f, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
      xp(p, Math.max(getConfig().minXpPerRotate, options * getConfig().xpPerOrientationOption));
      addStat(p, "architect.smart-shape.rotations", 1);
    });
  }

  static int rotateData(BlockData data) {
    if (data instanceof Directional directional) {
      List<BlockFace> yawFaces = yawFaces(directional.getFaces());
      BlockFace next = nextYawFace(directional.getFacing(), yawFaces);
      if (next != null) {
        directional.setFacing(next);
        return yawFaces.size();
      }
    }

    if (data instanceof Rotatable rotatable) {
      rotatable.setRotation(nextRotation(rotatable.getRotation()));
      return ROTATION_ORDER.size();
    }

    if (data instanceof Orientable orientable) {
      Axis next = nextAxis(orientable.getAxis(), orientable.getAxes());
      if (next != null) {
        orientable.setAxis(next);
        return orientable.getAxes().size();
      }
    }

    return 0;
  }

  /** Rotation is a spin around Y, so only the cardinal faces a block supports are reachable. */
  static List<BlockFace> yawFaces(Set<BlockFace> supported) {
    if (supported == null || supported.isEmpty()) {
      return List.of();
    }

    List<BlockFace> faces = new ArrayList<>(YAW_ORDER.size());
    for (BlockFace face : YAW_ORDER) {
      if (supported.contains(face)) {
        faces.add(face);
      }
    }

    return List.copyOf(faces);
  }

  /** Null for UP/DOWN facings: a spin around Y cannot move them, and forcing one detaches the block. */
  static BlockFace nextYawFace(BlockFace current, List<BlockFace> yawFaces) {
    if (yawFaces.size() < 2) {
      return null;
    }

    int index = yawFaces.indexOf(current);
    return index < 0 ? null : yawFaces.get((index + 1) % yawFaces.size());
  }

  /** Signs, banners and skulls yaw in vanilla 22.5 degree steps. */
  static BlockFace nextRotation(BlockFace current) {
    int index = ROTATION_ORDER.indexOf(current);
    return ROTATION_ORDER.get(index < 0 ? 0 : (index + 1) % ROTATION_ORDER.size());
  }

  /** Pillars have no upside down state, so every supported axis stays reachable. */
  static Axis nextAxis(Axis current, Set<Axis> supported) {
    if (supported == null || supported.size() < 2) {
      return null;
    }

    int index = Math.max(0, AXIS_ORDER.indexOf(current));
    for (int offset = 1; offset < AXIS_ORDER.size(); offset++) {
      Axis candidate = AXIS_ORDER.get((index + offset) % AXIS_ORDER.size());
      if (candidate != current && supported.contains(candidate)) {
        return candidate;
      }
    }

    return null;
  }


  @ConfigDescription("Sneak-left-click a block with an empty hand to rotate its orientation.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Xp Per Rotate for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minXpPerRotate = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Orientation Option for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerOrientationOption = 0.16;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
