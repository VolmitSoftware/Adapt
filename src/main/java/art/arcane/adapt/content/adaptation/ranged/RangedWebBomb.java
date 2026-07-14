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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RangedWebBomb extends SimpleAdaptation<RangedWebBomb.Config> {
  private static final BlockData AIR = Material.AIR.createBlockData();
  private static final BlockData BLOCK = Material.COBWEB.createBlockData();
  private final Map<UUID, UUID> activeSnowballs;
  private final Set<Block> activeBlocks;

  public RangedWebBomb() {
    super("ranged-webshot");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.web_shot");
    setIcon(Material.COBWEB);
    setInterval(4900);
    registerRecipe(AdaptRecipe.shaped()
        .key("ranged-web-bomb")
        .ingredient(new MaterialChar('I', Material.COBWEB))
        .ingredient(new MaterialChar('S', Material.SNOWBALL))
        .shapes(List.of(
            "III",
            "ISI",
            "III"))
        .result(BoundSnowBall.io.withData(new BoundSnowBall.Data(null)))
        .build());
    activeBlocks = ConcurrentHashMap.newKeySet();
    activeSnowballs = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COBWEB)
        .key("challenge_ranged_web_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_web_200", "ranged.web-bomb.mobs-trapped", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("ranged.web_shot.lore1"));
    statLore(v, C.YELLOW, "+ ", level, 2);
  }


  @EventHandler
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Snowball snowball)) {
      return;
    }
    UUID shooterId = activeSnowballs.remove(snowball.getUniqueId());
    if (shooterId == null) {
      return;
    }
    Player p = Bukkit.getPlayer(shooterId);
    if (p == null || !p.isOnline()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Block block;

    if (e.getHitEntity() != null) {
      block = e.getHitEntity().getLocation().add(0, 1, 0).getBlock();
    } else if (e.getHitBlock() != null) {
      block = e.getHitBlock().getLocation().add(0, 1, 0).getBlock();
    } else {
      block = e.getEntity().getLocation().add(0, 1, 0).getBlock();
    }

    Location center = block.getLocation().add(0.5, 0.5, 0.5);
    FxPresets.chargeRing(this, center, 8);
    fx(center, FxPriority.GAMEPLAY)
        .burst(Particle.WHITE_ASH, 12, 0.4D)
        .burst(Particle.CLOUD, 6, 0.3D)
        .chord(Sound.BLOCK_WOOL_PLACE, 0.9F, 0.7F, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.7F, 0.9F);

    if (e.getHitEntity() != null) {
      addStat(p, "ranged.web-bomb.mobs-trapped", 1);
      fx(e.getHitEntity(), FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0.5D, 0, 0.3D, 0.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.6D, 0, 0.35D, 0.0D)
          .sound(Sound.BLOCK_WOOL_PLACE, 0.5F, 1.3F);
    }
    snowball.remove();
    Set<Block> locs = new HashSet<>();
    locs.add(block);
    locs.add(block.getLocation().add(0, 1, 0).getBlock());
    locs.add(block.getLocation().add(0, -1, 0).getBlock());
    locs.add(block.getLocation().add(0, 0, 1).getBlock());
    locs.add(block.getLocation().add(0, 0, -1).getBlock());
    locs.add(block.getLocation().add(1, 0, 0).getBlock());
    locs.add(block.getLocation().add(-1, 0, 0).getBlock());

    for (Block i : locs) {
      addWebFoundation(p, i, level);
    }

    J.runAt(block.getLocation(), () -> fx(center, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 6, 0.3D)
        .sound(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.7F, 1.0F), level * 20);
  }


  @EventHandler
  public void on(ProjectileLaunchEvent e) {
    if (e.getEntity().getShooter() instanceof Player p && e.getEntity() instanceof Snowball snowball && hasActiveAdaptation(p)) {
      if (BoundSnowBall.isBindableItem(snowball.getItem())) {
        activeSnowballs.put(snowball.getUniqueId(), p.getUniqueId());
      }
    }
  }

  public void addWebFoundation(Player p, Block block, int seconds) {
    if (!block.getType().isAir()) {
      return;
    }

    J.runAt(block.getLocation(), () -> {
      if (!block.getType().isAir() || !canBlockPlace(p, block.getLocation())) {
        return;
      }
      block.setBlockData(BLOCK);
      activeBlocks.add(block);
      J.runAt(block.getLocation(), () -> removeFoundation(block), seconds * 20);
    });
  }

  public void removeFoundation(Block block) {
    J.runAt(block.getLocation(), () -> {
      if (block.getBlockData().equals(BLOCK)) {
        block.setBlockData(AIR);
      }
      activeBlocks.remove(block);
    });
  }


  //prevent piston from moving blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonExtendEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        e.setCancelled(true);
      }
    });
  }

  //prevent piston from pulling blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonRetractEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        e.setCancelled(true);
      }
    });
  }

  //prevent TNT from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    if (activeBlocks.contains(e.getBlock())) {
      e.setCancelled(true);
    }
  }

  //prevent block from being destroyed // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (activeBlocks.contains(e.getBlock())) {
      e.setCancelled(true);
    }
  }

  //prevent Entities from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blockList().removeIf(activeBlocks::contains);
  }



  @ConfigDescription("Throw a crafted web snare to trap targets in cobwebs.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      initialCost = 1;
    }
  }
}
