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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AgilityMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.List;
import java.util.UUID;

public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
  private static final String SLOT_JUMP = "jump";
  private static final int SUPER_JUMP_LEVELS = 4;

  private final Cooldowns cooldowns = cooldowns();

  public AgilitySuperJump() {
    super("agility-super-jump");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER_BOOTS);
    setInterval(9999);
    setMaxLevel(SUPER_JUMP_LEVELS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_BOOTS)
        .key("challenge_agility_super_jump_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_BOOTS)
            .key("challenge_agility_super_jump_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_super_jump_100", "agility.super-jump.jumps", 100, 300);
    registerMilestone("challenge_agility_super_jump_5k", "agility.super-jump.jumps", 5000, 1500);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    newConfig.maxLevel = SUPER_JUMP_LEVELS;
    setMaxLevel(SUPER_JUMP_LEVELS);
  }

  static double jumpHeight(double minimumHeight, double maximumHeight, int level) {
    return jumpHeight(minimumHeight, maximumHeight, level, SUPER_JUMP_LEVELS);
  }

  static double jumpHeight(double minimumHeight, double maximumHeight, int level, int maxLevel) {
    double vanillaHeight = AgilityJumpPhysics.heightForStrength(AgilityJumpPhysics.VANILLA_JUMP_STRENGTH);
    double safeMinimum = Double.isFinite(minimumHeight) ? minimumHeight : vanillaHeight;
    double safeMaximum = Double.isFinite(maximumHeight) ? maximumHeight : safeMinimum;
    double lower = Math.max(vanillaHeight, Math.min(safeMinimum, safeMaximum));
    double upper = Math.max(lower, Math.max(safeMinimum, safeMaximum));
    if (maxLevel <= 1) {
      return upper;
    }

    int clampedLevel = Math.max(1, Math.min(level, maxLevel));
    double progress = (double) (clampedLevel - 1) / (maxLevel - 1);
    return lower + ((upper - lower) * progress);
  }

  static double jumpStrengthBonus(double jumpHeight) {
    return AgilityJumpPhysics.bonusForHeight(jumpHeight);
  }

  private double getJumpHeight(int level) {
    return jumpHeight(getConfig().minimumJumpHeight, getConfig().maximumJumpHeight, level);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getJumpHeight(level), 2), 1);
    v.addLore(C.LIGHT_PURPLE + " " + AdaptLanguage.text(AgilityMessages.SUPER_JUMP_LORE2));
  }

  private void applyBoost(Player p) {
    AdaptAttributeService.get().apply(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH, jumpStrengthBonus(getJumpHeight(getLevel(p))), AttributeModifier.Operation.ADD_NUMBER);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking()) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (canUse(getPlayer(p))) {
        applyBoost(p);
      }

      if (p.isOnGround()) {
        fx(p.getLocation(), FxPriority.GAMEPLAY)
            .ring(Particle.CLOUD, 0.5D, 6, 0.05D)
            .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.35F);
      }
    });
  }

  @EventHandler
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.getNewGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      AdaptAttributeService.get().remove(e.getPlayer(), getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
    }
  }

  @EventHandler
  public void on(PlayerChangedWorldEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
    }
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("com.destroystokyo.paper.event.player.PlayerJumpEvent")) {
      return List.of();
    }

    return List.of(new JumpListener());
  }

  private final class JumpListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerJumpEvent e) {
      Player p = e.getPlayer();
      if (!hasAdaptation(p) || !p.isSneaking()) {
        return;
      }

      handleJump(p, e);
    }
  }

  // Cancellable keeps the outer signature free of Paper event types.
  private void handleJump(Player p, Cancellable e) {
    withPlayerThread(p, e, () -> {
      if (!hasActiveAdaptation(p) || !canUse(getPlayer(p))) {
        AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
        return;
      }

      applyBoost(p);

      UUID id = p.getUniqueId();
      if (!cooldowns.isReady(id, 1000)) {
        return;
      }

      cooldowns.mark(id);
      addStat(p, "agility.super-jump.jumps", 1);

      double jumpHeight = getJumpHeight(getLevel(p));
      float topPitch = (float) Math.min(2.0D, 1.4D + jumpHeight);
      double shockRadius = Math.max(0.6D, jumpHeight * 1.5D);
      fx(p.getLocation(), FxPriority.GAMEPLAY)
          .column(Particle.CLOUD, 8, 1.2D)
          .ring(Particles.BLOCK_CRACK, shockRadius, 10, 0.05D, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData())
          .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, 0.7F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, topPitch, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.3F, 0.7F);
    });
  }


  @ConfigDescription("Sneak and jump for exceptional height advantage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Jump apex in blocks at level 1.", impact = "Higher values make the first super-jump level rise further.")
    double minimumJumpHeight = 1.5D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Jump apex in blocks at level 4.", impact = "Higher values make the final super-jump level rise further.")
    double maximumJumpHeight = 2.5D;

    public Config() {
      baseCost = 2;
      costFactor = 0.55;
      maxLevel = SUPER_JUMP_LEVELS;
      initialCost = 5;
    }
  }
}
