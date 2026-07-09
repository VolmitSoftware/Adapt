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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;

public class ArchitectSteadyHands extends SimpleAdaptation<ArchitectSteadyHands.Config> {
  private final Cooldowns bridgeGrace = cooldowns();
  private final Cooldowns auraCd = cooldowns();

  public ArchitectSteadyHands() {
    super("architect-steady-hands");
    registerConfiguration(ArchitectSteadyHands.Config.class);
    setIcon(Material.LIGHTNING_ROD);
    setInterval(10440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LIGHTNING_ROD)
        .key("challenge_architect_steady_hands_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LIGHTNING_ROD)
            .key("challenge_architect_steady_hands_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_steady_hands_500", "architect.steady-hands.bridge-blocks", 500, 300);
    registerMilestone("challenge_architect_steady_hands_5k", "architect.steady-hands.bridge-blocks", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.steady_hands.lore1"));
    v.addLore(C.GREEN + "" + (int) getShieldedHeight(getLevelPercent(level)) + C.GRAY + " " + Localizer.dLocalize("architect.steady_hands.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (!e.getBlock().getRelative(BlockFace.DOWN).getType().isAir()) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.getBlock().getLocation(), Player::isSneaking);
    if (context == null) {
      return;
    }

    bridgeGrace.mark(p.getUniqueId());
    p.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, getConfig().hasteDurationTicks, getConfig().hasteAmplifier, false, false, true));
    addStat(p, "architect.steady-hands.bridge-blocks", 1);
    if (auraCd.isReady(p.getUniqueId(), 250)) {
      auraCd.mark(p.getUniqueId());
      fx(p.getLocation(), FxPriority.TRANSITION)
          .dustRing(Color.fromRGB(255, 225, 120), 0.5D, 12, 0.8F)
          .sound(Sound.BLOCK_WOOL_STEP, 0.3f, 1.2f);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerVelocityEvent e) {
    Player p = e.getPlayer();
    if (bridgeGrace.isReady(p.getUniqueId(), getConfig().bridgeGraceMillis)) {
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }

    if (bridgeGrace.isReady(p.getUniqueId(), getConfig().bridgeGraceMillis)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double shielded = getShieldedHeight(getLevelPercent(level));
    if (e.getDamage() <= shielded) {
      e.setCancelled(true);
      p.setFallDistance(0);
      fx(p.getLocation(), FxPriority.COMBAT)
          .dustRing(Color.fromRGB(255, 240, 180), 0.6D, 16, 1.0F)
          .sound(Sound.BLOCK_WOOL_BREAK, 0.5f, 0.8f);
      return;
    }

    e.setDamage(Math.max(0, e.getDamage() - shielded));
  }

  private double getShieldedHeight(double factor) {
    return M.lerp(getConfig().minShieldedBlocks, getConfig().maxShieldedBlocks, factor);
  }

  @Override
  public void onTick() {
  }

  @NoArgsConstructor
  @ConfigDescription("Stay rock-steady while bridging: no knockback and reduced fall damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fall damage shielded in blocks at level 0 progression.", impact = "Higher values absorb more fall damage for low-level players while bridging.")
    double minShieldedBlocks = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fall damage shielded in blocks at maximum level progression.", impact = "Higher values absorb more fall damage for max-level players while bridging.")
    double maxShieldedBlocks = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long in milliseconds after a bridge placement the protections stay active.", impact = "Higher values keep knockback and fall protection up longer between placements.")
    long bridgeGraceMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the haste boost granted per bridge placement.", impact = "Higher values keep the placement-speed boost active longer.")
    int hasteDurationTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the haste boost granted per bridge placement.", impact = "Higher values strengthen the placement-speed boost.")
    int hasteAmplifier = 0;
  }
}
