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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class HerbalismCompostCascade extends SimpleAdaptation<HerbalismCompostCascade.Config> {
  public HerbalismCompostCascade() {
    super("herbalism-compost-cascade");
    registerConfiguration(Config.class);
    setIcon(Material.COMPOSTER);
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPOSTER)
        .key("challenge_herbalism_compost_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BONE_MEAL)
            .key("challenge_herbalism_compost_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_compost_1k", "herbalism.compost-cascade.items-composted", 1000, 300);
    registerMilestone("challenge_herbalism_compost_25k", "herbalism.compost-cascade.items-composted", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getMaxItems(level), 2);
    statLore(v, Form.pc(getFillChance(level), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) || e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p) || !p.isSneaking() || p.hasCooldown(Material.COMPOSTER)) {
      return;
    }

    Block composter = e.getClickedBlock();
    if (composter == null) {
      composter = p.getTargetBlockExact(5);
    }

    if (composter == null || composter.getType() != Material.COMPOSTER) {
      return;
    }

    if (!canInteract(p, composter.getLocation()) || !canBlockPlace(p, composter.getLocation())) {
      return;
    }

    if (!(composter.getBlockData() instanceof Levelled levelled)) {
      return;
    }

    int oldLevel = levelled.getLevel();
    if (oldLevel >= 8) {
      return;
    }

    int level = getActiveLevel(p);
    double fillChance = getFillChance(level);
    int maxItems = getMaxItems(level);
    double radius = getRadius(level);
    Location center = composter.getLocation().clone().add(0.5, 0.5, 0.5);
    World world = center.getWorld();
    if (world == null) {
      return;
    }

    CompostState state = new CompostState(oldLevel);

    processDroppedItems(p, world, center, radius, state, maxItems, fillChance);
    processCropAndLeafBlocks(p, world, center, radius, level, state, maxItems, fillChance);
    processInventoryItems(p, state, maxItems, fillChance);

    if (state.consumed <= 0) {
      return;
    }

    Levelled updated = (Levelled) composter.getBlockData();
    updated.setLevel(Math.min(8, Math.max(oldLevel, state.compostLevel)));
    composter.setBlockData(updated);

    p.setCooldown(Material.COMPOSTER, getCooldownTicks(level));
    e.setCancelled(true);

    addStat(p, "harvest.composted", state.consumed);
    addStat(p, "herbalism.compost-cascade.items-composted", state.consumed);
    xp(p, center, (state.consumed * getConfig().xpPerItemConsumed) + (state.levelGains * getConfig().xpPerLevelGain));

    double visualRadius = Math.min(8.0D, radius);
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((visualRadius - 0.4D) * (1.0D - progress));
          fx.dome(Particle.SPORE_BLOSSOM_AIR, r, 8);
          fx.ring(Particle.COMPOSTER, r * 0.85D, 8, 0.2D);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_COMPOSTER_FILL, 0.8F, 1.25F);
          } else if (tick == 1) {
            fx.sound(Sound.BLOCK_COMPOSTER_FILL, 0.5F, 0.9F);
          } else if ((tick & 1) == 0) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3F, (float) (1.1D + (progress * 0.6D)));
          }
        })
        .start();

    if (updated.getLevel() >= 8) {
      fx(center, FxPriority.TRANSITION)
          .dustRing(Color.fromRGB(120, 230, 120), 1.2D, 16, 1.1F)
          .column(Particles.VILLAGER_HAPPY, 10, 1.4D)
          .chord(Sound.BLOCK_COMPOSTER_READY, 1.0F, 1.12F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
    }

    dropRewards(world, center, level, oldLevel, updated.getLevel(), state.consumed);
  }

  private void processDroppedItems(Player p, World world, Location center, double radius, CompostState state, int maxItems, double fillChance) {
    if (isComposterDone(state, maxItems)) {
      return;
    }

    for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof Item item) || isComposterDone(state, maxItems)) {
        continue;
      }

      if (!canSnatchItem(p, item)) {
        continue;
      }

      ItemStack stack = item.getItemStack();
      if (!isItem(stack) || !isCompostable(stack.getType())) {
        continue;
      }

      compostStack(stack, state, maxItems, fillChance);
      if (stack.getAmount() <= 0) {
        item.remove();
      } else {
        item.setItemStack(stack);
      }
    }
  }

  private void processCropAndLeafBlocks(Player p, World world, Location center, double radius, int level, CompostState state, int maxItems, double fillChance) {
    if (isComposterDone(state, maxItems)) {
      return;
    }

    int r = Math.max(1, (int) Math.ceil(radius));
    double rs = radius * radius;
    int bursts = getLeafCompostBursts(level);
    double leafFillChance = getLeafFillChance(level, fillChance);
    int puffs = 0;
    for (int x = -r; x <= r; x++) {
      for (int y = -r; y <= r; y++) {
        for (int z = -r; z <= r; z++) {
          if (isComposterDone(state, maxItems)) {
            return;
          }

          if ((x * x) + (y * y) + (z * z) > rs) {
            continue;
          }

          Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
          if (isMatureCrop(b)) {
            if (!canBlockBreak(p, b.getLocation()) || !canBlockPlace(p, b.getLocation())) {
              continue;
            }

            ItemStack[] drops = b.getDrops().toArray(ItemStack[]::new);
            if (!replantCrop(b)) {
              continue;
            }

            for (ItemStack drop : drops) {
              if (!isItem(drop)) {
                continue;
              }

              if (isCompostable(drop.getType()) && !isComposterDone(state, maxItems)) {
                compostStack(drop, state, maxItems, fillChance);
              }

              if (drop.getAmount() > 0) {
                world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
              }
            }
          } else if (isLeafBlock(b.getType())) {
            if (!canBlockBreak(p, b.getLocation())) {
              continue;
            }

            b.setType(Material.AIR, false);
            ItemStack leafMass = new ItemStack(Material.OAK_LEAVES, bursts);
            compostStack(leafMass, state, maxItems, leafFillChance);

            if (puffs < 8) {
              fx(b.getLocation().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
                  .particle(Particle.SPORE_BLOSSOM_AIR, 2, 0, 0.1D, 0, 0.15D, 0.01D)
                  .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
              puffs++;
            }
          }
        }
      }
    }
  }

  private void processInventoryItems(Player p, CompostState state, int maxItems, double fillChance) {
    if (isComposterDone(state, maxItems)) {
      return;
    }

    ItemStack[] storage = p.getInventory().getStorageContents();
    boolean changed = false;
    for (int i = 0; i < storage.length; i++) {
      if (isComposterDone(state, maxItems)) {
        break;
      }

      ItemStack stack = storage[i];
      if (!isItem(stack) || !isCompostable(stack.getType())) {
        continue;
      }

      compostStack(stack, state, maxItems, fillChance);
      changed = true;
      if (stack.getAmount() <= 0) {
        storage[i] = null;
      }
    }

    if (changed) {
      p.getInventory().setStorageContents(storage);
    }
  }

  private void compostStack(ItemStack stack, CompostState state, int maxItems, double fillChance) {
    while (stack.getAmount() > 0 && !isComposterDone(state, maxItems)) {
      stack.setAmount(stack.getAmount() - 1);
      state.processed++;
      state.consumed++;

      if (ThreadLocalRandom.current().nextDouble() <= fillChance) {
        state.compostLevel++;
        state.levelGains++;
      }
    }
  }

  private void dropRewards(World world, Location center, int level, int oldLevel, int newLevel, int consumed) {
    int boneMeal = getBaseBoneMeal(level) + Math.max(0, consumed / getItemsPerBoneMeal(level));
    if (newLevel >= 8 && oldLevel < 8) {
      boneMeal += getReadyBonusBoneMeal(level);
    }

    if (boneMeal > 0) {
      world.dropItemNaturally(center, new ItemStack(Material.BONE_MEAL, Math.min(64, boneMeal)));
    }

    if (newLevel < 8) {
      return;
    }

    int rolls = getValuableRolls(level);
    int sparkled = 0;
    for (int i = 0; i < rolls; i++) {
      if (ThreadLocalRandom.current().nextDouble() <= getValuableChance(level)) {
        ItemStack reward = rollValuableReward(level);
        world.dropItemNaturally(center, reward);
        if (sparkled < 3) {
          valuableSparkle(center, reward.getType());
          sparkled++;
        }
      }
    }
  }

  private void valuableSparkle(Location center, Material type) {
    FxEmitter emitter = fx(center, FxPriority.TRANSITION)
        .particle(Particle.WAX_ON, 6, 0, 0.3D, 0, 0.4D, 0.02D)
        .sound(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5F, 1.7F);
    if (type == Material.DIAMOND) {
      emitter.column(Particles.END_ROD, 4, 1.2D).sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 2.0F);
    } else if (type == Material.EMERALD) {
      emitter.column(Particles.END_ROD, 2, 0.9D);
    }
  }

  private ItemStack rollValuableReward(int level) {
    double lp = getLevelPercent(level);
    double r = ThreadLocalRandom.current().nextDouble();

    if (r < 0.45) {
      return new ItemStack(Material.MOSS_BLOCK, 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 3))));
    }

    if (r < 0.7) {
      return new ItemStack(Material.GLOW_BERRIES, 2 + ThreadLocalRandom.current().nextInt(2 + Math.max(1, (int) Math.round(lp * 4))));
    }

    if (r < 0.88) {
      return new ItemStack(Material.AMETHYST_SHARD, 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 4))));
    }

    if (r < 0.97) {
      return new ItemStack(Material.EMERALD, 1);
    }

    return new ItemStack(Material.DIAMOND, 1);
  }

  private boolean isMatureCrop(Block b) {
    BlockData data = b.getBlockData();
    if (!(data instanceof Ageable ageable)) {
      return false;
    }

    Material type = b.getType();
    if (type == Material.CHORUS_PLANT || type == Material.SUGAR_CANE || type == Material.BAMBOO) {
      return false;
    }

    return ageable.getAge() >= ageable.getMaximumAge();
  }

  private boolean replantCrop(Block b) {
    BlockData data = b.getBlockData();
    if (!(data instanceof Ageable ageable)) {
      return false;
    }

    ageable.setAge(0);
    b.setBlockData(ageable, true);
    return true;
  }

  private boolean isLeafBlock(Material type) {
    return type.name().endsWith("_LEAVES");
  }

  private boolean isComposterDone(CompostState state, int maxItems) {
    return state.compostLevel >= 8 || state.processed >= maxItems;
  }

  private boolean isCompostable(Material type) {
    String n = type.name().toUpperCase(Locale.ROOT);
    return n.contains("SEEDS")
        || n.contains("SAPLING")
        || n.contains("LEAVES")
        || n.contains("FLOWER")
        || n.contains("MUSHROOM")
        || n.contains("ROOTS")
        || n.contains("VINE")
        || n.contains("KELP")
        || n.contains("DRIPLEAF")
        || n.contains("MOSS")
        || type == Material.WHEAT
        || type == Material.BEETROOT
        || type == Material.CARROT
        || type == Material.POTATO
        || type == Material.POISONOUS_POTATO
        || type == Material.NETHER_WART
        || type == Material.CACTUS
        || type == Material.SUGAR_CANE
        || type == Material.BAMBOO
        || type == Material.SHORT_GRASS
        || type == Material.TALL_GRASS
        || type == Material.SEA_PICKLE;
  }

  private int getMaxItems(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor)));
  }

  private double getRadius(int level) {
    return Math.max(1, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private double getFillChance(int level) {
    return Math.min(getConfig().maxFillChance, getConfig().fillChanceBase + (getLevelPercent(level) * getConfig().fillChanceFactor));
  }

  private double getLeafFillChance(int level, double baseFillChance) {
    return Math.min(1.0, baseFillChance * (getConfig().leafFillChanceMultiplierBase + (getLevelPercent(level) * getConfig().leafFillChanceMultiplierFactor)));
  }

  private int getLeafCompostBursts(int level) {
    return Math.max(1, (int) Math.round(getConfig().leafCompostBurstsBase + (getLevelPercent(level) * getConfig().leafCompostBurstsFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
  }

  private int getBaseBoneMeal(int level) {
    return Math.max(0, (int) Math.round(getConfig().boneMealBase + (getLevelPercent(level) * getConfig().boneMealFactor)));
  }

  private int getReadyBonusBoneMeal(int level) {
    return Math.max(0, (int) Math.round(getConfig().readyBonusBoneMealBase + (getLevelPercent(level) * getConfig().readyBonusBoneMealFactor)));
  }

  private int getItemsPerBoneMeal(int level) {
    return Math.max(1, (int) Math.round(getConfig().itemsPerBoneMealBase - (getLevelPercent(level) * getConfig().itemsPerBoneMealReduction)));
  }

  private double getValuableChance(int level) {
    return Math.min(getConfig().maxValuableChance, getConfig().valuableChanceBase + (getLevelPercent(level) * getConfig().valuableChanceFactor));
  }

  private int getValuableRolls(int level) {
    return Math.max(0, (int) Math.round(getConfig().valuableRollsBase + (getLevelPercent(level) * getConfig().valuableRollsFactor)));
  }


  @ConfigDescription("Sneak-right-click a composter to process nearby drops, crops, leaves, and your own compostables.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 12.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsBase = 80.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsFactor = 240.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fillChanceBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fillChanceFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fill Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFillChance = 0.98;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafCompostBurstsBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafCompostBurstsFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafFillChanceMultiplierBase = 1.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafFillChanceMultiplierFactor = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 36.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 28.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneMealBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneMealFactor = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double readyBonusBoneMealBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double readyBonusBoneMealFactor = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double itemsPerBoneMealBase = 20.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double itemsPerBoneMealReduction = 14.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableChanceBase = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableChanceFactor = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Valuable Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxValuableChance = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableRollsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableRollsFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Consumed for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemConsumed = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level Gain for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLevelGain = 2.8;

    public Config() {
      costFactor = 0.72;
      maxLevel = 6;
      initialCost = 4;
    }
  }

  private static class CompostState {
    private int compostLevel;
    private int processed;
    private int consumed;
    private int levelGains;

    private CompostState(int compostLevel) {
      this.compostLevel = compostLevel;
      this.processed = 0;
      this.consumed = 0;
      this.levelGains = 0;
    }
  }
}
