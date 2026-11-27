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
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.UUID;

public class EnchantingRuneSight extends SimpleAdaptation<EnchantingRuneSight.Config> {
  public static final Id ID = Id.adapt("enchanting/rune_sight");
  public static final PlayerPreference<CommonPreferences.Toggle> FULL = CommonPreferences.toggle("full-details", Text.key("adapt.enchanting.preferences.enchantingrunesight.full-details"), CommonPreferences.Toggle.ON);

  private final Cooldowns revealThrottle = cooldowns();

  public EnchantingRuneSight() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_enchanting_rune_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanting_table"))
            .key("challenge_enchanting_rune_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_rune_100", "enchanting.rune-sight.offers-revealed", 100, 300);
    registerMilestone("challenge_enchanting_rune_1k", "enchanting.rune-sight.offers-revealed", 1000, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FULL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getRevealDepth(level), 1);
  }

  static int revealDepth(int maxReveal, double levelPercent) {
    return Math.max(1, Math.min(maxReveal, 1 + (int) Math.floor(levelPercent * (maxReveal - 1))));
  }

  private int getRevealDepth(int level) {
    return revealDepth(getConfig().maxRevealDepth, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PrepareItemEnchantEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    List<EnchantOffer> offers = e.offers();
    if (offers == null || offers.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
    if (!revealThrottle.isReady(id, getConfig().revealThrottleMs)) {
      return;
    }

    int depth = preferenceEnabled(p, FULL) ? getRevealDepth(level) : 1;
    EnchantingOffers.Reveal reveal = EnchantingOffers.reveal(offers, depth);
    if (reveal.revealed() <= 0) {
      return;
    }

    revealThrottle.mark(id);
    HudStatus.show(p, reveal.message());
    addStat(p, "enchanting.rune-sight.offers-revealed", reveal.revealed());
  }


  @ConfigDescription("Reveal the enchantments behind enchanting-table offers before you commit.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Max Reveal Depth for the Enchanting Rune Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxRevealDepth = 3;
    @ConfigDoc(value = "Controls Reveal Throttle Ms for the Enchanting Rune Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long revealThrottleMs = 400;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
