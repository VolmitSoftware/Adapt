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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectScaffolder extends SimpleAdaptation<ArchitectScaffolder.Config> {
  private final Map<Block, ScaffoldMark> scaffolds = new ConcurrentHashMap<>();
  private final Map<UUID, Set<Block>> byPlayer = playerState();
  private final Cooldowns denyCd = cooldowns();
  private volatile FilterCache filterCache;

  public ArchitectScaffolder() {
    super("architect-scaffolder");
    registerConfiguration(ArchitectScaffolder.Config.class);
    setIcon(Material.SCAFFOLDING);
    setInterval(9220);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SCAFFOLDING)
        .key("challenge_architect_scaffolder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCAFFOLDING)
            .key("challenge_architect_scaffolder_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_scaffolder_500", "architect.scaffolder.blocks-scaffolded", 500, 300);
    registerMilestone("challenge_architect_scaffolder_5k", "architect.scaffolder.blocks-scaffolded", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.SCAFFOLDER_LORE1));
    statLore(v, C.GREEN, "", getDurationSeconds(getLevelPercent(level)), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    Block block = e.getBlock();
    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, block.getLocation(), Player::isSneaking);
    if (context == null) {
      return;
    }

    Material type = block.getType();
    if (type.isAir() || !type.isBlock()) {
      return;
    }

    if (!isScaffoldAllowed(type)) {
      playScaffoldDenied(p);
      return;
    }

    UUID id = p.getUniqueId();
    Set<Block> mine = byPlayer.computeIfAbsent(id, unused -> ConcurrentHashMap.newKeySet());
    if (mine.size() >= getConfig().maxScaffoldsPerPlayer) {
      return;
    }

    if (!payHunger(p)) {
      playScaffoldDenied(p);
      return;
    }

    mine.add(block);
    scaffolds.put(block, new ScaffoldMark(id, type));
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .burst(Particle.CLOUD, 8, 0.25D)
        .burst(Particles.CRIT_MAGIC, 2, 0.15D)
        .sound(Sound.BLOCK_DEEPSLATE_PLACE, 0.6f, 1.6f);

    addStat(p, "architect.scaffolder.blocks-scaffolded", 1);
    int delayTicks = getDurationSeconds(getLevelPercent(context.level())) * 20;
    if (delayTicks > 30) {
      J.runAt(block.getLocation(), () -> warnScaffold(block), delayTicks - 20);
    }
    J.runAt(block.getLocation(), () -> removeScaffold(block), delayTicks);
  }

  private void warnScaffold(Block block) {
    if (!scaffolds.containsKey(block)) {
      return;
    }
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
        .burst(Particle.ASH, 3, 0.2D)
        .sound(Sound.BLOCK_SAND_STEP, 0.3f, 1.4f);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (scaffolds.isEmpty()) {
      return;
    }

    ScaffoldMark mark = scaffolds.remove(e.getBlock());
    if (mark == null) {
      return;
    }

    Set<Block> mine = byPlayer.get(mark.owner());
    if (mine != null) {
      mine.remove(e.getBlock());
    }
  }

  private void removeScaffold(Block block) {
    ScaffoldMark mark = scaffolds.remove(block);
    if (mark == null) {
      return;
    }

    Set<Block> mine = byPlayer.get(mark.owner());
    if (mine != null) {
      mine.remove(block);
    }

    if (block.getType() != mark.material()) {
      return;
    }

    block.setType(Material.AIR);
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .burst(Particle.REVERSE_PORTAL, 6, 0.2D)
        .sound(Sound.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.4f);

    ItemStack refund = new ItemStack(mark.material());
    Player owner = Bukkit.getPlayer(mark.owner());
    if (owner != null && owner.isOnline()) {
      J.runEntity(owner, () -> {
        safeGiveItem(owner, refund);
        fx(owner.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
            .column(Particles.END_ROD, 4, 1.0D)
            .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.6f);
      });
    } else {
      block.getWorld().dropItemNaturally(block.getLocation(), refund);
    }
  }

  private int getDurationSeconds(double factor) {
    return (int) Math.max(1, M.lerp(getConfig().minDurationSeconds, getConfig().maxDurationSeconds, factor));
  }

  private boolean isScaffoldAllowed(Material type) {
    Config config = getConfig();
    FilterCache cache = filterCache;
    if (cache == null || cache.source() != config.blockFilterMaterials
        || !Objects.equals(cache.mode(), config.blockFilterMode)) {
      cache = new FilterCache(config.blockFilterMaterials, config.blockFilterMode,
          parseFilterMaterials(config.blockFilterMaterials));
      filterCache = cache;
    }

    return isScaffoldAllowed(cache.mode(), cache.materials(), type);
  }

  private boolean payHunger(Player p) {
    double cost = Math.max(0, getConfig().hungerExhaustionPerScaffold);
    if (cost <= 0) {
      return true;
    }

    if (p.getFoodLevel() <= 0 && p.getSaturation() <= 0) {
      return false;
    }

    p.setExhaustion((float) (p.getExhaustion() + cost));
    return true;
  }

  private void playScaffoldDenied(Player p) {
    UUID id = p.getUniqueId();
    if (!denyCd.isReady(id, 1500)) {
      return;
    }

    denyCd.mark(id);
    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.15D)
        .sound(Sound.BLOCK_DISPENSER_FAIL, 0.4f, 0.8f);
  }

  static Set<Material> parseFilterMaterials(Collection<String> names) {
    Set<Material> materials = EnumSet.noneOf(Material.class);
    if (names == null) {
      return materials;
    }

    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }

      Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
      if (material != null) {
        materials.add(material);
      }
    }

    return materials;
  }

  static boolean isScaffoldAllowed(String mode, Set<Material> materials, Material type) {
    if (mode == null || type == null) {
      return true;
    }

    return switch (mode.trim().toUpperCase(Locale.ROOT)) {
      case "BLACKLIST" -> !materials.contains(type);
      case "WHITELIST" -> materials.contains(type);
      default -> true;
    };
  }

  private record ScaffoldMark(UUID owner, Material material) {
  }

  private record FilterCache(List<String> source, String mode, Set<Material> materials) {
  }

  @ConfigDescription("Sneak-place blocks as temporary scaffolds that dissolve and refund themselves, with an optional material filter and hunger cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaffold lifetime in seconds at level 0 progression.", impact = "Higher values keep low-level scaffolds in the world longer before dissolving.")
    int minDurationSeconds = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaffold lifetime in seconds at maximum level progression.", impact = "Higher values keep max-level scaffolds in the world longer before dissolving.")
    int maxDurationSeconds = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of active scaffolds tracked per player.", impact = "Higher values let players keep more temporary scaffolds at once.")
    int maxScaffoldsPerPlayer = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Block filter mode: OFF, BLACKLIST, or WHITELIST.", impact = "BLACKLIST denies the listed materials, WHITELIST allows only the listed materials, OFF applies no filter.")
    String blockFilterMode = "OFF";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Material names used by the block filter, for example TNT or SAND.", impact = "Entries are denied under BLACKLIST and are the only allowed scaffolds under WHITELIST.")
    List<String> blockFilterMaterials = new ArrayList<>();
    @art.arcane.adapt.util.config.ConfigDoc(value = "Exhaustion added per scaffolded block, where 4.0 drains half a hunger point. Zero disables the cost.", impact = "Higher values make scaffolding drain hunger faster; zero makes it free.")
    double hungerExhaustionPerScaffold = 0;

    public Config() {
      costFactor = 0.5;
    }
  }
}
