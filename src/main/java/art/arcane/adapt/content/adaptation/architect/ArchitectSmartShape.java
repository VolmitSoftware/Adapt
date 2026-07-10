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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ArchitectSmartShape extends SimpleAdaptation<ArchitectSmartShape.Config> {
  private static final List<BlockFace> ROTATION_ORDER = Arrays.asList(
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
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.QUARTZ_STAIRS)
            .key("challenge_architect_smart_shape_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_smart_shape_200", "architect.smart-shape.rotations", 200, 300);
    registerMilestone("challenge_architect_smart_shape_5k", "architect.smart-shape.rotations", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("architect.smart_shape.lore1"));
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("architect.smart_shape.lore2"));
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
    withAdaptedPlayer(p, e, () -> {
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

  private int rotateData(BlockData data) {
    if (data instanceof Directional directional) {
      BlockFace next = getNextFace(directional.getFacing(), directional.getFaces());
      if (next != null && next != directional.getFacing()) {
        directional.setFacing(next);
        return directional.getFaces().size();
      }
    }

    if (data instanceof Rotatable rotatable) {
      BlockFace next = getNextFace(rotatable.getRotation(), Set.copyOf(ROTATION_ORDER), ROTATION_ORDER);
      if (next != null && next != rotatable.getRotation()) {
        rotatable.setRotation(next);
        return ROTATION_ORDER.size();
      }
    }

    if (data instanceof Orientable orientable) {
      Axis current = orientable.getAxis();
      Axis next = switch (current) {
        case X -> Axis.Y;
        case Y -> Axis.Z;
        case Z -> Axis.X;
      };

      if (orientable.getAxes().contains(next)) {
        orientable.setAxis(next);
        return orientable.getAxes().size();
      }

      if (orientable.getAxes().contains(Axis.X)) {
        orientable.setAxis(Axis.X);
        return orientable.getAxes().size();
      }
    }

    return 0;
  }

  private BlockFace getNextFace(BlockFace current, Set<BlockFace> supported) {
    if (supported == null || supported.isEmpty()) {
      return null;
    }

    List<BlockFace> ordered = new ArrayList<>(supported);
    ordered.sort(Comparator.comparingInt(Enum::ordinal));
    return getNextFace(current, supported, ordered);
  }

  private BlockFace getNextFace(BlockFace current, Set<BlockFace> supported, List<BlockFace> order) {
    if (supported == null || supported.isEmpty()) {
      return null;
    }

    int idx = order.indexOf(current);
    if (idx < 0) {
      for (BlockFace face : order) {
        if (supported.contains(face)) {
          return face;
        }
      }

      return null;
    }

    for (int i = 1; i <= order.size(); i++) {
      BlockFace candidate = order.get((idx + i) % order.size());
      if (supported.contains(candidate)) {
        return candidate;
      }
    }

    return current;
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
