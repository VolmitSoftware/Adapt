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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SeabornMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;

public class SeaborneTurtlesMiningSpeed extends SimpleAdaptation<SeaborneTurtlesMiningSpeed.Config> {
  private static final String ATTRIBUTE_SLOT = "mining";
  private static final long EFFECT_DURATION_TICKS = 160L;
  private static final double SUBMERGED_MINING_SPEED_BONUS = 0.4D;

  private final Map<UUID, Boolean> submerged = playerState();
  private final Cooldowns breakPulse = cooldowns();

  public SeaborneTurtlesMiningSpeed() {
    super("seaborne-turtles-mining-speed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.haste");
    setIcon(Material.PRISMARINE_SHARD);
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_seaborne_mining_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_seaborne_mining_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_mining_2500", "seaborne.turtles-mining.blocks-underwater", 2500, 300);
    registerMilestone("challenge_seaborne_mining_25k", "seaborne.turtles-mining.blocks-underwater", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(SeabornMessages.HASTE_LORE1));
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    Location at = e.getBlock().getLocation();
    withAdaptedPlayer(p, e, () -> {
      if (!p.isInWater()) {
        return;
      }

      addStat(p, "seaborne.turtles-mining.blocks-underwater", 1);
      if (!breakPulse.isReady(p.getUniqueId(), 150L)) {
        return;
      }
      breakPulse.mark(p.getUniqueId());
      fx(at.add(0.5D, 0.5D, 0.5D), FxPriority.AMBIENT)
          .particle(Particle.BUBBLE, 4, 0, 0, 0, 0.05D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.2F, 1.7F);
    });
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        UUID id = player.getUniqueId();
        int level = getActiveLevel(player);
        if (level <= 0) {
          submerged.remove(id);
          return;
        }

        AdaptAttributeService.get().applyTimed(player, getName(), ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED, SUBMERGED_MINING_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EFFECT_DURATION_TICKS);

        boolean was = submerged.getOrDefault(id, false);
        if (!player.isInWater()) {
          if (was) {
            submerged.remove(id);
          }
          return;
        }

        if (!was) {
          submerged.put(id, true);
          fx(player.getLocation().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
              .ring(Particle.CRIT, 0.5D, 6, 0.0D)
              .particle(Particle.BUBBLE, 6, 0D, 0.2D, 0D, 0.35D, 0.02D)
              .dustBurst(3, 0.3D, 0.9F)
              .chord(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.5F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.25F, 1.0F);
        }
      });
    }
  }

  @ConfigDescription("Mine faster while underwater.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 15;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
