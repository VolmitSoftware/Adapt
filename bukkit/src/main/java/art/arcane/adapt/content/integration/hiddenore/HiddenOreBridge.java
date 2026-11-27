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

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.api.BreakCause;
import art.arcane.hiddenore.api.HiddenOreAPI;
import art.arcane.hiddenore.api.HiddenVein;
import art.arcane.hiddenore.api.event.HiddenOreDropsEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class HiddenOreBridge implements Listener {
  private static final Id PICKAXE = Id.adapt("pickaxe");

  static List<HiddenOreLink.VeinTarget> veins(Location origin, int radius) {
    HiddenOreAPI api = api();
    if (api == null) {
      return List.of();
    }

    List<HiddenVein> found = api.veinsNear(origin, radius);
    if (found.isEmpty()) {
      return List.of();
    }
    List<HiddenOreLink.VeinTarget> result = new ArrayList<>(found.size());
    for (HiddenVein vein : found) {
      result.add(toTarget(origin.getWorld(), vein));
    }
    return result;
  }

  static List<Block> veinSiblings(Block block) {
    HiddenOreAPI api = api();
    if (api == null) {
      return List.of();
    }

    List<HiddenVein> siblings = api.veinSiblings(block);
    if (siblings.isEmpty()) {
      return List.of();
    }
    World world = block.getWorld();
    List<Block> result = new ArrayList<>(siblings.size());
    for (HiddenVein vein : siblings) {
      result.add(world.getBlockAt(vein.x(), vein.y(), vein.z()));
    }
    return result;
  }

  @EventHandler
  public void on(HiddenOreDropsEvent e) {
    if (e.getCause() == BreakCause.EXPLODED) {
      return;
    }

    Player p = e.getPlayer();
    applyAutosmelt(p, e);
    applyDropToInventory(p, e);
    awardOreXp(p, e);
  }

  private void applyAutosmelt(Player p, HiddenOreDropsEvent e) {
    PickaxeAutosmelt autosmelt = adaptation(PickaxeAutosmelt.class);
    if (autosmelt == null || autosmelt.getActiveLevel(BukkitRefs.player(p)) <= 0) {
      return;
    }

    boolean smelted = false;
    List<ItemStack> drops = e.getDrops();
    for (int i = 0; i < drops.size(); i++) {
      ItemStack stack = drops.get(i);
      if (stack == null) {
        continue;
      }
      Material result = switch (stack.getType()) {
        case RAW_IRON -> Material.IRON_INGOT;
        case RAW_GOLD -> Material.GOLD_INGOT;
        case RAW_COPPER -> Material.COPPER_INGOT;
        default -> null;
      };
      if (result == null) {
        continue;
      }
      drops.set(i, new ItemStack(result, stack.getAmount()));
      smelted = true;
    }

    if (!smelted) {
      return;
    }
    AdaptServer adaptServer = Adapt.instance.getAdaptServer();
    AdaptPlayer adaptPlayer = adaptServer.getOnlineAdaptPlayer(p.getUniqueId());
    if (adaptPlayer != null && adaptPlayer.isRuntimeReady() && adaptPlayer.getPlayer() == BukkitRefs.player(p)) {
      adaptServer.addStat(p.getUniqueId(), "pickaxe.autosmelt.ores-smelted", 1);
    }
    Location at = e.getBlock().getLocation();
    if (soundsEnabled()) {
      SoundPlayer.of(BukkitRefs.world(e.getBlock().getWorld())).play(BukkitRefs.loc(at), Sounds.BLOCK_LAVA_POP, 1, 1);
    }
    if (particlesEnabled()) {
      e.getBlock().getWorld().spawnParticle(Particle.LAVA, at, 3, 0.5, 0.5, 0.5);
    }
  }

  private void applyDropToInventory(Player p, HiddenOreDropsEvent e) {
    PickaxeDropToInventory dropToInventory = adaptation(PickaxeDropToInventory.class);
    if (dropToInventory != null && dropToInventory.getActiveLevel(BukkitRefs.player(p)) > 0) {
      e.setToInventory(true);
    }
  }

  private void awardOreXp(Player p, HiddenOreDropsEvent e) {
    HiddenVein vein = e.getVein();
    if (vein == null) {
      return;
    }
    Material display = vein.oreDisplay() != null ? vein.oreDisplay() : vein.item();
    if (!(pickaxe() instanceof SkillPickaxes pickaxe)) {
      return;
    }
    double value = pickaxe.blockValue(BukkitRefs.id(display));
    if (value <= 0) {
      return;
    }
    pickaxe.xp(BukkitRefs.player(p), BukkitRefs.loc(e.getBlock().getLocation()), value);
  }

  private static Skill<?> pickaxe() {
    AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    return server == null ? null : server.getSkillRegistry().getSkill(PICKAXE);
  }

  private static <T> T adaptation(Class<T> type) {
    Skill<?> pickaxe = pickaxe();
    if (pickaxe == null) {
      return null;
    }
    for (Adaptation<?> adaptation : pickaxe.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }
    return null;
  }

  private static HiddenOreAPI api() {
    if (!(Bukkit.getPluginManager().getPlugin("HiddenOre") instanceof HiddenOre hiddenOre)) {
      return null;
    }
    return hiddenOre.getApi();
  }

  private static HiddenOreLink.VeinTarget toTarget(World world, HiddenVein vein) {
    Material display = vein.oreDisplay() != null ? vein.oreDisplay() : vein.item();
    return new HiddenOreLink.VeinTarget(new Location(world, vein.x(), vein.y(), vein.z()), display);
  }

  private static boolean particlesEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isParticlesEnabled();
  }

  private static boolean soundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }
}
