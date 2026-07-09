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

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BrewingSuperHeated extends SimpleAdaptation<BrewingSuperHeated.Config> {

  private static final int MAX_CHECKS_BEFORE_REMOVE = 20;
  private final Map<Block, Integer> activeStands = new ConcurrentHashMap<>();

  public BrewingSuperHeated() {
    super("brewing-super-heated");
    registerConfiguration(Config.class);
    setIcon(Material.LAVA_BUCKET);
    setInterval(253);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BLAZE_POWDER)
        .key("challenge_brewing_super_heated_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.MAGMA_CREAM)
            .key("challenge_brewing_super_heated_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brewing_super_heated_100", "brewing.super-heated.brews-accelerated", 100, 300);
    registerMilestone("challenge_brewing_super_heated_2500", "brewing.super-heated.brews-accelerated", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getFireBoost(getLevelPercent(level)), 0), 1);
    statLore(v, Form.pc(getLavaBoost(getLevelPercent(level)), 0), 2);
  }

  public double getLavaBoost(double factor) {
    return (getConfig().lavaMultiplier) * (getConfig().multiplierFactor * factor);
  }

  public double getFireBoost(double factor) {
    return (getConfig().fireMultiplier) * (getConfig().multiplierFactor * factor);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryMoveItemEvent e) {
    if (!e.getDestination().getType().equals(InventoryType.BREWING) || e.getDestination().getLocation() == null) {
      return;
    }

    activeStands.put(e.getDestination().getLocation().getBlock(), MAX_CHECKS_BEFORE_REMOVE);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    if (activeStands.containsKey(e.getBlock())) {
      BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);
      if (owner != null) {
        getServer().peekData(owner.getOwner()).addStat("brewing.super-heated.brews-accelerated", 1);
        Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
        fx(loc, FxPriority.TRANSITION)
            .particle(Particle.LAVA, 8, 0, 0.2D, 0, 0.4D, 0.0D)
            .particle(Particle.FLAME, 6, 0, 0.2D, 0, 0.35D, 0.02D)
            .particle(Particle.CAMPFIRE_COSY_SMOKE, 2, 0, 0.4D, 0, 0.2D, 0.02D)
            .chord(Sound.ENTITY_BLAZE_SHOOT, 0.4F, 1.3F, Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.6F);
      }
    }
    if (((BrewingStand) e.getBlock().getState()).getBrewingTime() > 0) {
      activeStands.put(e.getBlock(), MAX_CHECKS_BEFORE_REMOVE);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.getClickedInventory() == null) {
      return;
    }
    if (e.getView().getTopInventory().getType().equals(InventoryType.BREWING)) {
      activeStands.put(e.getView().getTopInventory().getLocation().getBlock(), MAX_CHECKS_BEFORE_REMOVE);
    }
  }


  @Override
  public void onTick() {
    if (activeStands.isEmpty()) {
      return;
    }

    for (Block block : activeStands.keySet()) {
      if (block == null) {
        continue;
      }

      J.runAt(block.getLocation(), () -> tickStand(block));
    }
  }

  private void tickStand(Block block) {
    BlockState state = block.getState();
    if (!(state instanceof BrewingStand brewingStand)) {
      activeStands.remove(block);
      return;
    }

    if (brewingStand.getBrewingTime() <= 0) {
      BrewingStand current = (BrewingStand) block.getState();
      if (current.getBrewingTime() <= 0) {
        Integer remainingChecks = activeStands.get(block);
        if (remainingChecks == null) {
          return;
        }

        if (remainingChecks <= 0) {
          activeStands.remove(block);
        } else {
          activeStands.put(block, remainingChecks - 1);
        }
      }
      return;
    }

    BrewingStandOwner owner = WorldData.of(brewingStand.getWorld()).get(block, BrewingStandOwner.class);
    if (owner == null) {
      activeStands.remove(block);
      return;
    }

    PlayerData playerData = getServer().peekData(owner.getOwner());
    if (playerData == null) {
      activeStands.remove(block);
      return;
    }

    PlayerSkillLine line = playerData.getSkillLineNullable(getSkill().getName());
    PlayerAdaptation adaptation = line != null ? line.getAdaptation(getName()) : null;
    if (adaptation == null || adaptation.getLevel() <= 0) {
      activeStands.remove(block);
      return;
    }

    updateHeat(brewingStand, getLevelPercent(adaptation.getLevel()));
  }

  private void updateHeat(BrewingStand b, double factor) {
    double l = 0;
    double f = 0;

    switch (b.getBlock().getRelative(BlockFace.DOWN).getType()) {
      case LAVA -> l = l + 1;
      case FIRE -> f = f + 1;
    }
    switch (b.getBlock().getRelative(BlockFace.NORTH).getType()) {
      case LAVA -> l = l + 1;
      case FIRE -> f = f + 1;
    }
    switch (b.getBlock().getRelative(BlockFace.SOUTH).getType()) {
      case LAVA -> l = l + 1;
      case FIRE -> f = f + 1;
    }
    switch (b.getBlock().getRelative(BlockFace.EAST).getType()) {
      case LAVA -> l = l + 1;
      case FIRE -> f = f + 1;
    }
    switch (b.getBlock().getRelative(BlockFace.WEST).getType()) {
      case LAVA -> l = l + 1;
      case FIRE -> f = f + 1;
    }

    double pct = (getFireBoost(factor) * f) + (getLavaBoost(factor) * l) + 1;
    int warp = (int) ((getInterval() / 50D) * pct);
    b.setBrewingTime(Math.max(1, b.getBrewingTime() - warp));
    b.update();

    if (M.r(1D / (333D / getInterval()))) {
      Location hl = b.getBlock().getLocation().add(0.5D, 0.9D, 0.5D);
      FxEmitter emitter = fx(hl, FxPriority.AMBIENT);
      emitter.sound(Sound.BLOCK_FIRE_AMBIENT, 1.0F, 1.0F + RNG.r.f(0.3F, 0.6F));
      if (f > 0) {
        emitter.particle(Particle.SMALL_FLAME, (int) f, 0, 0.1D, 0, 0.25D, 0.01D)
            .sound(Sound.ITEM_FIRECHARGE_USE, 0.2F, 1.4F);
      }
      if (l > 0) {
        emitter.particle(Particle.LAVA, (int) l, 0, 0.1D, 0, 0.25D, 0.0D)
            .particle(Particles.SMOKE, (int) l, 0, 0.2D, 0, 0.2D, 0.01D)
            .sound(Sound.BLOCK_LAVA_POP, 0.3F, 0.8F);
      }
    }
  }

  @ConfigDescription("Brewing stands work faster when surrounded by fire or lava.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Multiplier Factor for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double multiplierFactor = 1.33;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fireMultiplier = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lava Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lavaMultiplier = 0.69;

    public Config() {
      baseCost = 3;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
