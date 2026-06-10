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

import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectSupplyLine extends SimpleAdaptation<ArchitectSupplyLine.Config> {
  private final Map<UUID, RefillWindow> windows;

  public ArchitectSupplyLine() {
    super("architect-supply-line");
    registerConfiguration(ArchitectSupplyLine.Config.class);
    setDescription(Localizer.dLocalize("architect.supply_line.description"));
    setDisplayName(Localizer.dLocalize("architect.supply_line.name"));
    setIcon(Material.SHULKER_BOX);
    setInterval(13780);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    windows = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHULKER_BOX)
        .key("challenge_architect_supply_line_100")
        .title(Localizer.dLocalize("advancement.challenge_architect_supply_line_100.title"))
        .description(Localizer.dLocalize("advancement.challenge_architect_supply_line_100.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SHULKER_BOX)
            .key("challenge_architect_supply_line_1k")
            .title(Localizer.dLocalize("advancement.challenge_architect_supply_line_1k.title"))
            .description(Localizer.dLocalize("advancement.challenge_architect_supply_line_1k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_supply_line_100", "architect.supply-line.refills", 100, 300);
    registerMilestone("challenge_architect_supply_line_1k", "architect.supply-line.refills", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.supply_line.lore1"));
    v.addLore(C.GREEN + "" + getRefillsPerMinute(getLevelPercent(level)) + C.GRAY + " " + Localizer.dLocalize("architect.supply_line.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    ItemStack hand = e.getItemInHand();
    if (hand.getAmount() > 1) {
      return;
    }

    Material material = hand.getType();
    if (!material.isBlock() || material.isAir()) {
      return;
    }

    Player p = e.getPlayer();
    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    if (!tryConsumeRefill(p.getUniqueId(), getRefillsPerMinute(getLevelPercent(context.level())))) {
      return;
    }

    EquipmentSlot slot = e.getHand();
    J.runEntity(p, () -> refill(p, slot, material), 1);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    windows.remove(e.getPlayer().getUniqueId());
  }

  private boolean tryConsumeRefill(UUID id, int allowed) {
    long now = M.ms();
    RefillWindow current = windows.get(id);
    if (current == null || now - current.start() >= 60000L) {
      windows.put(id, new RefillWindow(now, 1));
      return true;
    }

    if (current.used() >= allowed) {
      return false;
    }

    windows.put(id, new RefillWindow(current.start(), current.used() + 1));
    return true;
  }

  private void refill(Player p, EquipmentSlot slot, Material material) {
    if (!p.isOnline()) {
      return;
    }

    ItemStack current = slot == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
    if (current != null && !current.getType().isAir()) {
      return;
    }

    ItemStack pulled = pullMatching(p, material);
    if (pulled == null) {
      return;
    }

    if (slot == EquipmentSlot.OFF_HAND) {
      p.getInventory().setItemInOffHand(pulled);
    } else {
      p.getInventory().setItemInMainHand(pulled);
    }

    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(p.getLocation(), Sound.BLOCK_COMPOSTER_FILL, 0.6f, 1.3f);
    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
    }

    getPlayer(p).getData().addStat("architect.supply-line.refills", 1);
    xp(p, getConfig().xpPerRefill);
  }

  private ItemStack pullMatching(Player p, Material material) {
    ItemStack[] contents = p.getInventory().getStorageContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack candidate = contents[i];
      if (candidate == null || candidate.getType().isAir()) {
        continue;
      }

      ItemStack pulled = pullFromShulker(candidate, material);
      if (pulled == null) {
        pulled = pullFromBundle(candidate, material);
      }

      if (pulled != null) {
        return pulled;
      }
    }

    return null;
  }

  private ItemStack pullFromShulker(ItemStack container, Material material) {
    if (!Tag.SHULKER_BOXES.isTagged(container.getType())) {
      return null;
    }

    if (!(container.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) {
      return null;
    }

    Inventory inv = box.getInventory();
    for (int slot = 0; slot < inv.getSize(); slot++) {
      ItemStack content = inv.getItem(slot);
      if (content == null || content.getType() != material) {
        continue;
      }

      ItemStack pulled = content.clone();
      inv.setItem(slot, null);
      meta.setBlockState(box);
      container.setItemMeta(meta);
      return pulled;
    }

    return null;
  }

  private ItemStack pullFromBundle(ItemStack container, Material material) {
    if (!(container.getItemMeta() instanceof BundleMeta meta)) {
      return null;
    }

    List<ItemStack> items = meta.getItems();
    if (items.isEmpty()) {
      return null;
    }

    List<ItemStack> remaining = new ArrayList<>(items.size());
    ItemStack pulled = null;
    for (ItemStack item : items) {
      if (pulled == null && item != null && item.getType() == material) {
        pulled = item.clone();
        continue;
      }

      remaining.add(item);
    }

    if (pulled == null) {
      return null;
    }

    meta.setItems(remaining);
    container.setItemMeta(meta);
    return pulled;
  }

  private int getRefillsPerMinute(double factor) {
    return (int) Math.max(1, M.lerp(getConfig().minRefillsPerMinute, getConfig().maxRefillsPerMinute, factor));
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

  private record RefillWindow(long start, int used) {
  }

  @NoArgsConstructor
  @ConfigDescription("Automatically refill your hand from shulker boxes or bundles when a placed stack runs out.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Supply Line adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at level 0 progression.", impact = "Higher values let low-level players refill more often.")
    int minRefillsPerMinute = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at maximum level progression.", impact = "Higher values let max-level players refill more often.")
    int maxRefillsPerMinute = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per successful refill.", impact = "Higher values speed up adaptation progression from refills.")
    double xpPerRefill = 2;
  }
}
