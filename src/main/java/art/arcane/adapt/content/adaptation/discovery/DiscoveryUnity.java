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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.List;
import java.util.UUID;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public class DiscoveryUnity extends SimpleAdaptation<DiscoveryUnity.Config> {
  private final Cooldowns chimeThrottle = cooldowns();

  public DiscoveryUnity() {
    super("discovery-unity");
    registerConfiguration(Config.class);
    setIcon(Material.END_CRYSTAL);
    setInterval(666);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_discovery_unity_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTING_TABLE)
            .key("challenge_discovery_unity_50k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_unity_5k", "discovery.unity.orbs-distributed", 5000, 400);
    registerMilestone("challenge_discovery_unity_50k", "discovery.unity.orbs-distributed", 50000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getXPGained(getLevelPercent(level), 1), 0) + " " + Localizer.dLocalize("discovery.unity.lore1") + C.GRAY + " " + Localizer.dLocalize("discovery.unity.lore2"));
  }

  //Give random XP to the player when they gain XP!
  @EventHandler(priority = EventPriority.LOW)
  public void on(PlayerExpChangeEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p) || e.getAmount() <= 0) {
      return;
    }

    xp(p, 5);
    AdaptPlayer ap = getPlayer(p);
    List<PlayerSkillLine> skills = ap.getData().getSkillLines().sortV();
    if (skills.isEmpty()) {
      return;
    }

    PlayerSkillLine skill = skills.get(RANDOM.nextInt(skills.size()));
    skill.giveXPFresh(Adapt.instance.getAdaptServer().getPlayer(p).getNot(), getXPGained(getLevelPercent(getLevel(p)), RANDOM.nextInt(3) + 1));
    double distributed = ap.getData().getStat("discovery.unity.orbs-distributed");
    ap.getData().addStat("discovery.unity.orbs-distributed", 1);

    UUID id = p.getUniqueId();
    if (chimeThrottle.isReady(id, 300L)) {
      chimeThrottle.mark(id);
      fx(p.getEyeLocation(), FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, 2, 0, 0, 0, 0.1, 0.02)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.7F);
    }

    if (((long) distributed + 1L) % 1000L == 0L) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particle.REVERSE_PORTAL, 12, 0, 0, 0, 0.1, 0.05)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.3F, 1.0F);
    }
  }

  private double getXPGained(double factor, int amount) {
    return amount * getConfig().xpGainedMultiplier * factor;
  }


  @ConfigDescription("Collecting Experience Orbs adds XP to random skills.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Gained Multiplier for the Discovery Unity adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpGainedMultiplier = 8;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
