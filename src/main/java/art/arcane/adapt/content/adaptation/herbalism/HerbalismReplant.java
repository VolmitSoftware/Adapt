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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {

  public HerbalismReplant() {
    super("herbalism-replant");
    registerConfiguration(Config.class);
    setIcon(Material.PUMPKIN_SEEDS);
    setInterval(6090);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHEAT_SEEDS)
        .key("challenge_herbalism_replant_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.COMPOSTER)
            .key("challenge_herbalism_replant_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_replant_500", "herbalism.replant.crops-replanted", 500, 300);
    registerMilestone("challenge_herbalism_replant_25k", "herbalism.replant.crops-replanted", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getRadius(level), 1);
  }


  private int getCooldown(double factor, int level) {
    if (level == 1) {
      return (int) getConfig().cooldownLvl1;
    }

    return (int) ((getConfig().baseCooldown - (getConfig().cooldownFactor * factor)) + getConfig().bonusCooldown);
  }

  private float getRadius(int lvl) {
    return lvl - getConfig().radiusSub;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) || e.getHand() != EquipmentSlot.HAND) {
      return;
    }
    if ((action == Action.RIGHT_CLICK_BLOCK && e.useInteractedBlock() == Event.Result.DENY)
        || (action == Action.RIGHT_CLICK_AIR && e.useItemInHand() == Event.Result.DENY)) {
      return;
    }

    Player p = e.getPlayer();
    if (action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()) {
      return;
    }
    int lvl = getActiveLevel(p);
    if (lvl <= 0) {
      return;
    }

    Block target = e.getClickedBlock();
    if (target == null) {
      target = p.getTargetBlockExact(5);
    }

    if (target == null || !(target.getBlockData() instanceof Ageable targetAge)) {
      return;
    }

    if (targetAge.getAge() != targetAge.getMaximumAge()) {
      return;
    }

    if (!canBlockBreak(p, target.getLocation())) {
      return;
    }

    ItemStack right = p.getInventory().getItemInMainHand();
    ItemStack left = p.getInventory().getItemInOffHand();
    ItemStack harvestTool;
    Material cooldownMaterial;
    boolean offHand;

    if (isTool(left) && isHoe(left) && !p.hasCooldown(left.getType())) {
      harvestTool = left.clone();
      cooldownMaterial = left.getType();
      offHand = true;
    } else if (isTool(right) && isHoe(right) && !p.hasCooldown(right.getType())) {
      harvestTool = right.clone();
      cooldownMaterial = right.getType();
      offHand = false;
    } else {
      return;
    }

    double footprint = Math.max(1.0D, getRadius(lvl));
    if (J.isFoliaThreading()
        && !J.isOwnedByCurrentRegion(target.getLocation(), footprint, footprint)) {
      return;
    }
    if (!hit(p, target, harvestTool)) {
      return;
    }

    if (offHand) {
      damageOffHand(p, 1 + ((lvl - 1) * 7));
    } else {
      damageHand(p, 1 + ((lvl - 1) * 7));
    }
    p.setCooldown(cooldownMaterial, getCooldown(getLevelPercent(p), lvl));

    if (lvl > 1) {
      Cuboid c = new Cuboid(target.getLocation().clone().add(0.5, 0.5, 0.5));
      c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
      c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
      c = c.expand(Cuboid.CuboidDirection.North, Math.round(getRadius(lvl)));
      c = c.expand(Cuboid.CuboidDirection.South, Math.round(getRadius(lvl)));
      c = c.expand(Cuboid.CuboidDirection.East, Math.round(getRadius(lvl)));
      c = c.expand(Cuboid.CuboidDirection.West, Math.round(getRadius(lvl)));

      for (Block i : c) {
        if (!i.equals(target)) {
          J.runAt(i.getLocation(), () -> hit(p, i, harvestTool), M.irand(1, 6));
        }
      }

      fx(target.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
          .dustRing(Color.LIME, footprint, Math.min(28, (int) Math.round(footprint * 8)), 1.0F)
          .chord(Sound.ITEM_SHOVEL_FLATTEN, 1F, 0.66F, Sound.BLOCK_BAMBOO_SAPLING_BREAK, 1F, 0.66F);
    }
  }

  private boolean hit(Player p, Block b, ItemStack harvestTool) {
    if (b == null || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p))) {
      return false;
    }
    if (!(b.getBlockData() instanceof Ageable ageable)
        || ageable.getAge() != ageable.getMaximumAge()
        || getActiveBlockBreakLevel(p, b.getLocation()) <= 0
        || !canBlockBreak(p, b.getLocation())
        || !ProtectionEventProbe.attemptBlockBreakProbe(p, b)) {
      return false;
    }
    if (!(b.getBlockData() instanceof Ageable current)
        || current.getAge() != current.getMaximumAge()) {
      return false;
    }
    Material cropType = b.getType();

    boolean dropToInventory = getActiveSiblingBlockBreakLevel(
        p,
        "herbalism-drop-to-inventory",
        b.getLocation()
    ) > 0;
    List<ItemStack> items = new ArrayList<>(b.getDrops(harvestTool, p));
    boolean seedConsumed = consumeSeed(items, current.getPlacementMaterial());
    if (seedConsumed && (!canBlockPlace(p, b.getLocation())
        || !ProtectionEventProbe.attemptBlockPlaceProbe(p, b))) {
      return false;
    }
    if (b.getType() != cropType
        || !(b.getBlockData() instanceof Ageable commitData)
        || commitData.getAge() != commitData.getMaximumAge()) {
      return false;
    }

    xp(p, b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).harvestPerAgeXP * commitData.getAge());
    if (seedConsumed) {
      xp(p, b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).plantCropSeedsXP);
    }
    if (dropToInventory) {
      boolean caught = false;
      for (ItemStack i : items) {
        if (!isItem(i) || i.getAmount() <= 0) {
          continue;
        }
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(i);
        if (overflow.isEmpty()) {
          caught = true;
        } else {
          overflow.values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        }
      }
      if (caught) {
        fx(p.getEyeLocation().subtract(0, 0.4, 0), FxPriority.TRANSITION)
            .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sound.BLOCK_CALCITE_HIT, 0.3F, 1.2F);
      }
    } else {
      for (ItemStack i : items) {
        if (!isItem(i) || i.getAmount() <= 0) {
          continue;
        }
        p.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), i);
      }
    }

    if (seedConsumed) {
      commitData.setAge(0);
      b.setBlockData(commitData, true);
      addStat(p, "harvest.planted", 1);
      addStat(p, "herbalism.replant.crops-replanted", 1);
    } else {
      b.setType(Material.AIR, true);
    }

    addStat(p, "harvest.blocks", 1);

    fx(b.getLocation().add(0.5, 0.6, 0.5), FxPriority.TRANSITION)
        .dustRing(Color.LIME, 0.4D, 8, 0.8F)
        .particle(Particle.HAPPY_VILLAGER, 3, 0, 0, 0, 0.15D, 0.02D)
        .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D);

    if (M.r(1D / (double) getLevel(p))) {
      fx(b.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
          .sound(Sound.ITEM_CROP_PLANT, 1F, 0.7F);
    }
    return true;
  }

  private boolean consumeSeed(List<ItemStack> drops, Material seedType) {
    if (seedType == null || seedType.isAir()) {
      return false;
    }

    for (ItemStack drop : drops) {
      if (isItem(drop) && drop.getType() == seedType && drop.getAmount() > 0) {
        drop.setAmount(drop.getAmount() - 1);
        return true;
      }
    }

    return false;
  }



  @ConfigDescription("Right-click a crop with a hoe to harvest and replant it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Lvl1 for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownLvl1 = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCooldown = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Factor for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownFactor = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusCooldown = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Sub for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int radiusSub = 1;

    public Config() {
      baseCost = 6;
      costFactor = 0.95;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
