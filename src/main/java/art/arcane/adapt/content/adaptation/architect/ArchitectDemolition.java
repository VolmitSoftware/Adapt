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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ArchitectDemolition extends SimpleAdaptation<ArchitectDemolition.Config> {
  private final Map<Block, DemolitionMark> placed;
  private final Map<UUID, ConcurrentLinkedDeque<Block>> order;

  public ArchitectDemolition() {
    super("architect-demolition");
    registerConfiguration(ArchitectDemolition.Config.class);
    setDescription(Localizer.dLocalize("architect.demolition.description"));
    setDisplayName(Localizer.dLocalize("architect.demolition.name"));
    setIcon(Material.TNT);
    setInterval(10880);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    placed = new ConcurrentHashMap<>();
    order = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TNT)
        .key("challenge_architect_demolition_500")
        .title(Localizer.dLocalize("advancement.challenge_architect_demolition_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_architect_demolition_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TNT)
            .key("challenge_architect_demolition_5k")
            .title(Localizer.dLocalize("advancement.challenge_architect_demolition_5k.title"))
            .description(Localizer.dLocalize("advancement.challenge_architect_demolition_5k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_demolition_500", "architect.demolition.blocks-demolished", 500, 300);
    registerMilestone("challenge_architect_demolition_5k", "architect.demolition.blocks-demolished", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.demolition.lore1"));
    v.addLore(C.GREEN + "" + (getWindowMillis(getLevelPercent(level)) / 1000) + C.GRAY + " " + Localizer.dLocalize("architect.demolition.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (getActiveLevel(p) <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    Block block = e.getBlock();
    ConcurrentLinkedDeque<Block> deque = order.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    deque.addLast(block);
    placed.put(block, new DemolitionMark(id, M.ms()));
    int cap = getConfig().maxTrackedPerPlayer;
    while (deque.size() > cap) {
      Block oldest = deque.pollFirst();
      if (oldest != null) {
        placed.remove(oldest);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    DemolitionMark mark = placed.get(e.getBlock());
    if (mark == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!mark.owner().equals(p.getUniqueId())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(context.level()))) {
      placed.remove(e.getBlock(), mark);
      return;
    }

    e.setInstaBreak(true);
    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(e.getBlock().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 0.7f);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    DemolitionMark mark = placed.remove(e.getBlock());
    if (mark == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!mark.owner().equals(p.getUniqueId())) {
      return;
    }

    int level = getActiveBlockBreakLevel(p, e.getBlock().getLocation());
    if (level <= 0) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(level))) {
      return;
    }

    Material type = e.getBlock().getType();
    if (type.isAir() || !type.isItem()) {
      return;
    }

    e.setDropItems(false);
    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(type));
    if (areParticlesEnabled()) {
      vfxCuboidOutline(e.getBlock(), Particle.SCRAPE);
    }

    getPlayer(p).getData().addStat("architect.demolition.blocks-demolished", 1);
    xp(p, getConfig().xpPerDemolish);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    ConcurrentLinkedDeque<Block> deque = order.remove(e.getPlayer().getUniqueId());
    if (deque == null) {
      return;
    }

    for (Block block : deque) {
      placed.remove(block);
    }
  }

  private long getWindowMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().minWindowSeconds, getConfig().maxWindowSeconds, factor) * 1000D);
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

  private record DemolitionMark(UUID owner, long at) {
  }

  @NoArgsConstructor
  @ConfigDescription("Blocks you recently placed break near-instantly and always drop their item.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Demolition adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at level 0 progression.", impact = "Higher values let low-level players instabreak older placements.")
    double minWindowSeconds = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at maximum level progression.", impact = "Higher values let max-level players instabreak older placements.")
    double maxWindowSeconds = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of recent placements tracked per player.", impact = "Higher values remember more placements at slightly more memory cost.")
    int maxTrackedPerPlayer = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per demolished block.", impact = "Higher values speed up adaptation progression from demolition.")
    double xpPerDemolish = 1;
  }
}
