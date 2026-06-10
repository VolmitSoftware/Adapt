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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectChalkLine extends SimpleAdaptation<ArchitectChalkLine.Config> {
  private final Map<UUID, ChalkAnchor> anchors;

  public ArchitectChalkLine() {
    super("architect-chalk-line");
    registerConfiguration(ArchitectChalkLine.Config.class);
    setDescription(Localizer.dLocalize("architect.chalk_line.description"));
    setDisplayName(Localizer.dLocalize("architect.chalk_line.name"));
    setIcon(Material.STRING);
    setInterval(500);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    anchors = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STRING)
        .key("challenge_architect_chalk_line_50")
        .title(Localizer.dLocalize("advancement.challenge_architect_chalk_line_50.title"))
        .description(Localizer.dLocalize("advancement.challenge_architect_chalk_line_50.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.STRING)
            .key("challenge_architect_chalk_line_500")
            .title(Localizer.dLocalize("advancement.challenge_architect_chalk_line_500.title"))
            .description(Localizer.dLocalize("advancement.challenge_architect_chalk_line_500.description"))
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
      SoundPlayer sp = SoundPlayer.of(p);
      sp.play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.5f, 1.2f);
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
    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
    getPlayer(p).getData().addStat("architect.chalk-line.lines-drawn", 1);
    xp(p, getConfig().xpPerLine);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    anchors.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    if (anchors.isEmpty()) {
      return;
    }

    long now = M.ms();
    double rangeSquared = getConfig().renderRangeBlocks * getConfig().renderRangeBlocks;
    for (Map.Entry<UUID, ChalkAnchor> entry : anchors.entrySet()) {
      ChalkAnchor anchor = entry.getValue();
      if (now >= anchor.expiresAt()) {
        anchors.remove(entry.getKey(), anchor);
        continue;
      }

      Player p = Bukkit.getPlayer(entry.getKey());
      if (p == null || !p.isOnline() || !p.getWorld().equals(anchor.anchor().getWorld())) {
        continue;
      }

      if (p.getLocation().distanceSquared(anchor.anchor()) > rangeSquared) {
        continue;
      }

      withPlayerThread(p, () -> renderLine(anchor));
    }
  }

  private void renderLine(ChalkAnchor anchor) {
    if (!areParticlesEnabled()) {
      return;
    }

    Vector direction = anchor.direction().getDirection();
    Location end = anchor.anchor().clone().add(direction.multiply(getConfig().lineLengthBlocks));
    vfxParticleLine(anchor.anchor(), end, getConfig().particlesPerLine, Particle.END_ROD);
  }

  private long getDurationMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().minDurationSeconds, getConfig().maxDurationSeconds, factor) * 1000D);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  private record ChalkAnchor(Location anchor, BlockFace direction, long expiresAt) {
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-right-click a block to snap a particle guide line along your facing axis.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Chalk Line adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }
}
