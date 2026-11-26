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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SwordMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class SwordsHeirloomEdge extends SimpleAdaptation<SwordsHeirloomEdge.Config> {
  private static final Rgb HEIRLOOM = Rgb.of(0xE8C46A);
  private static final DataKey<Byte> FLAG_KEY = DataKey.adapt("heirloom_edge", DataCodecs.BYTE);
  private static final DataKey<Integer> KILLS_KEY = DataKey.adapt("heirloom_edge_kills", DataCodecs.INT);
  private static final DataKey<Double> BONUS_KEY = DataKey.adapt("heirloom_edge_bonus", DataCodecs.DOUBLE);
  private static final DataKey<String> LORE_KEY = DataKey.adapt("heirloom_edge_lore", DataCodecs.STRING);
  private static final Id MODIFIER_KEY = Id.adapt("heirloom_edge_damage");

  public SwordsHeirloomEdge() {
    super("sword-heirloom-edge");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_heirloom_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_swords_heirloom_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_heirloom_10", "swords.heirloom-edge.banks", 10, 400);
    registerMilestone("challenge_swords_heirloom_100", "swords.heirloom-edge.banks", 100, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.SWORDS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getGrowthPerBank(level), 2), 1);
    statLore(v, getConfig().killsPerBank, 2);
    statLore(v, Form.f(getBonusCap(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PrepareAnvilEvent e) {
    PlayerRef p = e.player();
    if (p == null || getActiveLevel(p) <= 0) {
      return;
    }

    String rename = e.renameText();
    if (rename == null || rename.isBlank()) {
      return;
    }

    ItemRef result = e.result();
    if (result == null || !isSword(result) || !preference(p, SwordPreferences.SWORDS).accepts(result.type())) {
      return;
    }

    DataHolder data = result.data();
    if (!data.has(FLAG_KEY)) {
      data.set(FLAG_KEY, (byte) 1);
      data.set(KILLS_KEY, 0);
      data.set(BONUS_KEY, 0D);
    }

    applyHeirloomModifier(result, data.getOrDefault(BONUS_KEY, 0D));
    applyHeirloomLore(result);
    e.setResult(result);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    PlayerRef p = e.killer();
    if (p == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }

    Tasks.runEntity(p, () -> bankKill(p));
  }

  private void bankKill(PlayerRef p) {
    if (!p.isOnline()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isSword(hand) || !preference(p, SwordPreferences.SWORDS).accepts(hand.type())) {
      return;
    }

    DataHolder data = hand.data();
    if (!data.has(FLAG_KEY)) {
      return;
    }

    int perBank = Math.max(1, getConfig().killsPerBank);
    double cap = getBonusCap(level);
    int kills = data.getOrDefault(KILLS_KEY, 0) + 1;
    double bonus = data.getOrDefault(BONUS_KEY, 0D);
    boolean banked = false;

    if (kills >= perBank) {
      if (bonus < cap) {
        kills -= perBank;
        bonus = Math.min(cap, bonus + getGrowthPerBank(level));
        banked = true;
      } else {
        kills = perBank;
      }
    }

    data.set(KILLS_KEY, kills);
    data.set(BONUS_KEY, bonus);
    applyHeirloomModifier(hand, bonus);
    applyHeirloomLore(hand);
    p.inventory().setMainHand(hand);

    if (banked) {
      addStat(p, "swords.heirloom-edge.banks", 1);
      xp(p, getConfig().xpPerBank);
      fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
          .dustBurst(HEIRLOOM, 6, 0.3D, 1.0F)
          .burst(Particles.TOTEM, 4, 0.3D)
          .chord(Sounds.BLOCK_ANVIL_USE, 0.3F, 1.7F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.3F);
    }
  }

  private static void applyHeirloomModifier(ItemRef item, double bonus) {
    if (bonus <= 0) {
      item.removeAttributeModifier(Attributes.ATTACK_DAMAGE, MODIFIER_KEY);
      return;
    }

    item.setAttributeModifier(Attributes.ATTACK_DAMAGE,
        new AttributeMod(MODIFIER_KEY, bonus, AttributeOperation.ADD_VALUE), EquipmentSlot.MAIN_HAND);
  }

  private static void applyHeirloomLore(ItemRef item) {
    SwordBladeLore.replace(item, LORE_KEY, SwordMessages.HEIRLOOM_EDGE_NAME.english(),
        C.GOLD + AdaptLanguage.text(SwordMessages.HEIRLOOM_EDGE_NAME));
  }

  /** The damage an heirloom has banked into this blade, 0 for any other item. */
  public static double bankedBonus(ItemRef item) {
    DataHolder data = item.data();
    return data.has(FLAG_KEY) ? data.getOrDefault(BONUS_KEY, 0D) : 0D;
  }

  private double getGrowthPerBank(int level) {
    return getConfig().growthBase + (getLevelPercent(level) * getConfig().growthFactor);
  }

  private double getBonusCap(int level) {
    return getConfig().capBase + (getLevelPercent(level) * getConfig().capFactor);
  }


  @ConfigDescription("Name a sword at an anvil to make it an heirloom that banks a tiny permanent damage bonus every few kills.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Base for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Factor for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cap Base for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double capBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cap Factor for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double capFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Kills required to bank one growth step on the blade.", impact = "Lower values bank power faster; higher values require more kills per step.")
    int killsPerBank = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bank for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBank = 12;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
