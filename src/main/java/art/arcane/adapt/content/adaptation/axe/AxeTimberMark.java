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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AxeTimberMark extends SimpleAdaptation<AxeTimberMark.Config> {
  private final Map<UUID, Mark> marks = playerState();

  public AxeTimberMark() {
    super("axe-timber-mark");
    registerConfiguration(Config.class);
    setIcon(Material.OAK_LOG);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_LOG)
        .key("challenge_axe_timber_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_AXE)
        .key("challenge_axe_timber_40")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_timber_200", "axe.timber-mark.marks-felled", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + getMaxBlocks(level) + C.GRAY + " " + Localizer.dLocalize("axe.timber_mark.lore1"));
    v.addLore(C.YELLOW + "* " + Form.duration(getMarkDurationMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("axe.timber_mark.lore2"));
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
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || !isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Block clicked = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (clicked == null || !isLog(new org.bukkit.inventory.ItemStack(clicked.getType()))) {
      return;
    }

    e.setUseInteractedBlock(Event.Result.DENY);
    e.setUseItemInHand(Event.Result.DENY);
    e.setCancelled(true);

    UUID id = p.getUniqueId();
    Mark existing = marks.get(id);
    if (existing != null) {
      existing.aura.cancel();
    }

    long duration = getMarkDurationMillis(level);
    long until = System.currentTimeMillis() + duration;
    Location markCenter = clicked.getLocation().add(0.5D, 0.5D, 0.5D);
    int auraTicks = (int) Math.max(4L, duration / 50L);
    Mark[] self = new Mark[1];
    FxTimeline aura = timeline(markCenter)
        .duration(auraTicks)
        .priority(FxPriority.AMBIENT)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          if ((tick % 10) == 0) {
            fx.dustRing(0.62D, 8, (float) Math.max(0.2D, 1.2D - progress));
          }
        })
        .onComplete(() -> {
          marks.remove(id, self[0]);
          fx(markCenter, FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 3, 0.15D)
              .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.6F);
        });
    Mark mark = new Mark(clicked, until, aura);
    self[0] = mark;
    marks.put(id, mark);
    aura.start();

    fx(markCenter, FxPriority.TRANSITION)
        .dustRing(0.62D, 10, 1.0F)
        .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.8F, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.3F, 2.0F);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0 || !isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    UUID id = p.getUniqueId();
    Mark mark = marks.get(id);
    if (mark == null || mark.until < System.currentTimeMillis() || !mark.block.equals(e.getBlock())) {
      return;
    }

    marks.remove(id);
    mark.aura.cancel();

    Block seed = e.getBlock();
    BlockData logData = seed.getBlockData();
    Material type = seed.getType();
    int maxBlocks = getMaxBlocks(level);
    Set<Block> connected = floodLogs(seed, type, maxBlocks);
    int logsFelled = 0;
    for (Block b : connected) {
      if (b.equals(seed)) {
        continue;
      }

      if (!canBlockBreak(p, b.getLocation())) {
        continue;
      }

      b.breakNaturally(p.getInventory().getItemInMainHand());
      xp(p, getConfig().xpPerExtraLog);
      logsFelled++;
    }

    Set<Block> leaves = floodLeaves(connected, getMaxLeaves(level));
    int leavesCleared = 0;
    for (Block leaf : leaves) {
      if (!canBlockBreak(p, leaf.getLocation())) {
        continue;
      }

      leaf.breakNaturally(p.getInventory().getItemInMainHand());
      xp(p, getConfig().xpPerLeafCleared);
      leavesCleared++;
    }

    addStat(p, "axe.timber-mark.marks-felled", 1);
    if (logsFelled >= 40) {
      grantOnce(p, "challenge_axe_timber_40");
    }

    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    double sumX = 0.0D;
    double sumZ = 0.0D;
    for (Block b : connected) {
      minY = Math.min(minY, b.getY());
      maxY = Math.max(maxY, b.getY());
      sumX += b.getX();
      sumZ += b.getZ();
    }
    int count = connected.size();
    double climb = maxY - minY;
    int steps = Math.min(10, Math.max(3, logsFelled + 1));
    int lastTick = steps - 1;
    Location cascade = new Location(seed.getWorld(), (sumX / count) + 0.5D, minY + 0.5D, (sumZ / count) + 0.5D);
    timeline(cascade)
        .duration(steps)
        .priority(FxPriority.TRANSITION)
        .cullRadius(28)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.BLOCK_CRACK, 4, 0.0D, climb * progress, 0.0D, 0.2D, 0.02D, logData);
          if ((tick & 1) == 0) {
            fx.sound(Sound.BLOCK_WOOD_BREAK, 0.6F, (float) (0.7D + (0.4D * progress)));
          }
          if (tick == lastTick) {
            fx.sound(Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.3F, 0.6F);
          }
        })
        .start();
    fx(cascade, FxPriority.TRANSITION).dustRing(1.0D, 12, 1.0F);

    if (leavesCleared > 0) {
      fx(cascade.clone().add(0.0D, climb * 0.5D, 0.0D), FxPriority.AMBIENT)
          .particle(Particles.VILLAGER_HAPPY, 8, 0.0D, 0.0D, 0.0D, 2.0D, 0.02D)
          .sound(Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.5F, 1.1F);
    }
  }

  private Set<Block> floodLogs(Block start, Material type, int maxBlocks) {
    Set<Block> visited = java.util.concurrent.ConcurrentHashMap.newKeySet();
    ArrayDeque<Block> queue = new ArrayDeque<>();
    queue.add(start);
    visited.add(start);
    while (!queue.isEmpty() && visited.size() < maxBlocks) {
      Block b = queue.poll();
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            Block n = b.getRelative(x, y, z);
            if (n.getType() != type || visited.contains(n)) {
              continue;
            }

            visited.add(n);
            queue.add(n);
            if (visited.size() >= maxBlocks) {
              return visited;
            }
          }
        }
      }
    }
    return visited;
  }

  private Set<Block> floodLeaves(Set<Block> logs, int maxLeaves) {
    Set<Block> visited = java.util.concurrent.ConcurrentHashMap.newKeySet();
    ArrayDeque<Block> queue = new ArrayDeque<>();

    for (Block log : logs) {
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            Block n = log.getRelative(x, y, z);
            if (!isLeafMaterial(n.getType()) || visited.contains(n)) {
              continue;
            }

            visited.add(n);
            queue.add(n);
            if (visited.size() >= maxLeaves) {
              return visited;
            }
          }
        }
      }
    }

    while (!queue.isEmpty() && visited.size() < maxLeaves) {
      Block b = queue.poll();
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            Block n = b.getRelative(x, y, z);
            if (!isLeafMaterial(n.getType()) || visited.contains(n)) {
              continue;
            }

            visited.add(n);
            queue.add(n);
            if (visited.size() >= maxLeaves) {
              return visited;
            }
          }
        }
      }
    }

    return visited;
  }

  private boolean isLeafMaterial(Material type) {
    return type.name().endsWith("_LEAVES");
  }

  private int getMaxBlocks(int level) {
    return Math.max(8, (int) Math.round(getConfig().maxBlocksBase + (getLevelPercent(level) * getConfig().maxBlocksFactor)));
  }

  private long getMarkDurationMillis(int level) {
    return (long) Math.max(1000, Math.round(getConfig().markDurationMillisBase + (getLevelPercent(level) * getConfig().markDurationMillisFactor)));
  }

  private int getMaxLeaves(int level) {
    return Math.max(0, (int) Math.round(getConfig().maxLeavesBase + (getLevelPercent(level) * getConfig().maxLeavesFactor)));
  }

  private static final class Mark {
    private final Block block;
    private final long until;
    private final FxTimeline aura;

    private Mark(Block block, long until, FxTimeline aura) {
      this.block = block;
      this.until = until;
      this.aura = aura;
    }
  }


  @ConfigDescription("Mark a trunk, then break the marked log to fell connected wood.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlocksBase = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlocksFactor = 56;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mark Duration Millis Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double markDurationMillisBase = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mark Duration Millis Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double markDurationMillisFactor = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Extra Log for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerExtraLog = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Leaves Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxLeavesBase = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Leaves Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxLeavesFactor = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Leaf Cleared for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLeafCleared = 0.4;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
