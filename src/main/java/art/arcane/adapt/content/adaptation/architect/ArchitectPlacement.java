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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectPlacement extends SimpleAdaptation<ArchitectPlacement.Config> {
  private final Map<UUID, Map<Block, BlockFace>> totalMap = new ConcurrentHashMap<>();
  private final Map<UUID, Map<PreviewKey, BlockDisplay>> previewDisplays = new ConcurrentHashMap<>();

  public ArchitectPlacement() {
    super("architect-placement");
    registerConfiguration(ArchitectPlacement.Config.class);
    setDescription(Localizer.dLocalize("architect.placement.description"));
    setDisplayName(Localizer.dLocalize("architect.placement.name"));
    setIcon(Material.SCAFFOLDING);
    setInterval(360);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BRICKS)
        .key("challenge_architect_placement_1k")
        .title(Localizer.dLocalize("advancement.challenge_architect_placement_1k.title"))
        .description(Localizer.dLocalize("advancement.challenge_architect_placement_1k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BRICKS)
            .key("challenge_architect_placement_25k")
            .title(Localizer.dLocalize("advancement.challenge_architect_placement_25k.title"))
            .description(Localizer.dLocalize("advancement.challenge_architect_placement_25k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_placement_1k", "architect.placement.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_placement_25k", "architect.placement.blocks-placed", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.placement.lore3"));
  }

  private BlockFace getBlockFace(Player player) {
    List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 5);
    if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding())
      return null;
    Block targetBlock = lastTwoTargetBlocks.get(1);
    Block adjacentBlock = lastTwoTargetBlocks.get(0);
    return targetBlock.getFace(adjacentBlock);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    totalMap.remove(id);
    clearPreviewDisplays(id);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      SoundPlayer sp = SoundPlayer.of(p);
      if (getActiveLevel(p, Player::isSneaking) <= 0) {
        return;
      }

      Map<Block, BlockFace> blocks = totalMap.get(id);
      if (blocks == null || blocks.isEmpty()) {
        return;
      }

      ItemStack hand = e.getItemInHand();
      Block first = null;
      for (Block candidate : blocks.keySet()) {
        first = candidate;
        break;
      }
      if (!hand.getType().isBlock() || first == null || first.getType() != hand.getType()) {
        return;
      }

      double v = getValue(e.getBlock());
      Block ignored = null;
      for (Map.Entry<Block, BlockFace> entry : blocks.entrySet()) {
        Block source = entry.getKey();
        BlockFace face = entry.getValue();
        if (source == null || face == null) {
          continue;
        }
        if (source.getRelative(face).equals(e.getBlock())) {
          ignored = source;
          break;
        }
      }

      if (hand.getAmount() < blocks.size()) {
        Adapt.messagePlayer(p, C.RED + Localizer.dLocalize("architect.placement.lore1") + " " + C.GREEN + blocks.size() + C.RED + " " + Localizer.dLocalize("architect.placement.lore2"));
        return;
      }

      if (ignored != null) {
        blocks.remove(ignored);
      }
      for (Map.Entry<Block, BlockFace> entry : blocks.entrySet()) {
        Block b = entry.getKey();
        BlockFace face = entry.getValue();
        if (b == null || face == null) {
          continue;
        }

        Block relative = b.getRelative(face);
        if (!relative.getType().isAir()) {
          continue;
        }

        if (!canBlockPlace(p, relative.getLocation())) {
          Adapt.verbose("Player " + p.getName() + " doesn't have permission.");
          continue;
        }

        relative.setBlockData(b.getBlockData());
        getPlayer(p).getData().addStat("blocks.placed", 1);
        getPlayer(p).getData().addStat("blocks.placed.value", v);
        getPlayer(p).getData().addStat("architect.placement.blocks-placed", 1);
        sp.play(b.getLocation(), Sound.BLOCK_AZALEA_BREAK, 0.4f, 0.25f);
        xp(p, 2);

        hand.setAmount(hand.getAmount() - 1);
      }

      if (ignored != null) {
        e.getBlock().setBlockData(ignored.getBlockData());
        getPlayer(p).getData().addStat("blocks.placed", 1);
        getPlayer(p).getData().addStat("blocks.placed.value", v);
        getPlayer(p).getData().addStat("architect.placement.blocks-placed", 1);
        sp.play(ignored.getLocation(), Sound.BLOCK_AZALEA_BREAK, 0.4f, 0.25f);
        xp(p, 2);

        hand.setAmount(hand.getAmount() - 1);
      } else {
        e.setCancelled(true);
      }

      totalMap.remove(id);
      clearPreviewDisplays(id);
      if (hand.getAmount() > 0) {
        runPlayerViewport(getBlockFace(p), p.getTargetBlock(null, 5), p.getInventory().getItemInMainHand().getType(), p);
      }
    });
  }


  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      int level = getActiveLevel(p);
      if (level <= 0) {
        totalMap.remove(id);
        clearPreviewDisplays(id);
        return;
      }

      if (e.isSneaking()) {
        totalMap.remove(id);
        clearPreviewDisplays(id);
      }

      if (!e.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
        Block block = p.getTargetBlock(null, 5); // 5 is the range of player
        if (block instanceof Container) { // return if block is a container
          return;
        }
        Material handMaterial = p.getInventory().getItemInMainHand().getType();
        if (handMaterial.isAir()) {
          return;
        }
        BlockFace viewPortBlock = getBlockFace(p);
        runPlayerViewport(viewPortBlock, block, handMaterial, p);
      }
    });
  }


  @EventHandler
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      int level = getActiveLevel(p);
      if (level <= 0) {
        totalMap.remove(id);
        clearPreviewDisplays(id);
        return;
      }

      if (!p.isSneaking()) {
        totalMap.remove(id);
        clearPreviewDisplays(id);
      }

      if (p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
        Block block = p.getTargetBlock(null, 5); // 5 is the range of player
        if (block instanceof Container) { // return if block is a container
          return;
        }
        Material handMaterial = p.getInventory().getItemInMainHand().getType();
        if (handMaterial.isAir()) {
          return;
        }
        BlockFace viewPortBlock = getBlockFace(p);
        runPlayerViewport(viewPortBlock, block, handMaterial, p);
      }
    });
  }

  public void runPlayerViewport(BlockFace viewPortBlock, Block block, Material handMaterial, Player p) {
    UUID id = p.getUniqueId();
    if (viewPortBlock == null || block == null || handMaterial == null || handMaterial.isAir()) {
      totalMap.remove(id);
      clearPreviewDisplays(id);
      return;
    }

    Map<Block, BlockFace> map = new HashMap<>();

    if (viewPortBlock.getDirection().equals(BlockFace.NORTH.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.SOUTH.getDirection())) { // North & South = X
      for (int x = block.getX() - 1; x <= block.getX() + 1; x++) { // 1 is the radius of the blocks
        for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
          addViewportEntry(map, block.getWorld().getBlockAt(x, y, block.getZ()), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock.getDirection().equals(BlockFace.EAST.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.WEST.getDirection())) { // East & West = Z
      for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
        for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
          addViewportEntry(map, block.getWorld().getBlockAt(block.getX(), y, z), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock.getDirection().equals(BlockFace.UP.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.DOWN.getDirection())) { // Up & Down = Y
      for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
        for (int x = block.getX() - 1; x <= block.getX() + 1; x++) {
          addViewportEntry(map, block.getWorld().getBlockAt(x, block.getY(), z), viewPortBlock, handMaterial);
        }
      }
    }

    if (map.isEmpty()) {
      totalMap.remove(id);
      clearPreviewDisplays(id);
      return;
    }

    totalMap.put(id, map);
  }

  private void addViewportEntry(Map<Block, BlockFace> map, Block target, BlockFace viewPortBlock, Material handMaterial) {
    if (target == null || viewPortBlock == null || handMaterial == null) {
      return;
    }

    int maxBlocks = Math.max(1, getConfig().maxBlocks);
    if (map.size() >= maxBlocks) {
      return;
    }

    if (target.getType() == handMaterial) {
      map.put(target, viewPortBlock);
    }
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }


  @Override
  public void onTick() {
    if (previewDisplays.isEmpty() && totalMap.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, Map<PreviewKey, BlockDisplay>> entry : previewDisplays.entrySet()) {
      UUID playerId = entry.getKey();
      Map<PreviewKey, BlockDisplay> displays = entry.getValue();
      if (!totalMap.containsKey(playerId) && previewDisplays.remove(playerId, displays)) {
        clearPreviewDisplays(displays);
      }
    }

    if (totalMap.isEmpty()) {
      return;
    }

    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline() || !totalMap.containsKey(p.getUniqueId())) {
        continue;
      }
      withPlayerThread(p, () -> renderPreview(p));
    }
  }

  private void renderPreview(Player p) {
    UUID id = p.getUniqueId();
    Map<Block, BlockFace> blockRender = totalMap.get(id);
    if (getActiveLevel(p, Player::isSneaking) <= 0 || blockRender == null || blockRender.isEmpty()) {
      totalMap.remove(id);
      clearPreviewDisplays(id);
      return;
    }

    Set<PreviewKey> activePreviews = new HashSet<>();
    boolean displayPreview = getConfig().useDisplayEntities;

    for (Map.Entry<Block, BlockFace> entry : blockRender.entrySet()) {
      Block b = entry.getKey();
      BlockFace bf = entry.getValue();
      if (b == null || bf == null || b instanceof Container) {
        continue;
      }

      Block transposedBlock = b.getRelative(bf);
      if (displayPreview) {
        if (!transposedBlock.getType().isAir()) {
          continue;
        }

        PreviewKey key = PreviewKey.of(transposedBlock);
        activePreviews.add(key);
        ensurePreviewDisplay(id, key, b.getBlockData());
      } else if (areParticlesEnabled()) {
        vfxCuboidOutline(transposedBlock, Particle.REVERSE_PORTAL);
      }
    }

    if (displayPreview) {
      clearStalePreviewDisplays(id, activePreviews);
    } else {
      clearPreviewDisplays(id);
    }
  }

  private void ensurePreviewDisplay(UUID playerId, PreviewKey key, org.bukkit.block.data.BlockData sourceData) {
    if (key == null || sourceData == null) {
      return;
    }

    Map<PreviewKey, BlockDisplay> displays = previewDisplays.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
    BlockDisplay existing = displays.get(key);
    if (existing != null) {
      if (existing.isValid()) {
        if (J.isFoliaThreading()) {
          J.runEntity(existing, () -> {
            if (!existing.isValid()) {
              displays.remove(key, existing);
              return;
            }
            existing.setBlock(sourceData);
            showPreviewToOwner(playerId, existing);
          });
        } else {
          existing.setBlock(sourceData);
          showPreviewToOwner(playerId, existing);
        }
        return;
      }
      displays.remove(key);
    }

    Runnable spawnTask = () -> {
      if (!totalMap.containsKey(playerId)) {
        return;
      }

      org.bukkit.World world = Bukkit.getWorld(key.worldId());
      if (world == null) {
        return;
      }

      Block targetBlock = world.getBlockAt(key.x(), key.y(), key.z());
      BlockDisplay live = displays.get(key);
      if (live != null && live.isValid()) {
        live.setBlock(sourceData);
        showPreviewToOwner(playerId, live);
        return;
      }

      BlockDisplay spawned = world.spawn(targetBlock.getLocation(), BlockDisplay.class, display -> {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(1);
        display.setViewRange((float) Math.max(0.25, getConfig().displayEntityViewRange));
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBlock(sourceData);
      });
      displays.put(key, spawned);
      showPreviewToOwner(playerId, spawned);
    };

    if (J.isFoliaThreading()) {
      org.bukkit.World world = Bukkit.getWorld(key.worldId());
      if (world == null) {
        return;
      }
      J.runAt(new org.bukkit.Location(world, key.x() + 0.5, key.y(), key.z() + 0.5), spawnTask);
      return;
    }

    spawnTask.run();
  }

  private void clearStalePreviewDisplays(UUID playerId, Set<PreviewKey> activePreviews) {
    Map<PreviewKey, BlockDisplay> displays = previewDisplays.get(playerId);
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (Map.Entry<PreviewKey, BlockDisplay> entry : displays.entrySet()) {
      PreviewKey key = entry.getKey();
      if (key == null || activePreviews.contains(key)) {
        continue;
      }

      BlockDisplay removed = entry.getValue();
      if (removed != null && displays.remove(key, removed)) {
        removeDisplayEntity(removed);
      }
    }

    if (displays.isEmpty()) {
      previewDisplays.remove(playerId);
    }
  }

  private void clearPreviewDisplays(UUID playerId) {
    Map<PreviewKey, BlockDisplay> displays = previewDisplays.remove(playerId);
    clearPreviewDisplays(displays);
  }

  private void clearPreviewDisplays(Map<PreviewKey, BlockDisplay> displays) {
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (BlockDisplay display : displays.values()) {
      removeDisplayEntity(display);
    }
  }

  private void removeDisplayEntity(Entity entity) {
    if (entity == null) {
      return;
    }

    if (J.isFoliaThreading()) {
      J.runEntity(entity, () -> {
        if (entity.isValid()) {
          entity.remove();
        }
      });
    } else if (entity.isValid()) {
      entity.remove();
    }
  }

  private void showPreviewToOwner(UUID playerId, Entity entity) {
    if (entity == null || !entity.isValid()) {
      return;
    }

    Player owner = Bukkit.getPlayer(playerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    if (J.isFoliaThreading()) {
      J.runEntity(owner, () -> {
        if (entity.isValid()) {
          owner.showEntity(Adapt.instance, entity);
        }
      });
      return;
    }

    owner.showEntity(Adapt.instance, entity);
  }

  private record PreviewKey(UUID worldId, int x, int y, int z) {
    private static PreviewKey of(Block block) {
      return new PreviewKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  @NoArgsConstructor
  @ConfigDescription("Place multiple blocks at once while sneaking with a matching block.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Architect Placement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int maxBlocks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Placement adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Use owner-only block display previews instead of particles for the wand guide.", impact = "True shows ghost blocks only to the wand user; false keeps particle outlines.")
    boolean useDisplayEntities = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "View range used for wand preview display entities.", impact = "Lower values hide previews sooner; higher values keep them visible from farther away.")
    double displayEntityViewRange = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 2;
  }
}
