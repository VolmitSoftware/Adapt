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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


public class DiscoveryUnity extends SimpleAdaptation<DiscoveryUnity.Config> {
  public static final Id ID = Id.adapt("discovery/unity");
  private final Cooldowns chimeThrottle = cooldowns();

  public DiscoveryUnity() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("end_crystal"));
    setInterval(666);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("experience_bottle"))
        .key("challenge_discovery_unity_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanting_table"))
            .key("challenge_discovery_unity_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_unity_5k", "discovery.unity.orbs-distributed", 5000, 400);
    registerMilestone("challenge_discovery_unity_50k", "discovery.unity.orbs-distributed", 50000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.discovery.unity.per_orb").withMarkup("xp", Format.f(getXPGained(getLevelPercent(level), 1), 0)).resolve());
  }

  //Give random XP to the player when they gain XP!
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || e.amount() <= 0) {
      return;
    }

    xp(p, 5);
    PlayerProfile ap = getPlayer(p);
    if (ap == null) {
      return;
    }
    List<SkillProgress> skills = ap.skills();
    if (skills.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    SkillProgress skill = skills.get(random.nextInt(skills.size()));
    DiscoveryXpShare.feed(skill, getXPGained(getLevelPercent(getLevel(p)), random.nextInt(3) + 1), getId() + ":distribution");
    double distributed = ap.stat("discovery.unity.orbs-distributed");
    ap.addStat("discovery.unity.orbs-distributed", 1);

    UUID id = p.uuid();
    if (chimeThrottle.isReady(id, 300L)) {
      chimeThrottle.mark(id);
      fx(p.eyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.WAX_ON, 2, 0, 0, 0, 0.1, 0.02)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.7F);
    }

    if (((long) distributed + 1L) % 1000L == 0L) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 12, 0, 0, 0, 0.1, 0.05)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.3F, 1.0F);
    }
  }

  private double getXPGained(double factor, int amount) {
    return DiscoveryXpShare.orbXp(getConfig().xpGainedMultiplier, factor, amount);
  }


  @ConfigDescription("Gaining vanilla experience adds XP to random skills.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Xp Gained Multiplier for the Discovery Unity adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpGainedMultiplier = 8;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 7;
      initialCost = 6;
    }
  }
}
