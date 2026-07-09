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
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
  private static final BlockData AIR = Material.AIR.createBlockData();
  private static final BlockData BLOCK = Material.TINTED_GLASS.createBlockData();
  private final Map<UUID, Integer> blockPower;
  private final Map<UUID, Long> cooldowns;
  private final Set<UUID> active;
  private final Set<Block> activeBlocks;

  public ArchitectFoundation() {
    super("architect-foundation");
    registerConfiguration(ArchitectFoundation.Config.class);
    setIcon(Material.TINTED_GLASS);
    setInterval(988);
    blockPower = new ConcurrentHashMap<>();
    cooldowns = new ConcurrentHashMap<>();
    active = ConcurrentHashMap.newKeySet();
    activeBlocks = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SCAFFOLDING)
        .key("challenge_architect_foundation_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCAFFOLDING)
            .key("challenge_architect_foundation_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_foundation_1k", "architect.foundation.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_foundation_10k", "architect.foundation.blocks-placed", 10000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.foundation.lore1")
        + (getBlockPower(getLevelPercent(level))) + C.GRAY + " "
        + Localizer.dLocalize("architect.foundation.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      if (!p.isSneaking()) {
        return;
      }
      if (getActiveBlockPlaceLevel(p, p.getLocation()) <= 0) {
        return;
      }
      if (e.getTo() == null || !e.getFrom().getBlock().equals(e.getTo().getBlock())) {
        return;
      }
      if (!this.active.contains(id)) {
        return;
      }
      int power = blockPower.getOrDefault(id, 0);

      if (power <= 0) {
        return;
      }

      Location l = e.getTo();
      World world = l.getWorld();
      Set<Block> locs = new HashSet<>();
      locs.add(world.getBlockAt(l.clone().add(0.3, -1, -0.3)));
      locs.add(world.getBlockAt(l.clone().add(-0.3, -1, -0.3)));
      locs.add(world.getBlockAt(l.clone().add(0.3, -1, 0.3)));
      locs.add(world.getBlockAt(l.clone().add(-0.3, -1, +0.3)));

      int startPower = power;
      for (Block b : locs) {
        if (addFoundation(b)) {
          power--;
          addStat(p, "architect.foundation.blocks-placed", 1);
        }

        if (power <= 0) {
          break;
        }
      }

      if (power != startPower) {
        float pitch = (float) (0.8D + (0.8D * (power / (double) Math.max(1, getConfig().maxBlocks))));
        fx(l, FxPriority.TRAIL)
            .dustRing(Color.fromRGB(120, 255, 200), 0.6D, 12, 0.8F)
            .sound(Sound.BLOCK_DEEPSLATE_PLACE, 0.7f, pitch);
      }

      blockPower.put(id, power);
    });
  }

  // prevent piston from moving blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonExtendEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        Adapt.verbose("Cancelled Piston Extend on Adaptation Foundation Block");
        e.setCancelled(true);
      }
    });
  }

  // prevent piston from pulling blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonRetractEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        Adapt.verbose("Cancelled Piston Retract on Adaptation Foundation Block");
        e.setCancelled(true);
      }
    });
  }

  // prevent TNT from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    if (activeBlocks.contains(e.getBlock())) {
      Adapt.verbose("Cancelled Block Explosion on Adaptation Foundation Block");
      e.setCancelled(true);
    }
  }

  // prevent block from being destroyed // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (activeBlocks.contains(e.getBlock())) {
      e.setCancelled(true);
    }
  }

  // prevent Entities from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blockList().removeIf(activeBlocks::contains);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (p.getGameMode().equals(GameMode.CREATIVE)
          || p.getGameMode().equals(GameMode.SPECTATOR)) {
        return;
      }
      UUID id = p.getUniqueId();

      boolean ready = !hasCooldown(id);
      boolean active = this.active.contains(id);

      if (e.isSneaking() && ready && !active) {
        this.active.add(id);
        cooldowns.put(id, Long.MAX_VALUE);
        FxPresets.chargeRing(this, p.getLocation(), 6);
      } else if (!e.isSneaking() && active) {
        this.active.remove(id);
        cooldowns.put(id, M.ms() + getConfig().cooldown);
        fx(p.getLocation(), FxPriority.TRANSITION)
            .dustRing(Color.GRAY, 1.0D, 16, 1.0F)
            .chord(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f, Sound.BLOCK_SCULK_CATALYST_BREAK, 1.0f, 0.81f);
      }
    });
  }

  public boolean addFoundation(Block block) {
    if (!block.getType().isAir()) {
      return false;
    }

    if (!block.getWorld()
        .getNearbyEntities(block.getLocation()
            .add(.5, .5, .5), .5, .5, .5, entity ->
            entity instanceof ItemFrame || entity instanceof Painting).isEmpty())
      return false;


    J.runAt(block.getLocation(), () -> {
      block.setBlockData(BLOCK);
      activeBlocks.add(block);
    });
    J.runAt(block.getLocation(), () -> removeFoundation(block), 3 * 20);
    return true;
  }

  public void removeFoundation(Block block) {
    if (!block.getBlockData().equals(BLOCK)) {
      return;
    }

    J.runAt(block.getLocation(), () -> {
      block.setBlockData(AIR);
      activeBlocks.remove(block);
      fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
          .burst(Particle.ASH, 4, 0.2D)
          .sound(Sound.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.0f);
    });
  }

  public int getBlockPower(double factor) {
    return (int) Math.floor(M.lerp(getConfig().minBlocks, getConfig().maxBlocks, factor));
  }

  @Override
  public void onTick() {
    if (active.isEmpty() && blockPower.isEmpty()) {
      return;
    }
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player i = adaptPlayer.getPlayer();
      if (i == null || !i.isOnline()) {
        continue;
      }
      withPlayerThread(i, () -> refreshPlayerPower(i));
    }
  }

  private void refreshPlayerPower(Player i) {
    UUID id = i.getUniqueId();
    if (!hasActiveAdaptation(i)) {
      active.remove(id);
      blockPower.remove(id);
      cooldowns.remove(id);
      return;
    }

    boolean ready = !hasCooldown(id);
    int availablePower = getBlockPower(getLevelPercent(i));
    blockPower.compute(id, (k, v) -> {
      if (v == null || (ready && v != availablePower)) {
        fx(i.getLocation(), FxPriority.TRANSITION)
            .dustRing(Color.fromRGB(120, 255, 200), 1.2D, 16, 1.0F)
            .chord(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.81f);
        return availablePower;
      }
      return v;
    });
  }

  private boolean hasCooldown(UUID id) {
    Long cooldown = cooldowns.get(id);
    if (cooldown != null && M.ms() >= cooldown) {
      cooldowns.remove(id);
      cooldown = null;
    }

    return cooldown != null;
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    blockPower.remove(id);
    cooldowns.remove(id);
    active.remove(id);
  }

  @ConfigDescription("Sneak to place a temporary foundation beneath you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long duration = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int minBlocks = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int maxBlocks = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 5000;

    public Config() {
      baseCost = 5;
      costFactor = 0.40;
      initialCost = 1;
    }
  }
}
