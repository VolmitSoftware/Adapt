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

package art.arcane.adapt.content.adaptation.enchanting;

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
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.UUID;

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {
  public static final Id ID = Id.adapt("enchanting/xp_return");
  private final Cooldowns cooldown = cooldowns();

  public EnchantingXPReturn() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.enchanting.return");
    setIcon(Id.minecraft("experience_bottle"));
    setInterval(13001);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("experience_bottle"))
        .key("challenge_enchanting_xp_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_enchanting_xp_100", "enchanting.xp-return.levels-saved", 100, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + Text.key("adapt.enchanting.return.lore1").resolve());
    statLore(v, TextFormat.GREEN, "", rewardXp(level, getConfig().vanillaXpAtLevelOne, getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerEnchant), 2);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    if (!cooldown.isReady(id, getConfig().cooldownMillis)) {
      return;
    }

    int xpAmount = rewardXp(level, getConfig().vanillaXpAtLevelOne, getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerEnchant);
    if (xpAmount <= 0) {
      return;
    }

    cooldown.mark(id);
    p.world().spawnExperience(p.location().position(), xpAmount);
    addStat(p, "enchanting.xp-return.levels-saved", xpAmount);
    xpRefundFx(p, level);
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  private void xpRefundFx(PlayerRef p, int level) {
    int rods = Math.min(level, 5);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANT, (0.7D * (1.0D - progress)) + 0.1D, 1.4D, 2, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.VILLAGER_HAPPY, 4, 0, 1.0D, 0, 0.3D, 0)
                .column(Particles.END_ROD, rods, 1.2D)
                .chord(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
          }
        })
        .start();
  }


  @ConfigDescription("Committed enchants periodically return a bounded vanilla XP orb.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Vanilla XP points returned by a level-one activation.", impact = "Higher values increase the base enchanting refund.")
    int vanillaXpAtLevelOne = 2;
    @ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the refund scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 4;
    @ConfigDoc(value = "Maximum vanilla XP points returned by one enchant.", impact = "Caps oversized or heavily scaled refunds.")
    int maximumXpPerEnchant = 32;
    @ConfigDoc(value = "Minimum milliseconds between vanilla XP refunds for the same player.", impact = "Higher values reduce repeated enchanting refunds.")
    long cooldownMillis = 30000L;

    public Config() {
      initialCost = 1;
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 7;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      vanillaXpAtLevelOne = Math.max(0, Math.min(vanillaXpAtLevelOne, 100000));
      vanillaXpPerAdditionalLevel = Math.max(0, Math.min(vanillaXpPerAdditionalLevel, 100000));
      maximumXpPerEnchant = Math.max(0, Math.min(maximumXpPerEnchant, 100000));
      cooldownMillis = Math.max(0L, Math.min(cooldownMillis, 3600000L));
    }
  }
}
