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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;

public class ExcavationBurrow extends SimpleAdaptation<ExcavationBurrow.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationBurrow() {
    super("excavation-burrow");
    registerConfiguration(Config.class);
    setIcon(Material.COARSE_DIRT);
    setInterval(4130);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COARSE_DIRT)
        .key("challenge_excavation_burrow_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_burrow_100", "excavation.burrow.burrows-dug", 100, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxDepth(level), 1);
    statLore(v, getConfig().durabilityCostPerBlock, 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 4);
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isShovel(hand)) {
      return;
    }
    if (action == Action.RIGHT_CLICK_AIR && J.isFoliaThreading()) {
      return;
    }

    boolean vetoed = e.getClickedBlock() != null
        ? e.useInteractedBlock() == Event.Result.DENY
        : e.useItemInHand() == Event.Result.DENY;

    Block clicked = e.getClickedBlock();
    if (action == Action.RIGHT_CLICK_AIR) {
      clicked = p.getTargetBlockExact(5);
      if (clicked == null) {
        clicked = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
      }
    }

    if (clicked == null || !isShovelable(clicked.getType())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, clicked.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (vetoed) {
      if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
        e.setCancelled(true);
      }
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    List<Block> plan = planDig(p, clicked, getMaxDepth(level));
    if (plan.isEmpty()) {
      return;
    }

    if (!canApplyDurability(hand, plan.size() * getConfig().durabilityCostPerBlock)) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 4, 0, 1.0D, 0, 0.15D, 0.01D)
          .chord(Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.65f, Sound.ITEM_SHIELD_BLOCK, 0.3f, 1.4f);
      return;
    }

    int planSize = plan.size();
    BlockData clickedData = clicked.getBlockData();
    BlockData floorData = plan.get(planSize - 1).getBlockData();
    if (!digBlock(p, plan.get(0), 1.1F)) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    e.setCancelled(true);

    timeline(p.getLocation())
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.BLOCK_CRACK, 1.4D - (1.1D * progress), 10, 0.1D, clickedData);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 0.7f, Sound.ITEM_SHOVEL_FLATTEN, 0.5f, 0.9f);
          }
        })
        .start();

    scheduleDig(p, plan, floorData);
    addStat(p, "excavation.burrow.burrows-dug", 1);
  }

  private List<Block> planDig(Player p, Block start, int maxDepth) {
    World world = start.getWorld();
    int stopY = world.getMinHeight() + getConfig().safeFloorMargin;
    List<Block> plan = new ArrayList<>(maxDepth);
    Block current = start;
    for (int i = 0; i < maxDepth; i++) {
      if (current.getY() <= stopY
          || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(current.getLocation()))) {
        break;
      }

      if (!isShovelable(current.getType())) {
        break;
      }

      if (!canBlockBreak(p, current.getLocation())) {
        break;
      }

      Block below = current.getRelative(BlockFace.DOWN);
      Material belowType = below.getType();
      if (belowType == Material.LAVA) {
        break;
      }

      if (belowType.isAir() && below.getRelative(BlockFace.DOWN).getType().isAir()) {
        break;
      }

      plan.add(current);
      current = below;
    }

    return plan;
  }

  private void scheduleDig(Player player, List<Block> plan, BlockData floorData) {
    int interval = Math.max(1, getConfig().ticksPerBlock);
    int size = plan.size();
    Location floor = plan.get(size - 1).getLocation().add(0.5, 0.0, 0.5);
    for (int i = 1; i < size; i++) {
      Block block = plan.get(i);
      float pitch = (float) (1.1D + (0.4D * ((double) i / size)));
      int delay = i * interval;
      J.runEntity(player, () -> digBlock(player, block, pitch), delay);
    }

    J.runAt(floor, () -> bottomOut(floor, floorData), (size * interval) + interval);
  }

  private void bottomOut(Location floor, BlockData floorData) {
    fx(floor, FxPriority.GAMEPLAY)
        .dome(Particle.CLOUD, 0.6D, 12)
        .particle(Particles.BLOCK_CRACK, 6, 0, 0.2D, 0, 0.15D, 0.15D, floorData)
        .chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 0.6f, Sound.ITEM_SHOVEL_FLATTEN, 0.4f, 0.5f);
  }

  private boolean digBlock(Player player, Block block, float pitch) {
    Location location = block.getLocation();
    if (!player.isOnline()
        || player.getWorld() != block.getWorld()
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(location)))) {
      return false;
    }

    if (!isShovelable(block.getType())
        || block.getRelative(BlockFace.DOWN).getType() == Material.LAVA
        || !canBlockBreak(player, location)) {
      return false;
    }

    ItemStack hand = player.getInventory().getItemInMainHand();
    if (!isShovel(hand)
        || !canApplyDurability(hand, getConfig().durabilityCostPerBlock)
        || !ProtectionEventProbe.attemptBlockBreakProbe(player, block)) {
      return false;
    }

    BlockData data = block.getBlockData();
    if (!block.breakNaturally(hand)) {
      return false;
    }

    applyDurability(player, hand, getConfig().durabilityCostPerBlock);
    addStat(player, "excavation.burrow.blocks-burrowed", 1);
    xp(player, getConfig().xpPerBlock);
    fx(location.add(0.5, 0.5, 0.5), FxPriority.TRAIL)
        .particle(Particles.BLOCK_CRACK, 4, 0, 0, 0, 0.15D, 0.02D, data)
        .particle(Particle.CLOUD, 1, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.ITEM_SHOVEL_FLATTEN, 0.45f, pitch);
    return true;
  }

  private boolean canApplyDurability(ItemStack hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    return damageable.getDamage() + cost < hand.getType().getMaxDurability();
  }

  private boolean applyDurability(Player p, ItemStack hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    int maxDurability = hand.getType().getMaxDurability();
    int currentDamage = damageable.getDamage();
    if (currentDamage + cost >= maxDurability) {
      return false;
    }

    damageable.setDamage(currentDamage + cost);
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    return true;
  }

  private boolean isShovelable(Material type) {
    return switch (type) {
      case CLAY, DIRT, COARSE_DIRT, ROOTED_DIRT, FARMLAND, GRASS_BLOCK,
           DIRT_PATH, GRAVEL, MYCELIUM, PODZOL, SAND, RED_SAND, SOUL_SAND,
           SOUL_SOIL, SNOW, SNOW_BLOCK, MUD, MUDDY_MANGROVE_ROOTS -> true;
      default -> false;
    };
  }

  private int getMaxDepth(int level) {
    return Math.max(2, (int) Math.round(getConfig().depthBase + (getLevelPercent(level) * getConfig().depthFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(2000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Sneak-right-click soft ground with a shovel to rapidly dig straight down, stopping before hazards.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthFactor = 13;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ticks Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int ticksPerBlock = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Safe Floor Margin for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int safeFloorMargin = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBlock = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per burrow activation.", impact = "Higher values drain more food per dig; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 14000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 7000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBlock = 2;

    public Config() {
      baseCost = 5;
      costFactor = 0.78;
      initialCost = 6;
    }
  }
}
