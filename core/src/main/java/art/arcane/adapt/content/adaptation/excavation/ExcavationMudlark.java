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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationMudlark extends SimpleAdaptation<ExcavationMudlark.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> HASTE = CommonPreferences.toggle("wet-haste", ExcavationMessages.PREFERENCE_EXCAVATIONMUDLARK_HASTE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DROPS = CommonPreferences.toggle("bonus-drops", ExcavationMessages.PREFERENCE_EXCAVATIONMUDLARK_DROPS, CommonPreferences.Toggle.ON);

  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> wetHasteUntil = playerState();

  public ExcavationMudlark() {
    super("excavation-mudlark");
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("mud"));
    setInterval(4530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("mud"))
        .key("challenge_excavation_mudlark_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_mudlark_1k", "excavation.mudlark.bonus-drops", 1000, 500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HASTE, DROPS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getBonusChance(level), 1), 1);
    statLore(v, getHasteAmplifier(level) + 1, 2);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, HASTE)) {
      return;
    }
    if (!isShovel(p.inventory().mainHand())) {
      return;
    }

    if (!isWet(p)) {
      return;
    }

    BlockActionContext context = resolveInteractContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int hasteDurationTicks = getConfig().hasteDurationTicks;
    if (hasteDurationTicks <= 0) {
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getName(), "haste", Attributes.BLOCK_BREAK_SPEED, hasteScalar(getHasteAmplifier(context.level())), AttributeOperation.ADD_MULTIPLIED_BASE, hasteDurationTicks);

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long until = wetHasteUntil.get(id);
    boolean onset = until == null || now >= until;
    wetHasteUntil.put(id, now + (hasteDurationTicks * MILLIS_PER_TICK));
    AbilityGlyphs.duration(id, this, now, hasteDurationTicks * MILLIS_PER_TICK);
    if (onset) {
      fx(p.eyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.SPLASH, 5, 0, 0.2D, 0, 0.3D, 0.02D)
          .sound(Sounds.ENTITY_PLAYER_SPLASH, 0.3f, 1.4f);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    Id type = e.block().type();
    if (!isMudlarkBlock(type) || !preferenceEnabled(e.player(), DROPS)) {
      return;
    }

    PlayerRef p = e.player();
    if (!isShovel(p.inventory().mainHand())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    Id bonus = getBonusDrop(type);
    Loc drop = e.block().center();
    ItemOps items = AdaptCore.platform().host().items();
    e.block().world().dropItemNaturally(drop.position(), items.create(bonus, 1));
    fx(drop, FxPriority.TRANSITION)
        .particle(Particles.SPLASH, 8, 0, 0, 0, 0.25D, 0.01D)
        .particle(Particles.itemCrack(items.create(bonus)), 3, 0, 0.1D, 0, 0.15D, 0.02D)
        .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 1.2f, Sounds.ENTITY_ITEM_PICKUP, 0.4f, 1.5f);
    addStat(p, "excavation.mudlark.bonus-drops", 1);
    xp(p, getConfig().xpPerBonusDrop);
  }

  private boolean isWet(PlayerRef p) {
    if (p.isInWater()) {
      return true;
    }

    if (!p.world().hasStorm()) {
      return false;
    }

    return p.world().blockAt(p.location()).skyLight() >= 14;
  }

  private boolean isMudlarkBlock(Id type) {
    if (!type.isMinecraft()) {
      return false;
    }
    return switch (type.path()) {
      case "clay", "mud", "muddy_mangrove_roots", "soul_sand", "soul_soil" -> true;
      default -> false;
    };
  }

  private Id getBonusDrop(Id type) {
    return switch (type.path()) {
      case "clay" -> Id.minecraft("clay_ball");
      case "mud", "muddy_mangrove_roots" -> Id.minecraft("mud");
      case "soul_sand" -> Id.minecraft("soul_sand");
      default -> Id.minecraft("soul_soil");
    };
  }

  private double getBonusChance(int level) {
    return Math.min(getConfig().maxBonusChance, getConfig().bonusChanceBase + (getLevelPercent(level) * getConfig().bonusChanceFactor));
  }

  private int getHasteAmplifier(int level) {
    return hasteAmplifier(getLevelPercent(level), getConfig().maxHasteLevel);
  }

  static int hasteAmplifier(double levelPercent, double maxHasteLevel) {
    return Math.max(0, (int) Math.round(levelPercent * (maxHasteLevel - 1)));
  }

  static double hasteScalar(int amplifier) {
    return 0.20D * (amplifier + 1);
  }

  @ConfigDescription("Bonus drops from muddy blocks, plus haste while digging in water or rain.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Base for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Factor for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Chance for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Haste Level for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHasteLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Duration Ticks for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Drop for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDrop = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
