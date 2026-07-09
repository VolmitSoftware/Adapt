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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class SeaborneSpeed extends SimpleAdaptation<SeaborneSpeed.Config> {
  private final Map<UUID, Boolean> gracing = playerState();
  private final Cooldowns swimSound = cooldowns();

  public SeaborneSpeed() {
    super("seaborne-speed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.dolphin_grace");
    setIcon(Material.PRISMARINE_CRYSTALS);
    setInterval(1020);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HEART_OF_THE_SEA)
        .key("challenge_seaborne_speed_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TRIDENT)
            .key("challenge_seaborne_speed_100k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_speed_10k", "seaborne.speed.blocks-swum", 10000, 300);
    registerMilestone("challenge_seaborne_speed_100k", "seaborne.speed.blocks-swum", 100000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore1") + C.GREEN + (level) + C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("seaborn.dolphin_grace.lore3"));
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        UUID id = player.getUniqueId();
        boolean depthStrider = player.getInventory().getBoots() != null
            && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER);
        boolean active = player.isInWater() && !depthStrider;
        boolean was = gracing.getOrDefault(id, false);
        if (!active) {
          if (was) {
            gracing.put(id, false);
          }
          return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 62, level));
        addStat(player, "seaborne.speed.blocks-swum", 1);

        if (!was) {
          gracing.put(id, true);
          timeline(player)
              .duration(10)
              .priority(FxPriority.TRANSITION)
              .cullRadius(24)
              .frame((f, tick, progress) -> {
                f.ring(Particle.BUBBLE, 0.8D - (0.5D * progress), 12, 0.1D);
                f.dustRing(0.8D - (0.5D * progress), 8, 0.9F);
                if (tick == 0) {
                  f.chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.45F, 1.2F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 1.4F);
                }
              })
              .start();
          return;
        }

        Vector vel = player.getVelocity();
        double speedSq = vel.lengthSquared();
        if (speedSq <= 0.09D) {
          return;
        }

        int count = Math.min(6, 3 + (int) (speedSq * 2.0D));
        FxEmitter emitter = fx(player.getLocation(), FxPriority.TRAIL)
            .trail(Particle.BUBBLE, -vel.getX(), -vel.getY(), -vel.getZ(), 1.4D, count)
            .particle(Particle.GLOW, 1, 0D, 0.4D, 0D, 0.1D, 0D);
        if (swimSound.isReady(id, 2000L)) {
          swimSound.mark(id);
          emitter.sound(Sound.ENTITY_DOLPHIN_SPLASH, 0.25F, 1.1F);
        }
      });
    }
  }

  @ConfigDescription("Swim faster with dolphin-like grace.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.525;
      maxLevel = 7;
    }
  }
}
