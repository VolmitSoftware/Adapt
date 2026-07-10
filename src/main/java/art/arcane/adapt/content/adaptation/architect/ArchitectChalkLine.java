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
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArchitectChalkLine extends SimpleAdaptation<ArchitectChalkLine.Config> {
  private static final int MAX_ANCHORS_PER_TICK = 64;
  private static final long RENDER_CYCLE_MILLIS = 500L;
  private final Map<UUID, ChalkAnchor> anchors = playerState();
  private List<UUID> renderPlayers = List.of();
  private int renderIndex;
  private long nextRenderCycleAt;

  public ArchitectChalkLine() {
    super("architect-chalk-line");
    registerConfiguration(ArchitectChalkLine.Config.class);
    setIcon(Material.STRING);
    setInterval(RENDER_CYCLE_MILLIS);
    nextRenderCycleAt = M.ms() + RENDER_CYCLE_MILLIS;
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STRING)
        .key("challenge_architect_chalk_line_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.STRING)
            .key("challenge_architect_chalk_line_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_chalk_line_50", "architect.chalk-line.lines-drawn", 50, 300);
    registerMilestone("challenge_architect_chalk_line_500", "architect.chalk-line.lines-drawn", 500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.chalk_line.lore1"));
    v.addLore(C.GREEN + "" + (getDurationMillis(getLevelPercent(level)) / 1000) + C.GRAY + " " + Localizer.dLocalize("architect.chalk_line.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isItem(hand) || !hand.getType().isBlock()) {
      return;
    }

    UUID id = p.getUniqueId();
    ChalkAnchor existing = anchors.remove(id);
    if (existing != null && M.ms() < existing.expiresAt()) {
      e.setUseItemInHand(Event.Result.DENY);
      e.setUseInteractedBlock(Event.Result.DENY);
      fx(existing.anchor(), FxPriority.TRANSITION)
          .burst(Particles.END_ROD, 6, 0.2D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.4f, 1.0f);
      return;
    }

    Block target = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (target == null) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, target.getLocation(), Player::isSneaking);
    if (context == null) {
      return;
    }

    e.setUseItemInHand(Event.Result.DENY);
    e.setUseInteractedBlock(Event.Result.DENY);
    long expiresAt = M.ms() + getDurationMillis(getLevelPercent(context.level()));
    Location anchor = target.getLocation().add(0.5, 1.1, 0.5);
    anchors.put(id, new ChalkAnchor(anchor, p.getFacing(), expiresAt));
    fx(anchor, FxPriority.GAMEPLAY)
        .ring(Particles.END_ROD, 0.6D, 16, 0)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 2.0f);
    addStat(p, "architect.chalk-line.lines-drawn", 1);
    xp(p, getConfig().xpPerLine);
  }

  @Override
  public void onTick() {
    if (anchors.isEmpty()) {
      renderPlayers = List.of();
      renderIndex = 0;
      setInterval(RENDER_CYCLE_MILLIS);
      return;
    }

    long now = M.ms();
    if (renderIndex >= renderPlayers.size()) {
      if (now < nextRenderCycleAt) {
        setInterval(nextRenderCycleAt - now);
        return;
      }
      renderPlayers = new ArrayList<>(anchors.keySet());
      renderIndex = 0;
      nextRenderCycleAt = now + RENDER_CYCLE_MILLIS;
    }

    int endIndex = renderBatchEnd(renderIndex, renderPlayers.size());
    while (renderIndex < endIndex) {
      UUID playerId = renderPlayers.get(renderIndex++);
      ChalkAnchor anchor = anchors.get(playerId);
      if (anchor == null) {
        continue;
      }
      if (now >= anchor.expiresAt()) {
        anchors.remove(playerId, anchor);
        continue;
      }

      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        anchors.remove(playerId, anchor);
        continue;
      }
      withPlayerThread(player, () -> renderLine(player, playerId, anchor));
    }
    setInterval(renderIndex < renderPlayers.size()
        ? MIN_INTERVAL_MILLIS
        : Math.max(MIN_INTERVAL_MILLIS, nextRenderCycleAt - now));
  }

  private void renderLine(Player player, UUID playerId, ChalkAnchor anchor) {
    if (!player.isOnline() || anchors.get(playerId) != anchor || M.ms() >= anchor.expiresAt()) {
      anchors.remove(playerId, anchor);
      return;
    }
    if (player.getWorld() != anchor.anchor().getWorld()
        || player.getLocation().distanceSquared(anchor.anchor()) > renderRangeSquared(getConfig().renderRangeBlocks)) {
      return;
    }

    Vector direction = anchor.direction().getDirection();
    Location end = anchor.anchor().clone().add(direction.multiply(clampLineLength(getConfig().lineLengthBlocks)));
    fx(anchor.anchor(), FxPriority.TRAIL)
        .line(Particles.END_ROD, end.getX(), end.getY(), end.getZ(), clampParticleCount(getConfig().particlesPerLine));
  }

  static int renderBatchEnd(int startIndex, int playerCount) {
    return Math.min(Math.max(0, playerCount), Math.max(0, startIndex) + MAX_ANCHORS_PER_TICK);
  }

  static int clampParticleCount(int configured) {
    return Math.max(2, Math.min(32, configured));
  }

  static double clampLineLength(double configured) {
    return Math.max(1D, Math.min(64D, configured));
  }

  static double renderRangeSquared(double configured) {
    double range = Math.max(8D, Math.min(96D, configured));
    return range * range;
  }

  private long getDurationMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().minDurationSeconds, getConfig().maxDurationSeconds, factor) * 1000D);
  }

  private record ChalkAnchor(Location anchor, BlockFace direction, long expiresAt) {
  }

  @ConfigDescription("Sneak-right-click a block to snap a particle guide line along your facing axis.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Guide line lifetime in seconds at level 0 progression.", impact = "Higher values keep low-level guide lines visible longer.")
    double minDurationSeconds = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Guide line lifetime in seconds at maximum level progression.", impact = "Higher values keep max-level guide lines visible longer.")
    double maxDurationSeconds = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Length of the rendered guide line in blocks.", impact = "Higher values render a longer alignment guide.")
    double lineLengthBlocks = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Number of particles rendered along the guide line each render pass.", impact = "Higher values draw a denser line at slightly more cost.")
    int particlesPerLine = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance in blocks from the anchor at which the line still renders.", impact = "Higher values render the guide line even when the player walks farther away.")
    double renderRangeBlocks = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per chalk line placed.", impact = "Higher values speed up adaptation progression from chalk lines.")
    double xpPerLine = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.4;
      initialCost = 1;
    }
  }
}
