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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HerbalismSeedSower extends SimpleAdaptation<HerbalismSeedSower.Config> {
  public HerbalismSeedSower() {
    super("herbalism-seed-sower");
    registerConfiguration(Config.class);
    setIcon(Material.WHEAT_SEEDS);
    setInterval(6920);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHEAT_SEEDS)
        .key("challenge_herbalism_seed_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.FARMLAND)
            .key("challenge_herbalism_seed_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_seed_1k", "herbalism.seed-sower.seeds-planted", 1000, 300);
    registerMilestone("challenge_herbalism_seed_25k", "herbalism.seed-sower.seeds-planted", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    double factor = getLevelPercent(level);
    statLore(v, getRadius(level), 1);
    statLore(v, getMaxCrops(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(factor) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) || e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking() || !hasActiveAdaptation(p)) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isItem(hand)) {
      return;
    }

    Material seedType = hand.getType();
    Material cropType = getCropType(seedType);
    if (cropType == null || p.hasCooldown(seedType)) {
      return;
    }

    Block origin = e.getClickedBlock();
    if (origin == null) {
      origin = p.getTargetBlockExact(5);
    }

    if (origin == null) {
      return;
    }

    int planted = plantNearby(p, origin, hand, seedType, cropType, getRadius(getLevel(p)), getMaxCrops(getLevel(p)));
    if (planted <= 0) {
      return;
    }

    e.setCancelled(true);
    p.setCooldown(seedType, getCooldownTicks(getLevelPercent(p)));
    addStat(p, "harvest.planted", planted);
    addStat(p, "herbalism.seed-sower.seeds-planted", planted);
    xp(p, planted * getConfig().xpPerCrop);

    double footprint = Math.max(1.0D, getRadius(getLevel(p)));
    FxEmitter emitter = fx(origin.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .dustRing(Color.LIME, footprint, Math.min(28, (int) Math.round(footprint * 8)), 1.0F)
        .sound(Sound.ITEM_CROP_PLANT, 0.6F, 1.25F);
    if (planted > 4) {
      emitter.sound(Sound.BLOCK_ROOTED_DIRT_PLACE, 0.5F, 1.35F);
    }
  }

  private int plantNearby(Player p, Block origin, ItemStack seeds, Material seedType, Material cropType, int radius, int maxCrops) {
    int planted = 0;
    int emitted = 0;
    int available = p.getGameMode() == GameMode.CREATIVE ? Integer.MAX_VALUE : seeds.getAmount();
    int y = origin.getY();

    for (int x = -radius; x <= radius && planted < maxCrops; x++) {
      for (int z = -radius; z <= radius && planted < maxCrops; z++) {
        if (available <= 0) {
          break;
        }

        Block base = origin.getWorld().getBlockAt(origin.getX() + x, y, origin.getZ() + z);
        if (!isValidBase(seedType, base.getType())) {
          continue;
        }

        Block crop = base.getRelative(0, 1, 0);
        if (!crop.isEmpty() || !canBlockPlace(p, crop.getLocation())) {
          continue;
        }

        crop.setType(cropType);
        planted++;
        available--;

        if (emitted < 12) {
          fx(crop.getLocation().add(0.5, 0.2, 0.5), FxPriority.AMBIENT)
              .particle(Particle.COMPOSTER, 1, 0, 0.05D, 0, 0.1D, 0.01D)
              .particle(Particle.HAPPY_VILLAGER, 1, 0, 0.05D, 0, 0.1D, 0.01D);
          emitted++;
        }
      }
    }

    if (p.getGameMode() != GameMode.CREATIVE && planted > 0) {
      seeds.setAmount(Math.max(0, seeds.getAmount() - planted));
      p.getInventory().setItemInMainHand(seeds.getAmount() <= 0 ? new ItemStack(Material.AIR) : seeds);
    }

    return planted;
  }

  private boolean isValidBase(Material seedType, Material base) {
    if (seedType == Material.NETHER_WART) {
      return base == Material.SOUL_SAND;
    }

    return base == Material.FARMLAND;
  }

  private Material getCropType(Material seedType) {
    return switch (seedType) {
      case WHEAT_SEEDS -> Material.WHEAT;
      case CARROT -> Material.CARROTS;
      case POTATO -> Material.POTATOES;
      case BEETROOT_SEEDS -> Material.BEETROOTS;
      case MELON_SEEDS -> Material.MELON_STEM;
      case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
      case TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP;
      case NETHER_WART -> Material.NETHER_WART;
      default -> null;
    };
  }

  private int getRadius(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseRadius + (getLevelPercent(level) * getConfig().radiusFactor)));
  }

  private int getMaxCrops(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseCropCount + (getLevelPercent(level) * getConfig().cropCountFactor)));
  }

  private int getCooldownTicks(double factor) {
    return Math.max(2, (int) Math.round(getConfig().cooldownTicksBase - (factor * getConfig().cooldownTicksReduction)));
  }


  @ConfigDescription("Sneak-right-click with seeds to plant nearby farmland and soul-sand plots.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Radius for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseRadius = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Crop Count for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCropCount = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crop Count Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropCountFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Crop for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCrop = 1.45;

    public Config() {
      baseCost = 3;
      costFactor = 0.675;
      initialCost = 3;
    }
  }
}
