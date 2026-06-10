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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcavationBurrow extends SimpleAdaptation<ExcavationBurrow.Config> {
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public ExcavationBurrow() {
    super("excavation-burrow");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.burrow.description"));
    setDisplayName(Localizer.dLocalize("excavation.burrow.name"));
    setIcon(Material.COARSE_DIRT);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4130);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COARSE_DIRT)
        .key("challenge_excavation_burrow_100")
        .title(Localizer.dLocalize("advancement.challenge_excavation_burrow_100.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_burrow_100.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_burrow_100", "excavation.burrow.burrows-dug", 100, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + getMaxDepth(level) + C.GRAY + " " + Localizer.dLocalize("excavation.burrow.lore1"));
    v.addLore(C.GREEN + "+ " + getConfig().durabilityCostPerBlock + C.GRAY + " " + Localizer.dLocalize("excavation.burrow.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.burrow.lore3"));
    v.addLore(C.RED + "- " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("excavation.burrow.lore4"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
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

    long now = System.currentTimeMillis();
    long nextReady = cooldowns.getOrDefault(p.getUniqueId(), 0L);
    if (now < nextReady) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, clicked.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();

    List<Block> plan = planDig(p, clicked, getMaxDepth(level));
    if (plan.isEmpty()) {
      return;
    }

    if (!applyDurability(p, hand, plan.size() * getConfig().durabilityCostPerBlock)) {
      SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.65f);
      return;
    }

    cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    e.setCancelled(true);
    scheduleDig(plan, hand.clone());
    getPlayer(p).getData().addStat("excavation.burrow.burrows-dug", 1);
    getPlayer(p).getData().addStat("excavation.burrow.blocks-burrowed", plan.size());
    xp(p, plan.size() * getConfig().xpPerBlock);
  }

  private List<Block> planDig(Player p, Block start, int maxDepth) {
    World world = start.getWorld();
    int stopY = world.getMinHeight() + getConfig().safeFloorMargin;
    List<Block> plan = new ArrayList<>(maxDepth);
    Block current = start;
    for (int i = 0; i < maxDepth; i++) {
      if (current.getY() <= stopY) {
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

  private void scheduleDig(List<Block> plan, ItemStack tool) {
    int interval = Math.max(1, getConfig().ticksPerBlock);
    for (int i = 0; i < plan.size(); i++) {
      Block block = plan.get(i);
      int delay = i * interval;
      if (delay <= 0) {
        digBlock(block, tool);
        continue;
      }

      J.runAt(block.getLocation(), () -> digBlock(block, tool), delay);
    }
  }

  private void digBlock(Block block, ItemStack tool) {
    if (!isShovelable(block.getType())) {
      return;
    }

    if (block.getRelative(BlockFace.DOWN).getType() == Material.LAVA) {
      return;
    }

    block.breakNaturally(tool);
    if (areParticlesEnabled()) {
      block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
    }

    SoundPlayer.of(block.getWorld()).play(block.getLocation(), Sound.ITEM_SHOVEL_FLATTEN, 0.45f, 1.3f);
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

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-right-click soft ground with a shovel to rapidly dig straight down, stopping before hazards.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.78;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }
}
