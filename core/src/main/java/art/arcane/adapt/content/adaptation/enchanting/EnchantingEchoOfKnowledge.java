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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

public class EnchantingEchoOfKnowledge extends SimpleAdaptation<EnchantingEchoOfKnowledge.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", EnchantingMessages.PREFERENCE_ENCHANTINGECHOOFKNOWLEDGE_SNEAK, CommonPreferences.Toggle.OFF);

  private final Cooldowns chargeThrottle = cooldowns();

  public EnchantingEchoOfKnowledge() {
    super("enchanting-echo-of-knowledge");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("knowledge_book"));
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("knowledge_book"))
        .key("challenge_enchanting_echo_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_enchanting_echo_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_echo_25", "enchanting.echo-of-knowledge.levels-charged", 25, 400);
    registerMilestone("challenge_enchanting_echo_250", "enchanting.echo-of-knowledge.levels-charged", 250, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getChargeRate(level), 1), 1);
  }

  static double echoChargeRate(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  private double getChargeRate(int level) {
    return echoChargeRate(getConfig().chargeRateBase, getConfig().chargeRateFactor, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef p = e.player();
    if (e.amount() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || preferenceEnabled(p, SNEAK) && !p.isSneaking()) {
      return;
    }

    ItemRef book = p.inventory().mainHand();
    if (!isChargeableBook(book)) {
      return;
    }

    book = book.copy();
    int gain = Math.max(1, (int) Math.round(e.amount() * getChargeRate(level)));
    EnchantingEchoes.Charge charge = EnchantingEchoes.charge(book, gain, Math.max(1, getConfig().chargeThreshold));
    if (!charge.changed()) {
      return;
    }

    p.inventory().setMainHand(book);
    if (charge.upgraded() != null) {
      addStat(p, "enchanting.echo-of-knowledge.levels-charged", 1);
      xp(p, getConfig().skillXpOnUpgrade);
      EnchantingEchoes.announce(p, charge.upgraded());
      upgradeFx(p);
      return;
    }

    UUID id = p.uuid();
    if (chargeThrottle.isReady(id, 400L)) {
      chargeThrottle.mark(id);
      fx(p.eyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0, 0, 0.3D, 0.03D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F);
    }
  }

  private boolean isChargeableBook(ItemRef item) {
    return isItem(item)
        && item.type().equals(EnchantingBooks.ENCHANTED_BOOK)
        && !item.storedEnchantments().isEmpty();
  }


  private void upgradeFx(PlayerRef p) {
    timeline(p)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(18.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.8D * (1.0D - progress)) + 0.15D, 1.6D, 4, progress * Math.PI * 3.0D);
          if (tick == 0) {
            f.dome(Particles.END_ROD, 1.3D, 12)
                .chord(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.5F, Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
          }
          if (tick == 5) {
            f.column(Particles.CRIT_MAGIC, 5, 1.5D)
                .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
          }
        })
        .start();
  }

  @ConfigDescription("Hold an enchanted book while collecting XP to charge it and upgrade an enchantment within vanilla caps.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Rate Base for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeRateBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Rate Factor for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeRateFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Threshold for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int chargeThreshold = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Upgrade for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnUpgrade = 35;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
