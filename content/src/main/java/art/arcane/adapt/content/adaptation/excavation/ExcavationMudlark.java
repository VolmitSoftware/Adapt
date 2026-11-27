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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationMudlark extends SimpleAdaptation<ExcavationMudlark.Config> {
  public static final Id ID = Id.adapt("excavation/mudlark");
  public static final PlayerPreference<CommonPreferences.Toggle> HASTE = CommonPreferences.toggle("wet-haste", Text.key("adapt.excavation.preferences.excavationmudlark.wet-haste"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DROPS = CommonPreferences.toggle("bonus-drops", Text.key("adapt.excavation.preferences.excavationmudlark.bonus-drops"), CommonPreferences.Toggle.ON);

  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> wetHasteUntil = playerState();

  public ExcavationMudlark() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("mud"));
    setInterval(4530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("mud"))
        .key("challenge_excavation_mudlark_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_mudlark_1k", "excavation.mudlark.bonus-drops", 1000, 500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HASTE, DROPS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBonusChance(level), 1), 1);
    statLore(v, getHasteAmplifier(level) + 1, 2);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, HASTE)) {
      return;
    }
    if (!ItemKinds.isShovel(p.inventory().mainHand())) {
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

    AdaptAttributeService.get().applyTimed(p, getId().toString(), "haste", Attributes.BLOCK_BREAK_SPEED, hasteScalar(getHasteAmplifier(context.level())), AttributeOperation.ADD_MULTIPLIED_BASE, hasteDurationTicks);

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long until = wetHasteUntil.get(id);
    boolean onset = until == null || now >= until;
    wetHasteUntil.put(id, now + (hasteDurationTicks * MILLIS_PER_TICK));
    HudStatus.duration(id, this, now, hasteDurationTicks * MILLIS_PER_TICK);
    if (onset) {
      fx(p.eyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.SPLASH, 5, 0, 0.2D, 0, 0.3D, 0.02D)
          .sound(Sounds.ENTITY_PLAYER_SPLASH, 0.3f, 1.4f);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    Id type = e.block().type();
    if (!ExcavationFinds.isMudlarkBlock(type) || !preferenceEnabled(e.player(), DROPS)) {
      return;
    }

    PlayerRef p = e.player();
    if (!ItemKinds.isShovel(p.inventory().mainHand())) {
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

    ExcavationFinds.dropMudlarkBonus(this, e.block().center(), ExcavationFinds.mudlarkBonus(type));
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

  static void bonusFx(Adaptation<?> owner, Loc drop, ItemRef bonus) {
    Fx.now(owner, drop, FxPriority.TRANSITION)
        .particle(Particles.SPLASH, 8, 0, 0, 0, 0.25D, 0.01D)
        .particle(Particles.itemCrack(bonus), 3, 0, 0.1D, 0, 0.15D, 0.02D)
        .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 1.2f, Sounds.ENTITY_ITEM_PICKUP, 0.4f, 1.5f);
  }

  @ConfigDescription("Bonus drops from muddy blocks, plus haste while digging in water or rain.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Bonus Chance Base for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceBase = 0.05;
    @ConfigDoc(value = "Controls Bonus Chance Factor for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceFactor = 0.2;
    @ConfigDoc(value = "Controls Max Bonus Chance for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusChance = 0.25;
    @ConfigDoc(value = "Controls Max Haste Level for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHasteLevel = 3;
    @ConfigDoc(value = "Controls Haste Duration Ticks for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 60;
    @ConfigDoc(value = "Controls Xp Per Bonus Drop for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDrop = 3;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
