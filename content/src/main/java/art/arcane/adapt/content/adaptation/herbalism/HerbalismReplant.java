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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;

import java.util.concurrent.ThreadLocalRandom;
public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {
  public static final Id ID = Id.adapt("herbalism/replant");
  private static final Rgb LIME = new Rgb(0, 255, 0);
  public static final Id AIR = Id.minecraft("air");
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", Text.key("adapt.herbalism.preferences.herbalismreplant.materials"));
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", Text.key("adapt.herbalism.preferences.herbalismreplant.sneak"), CommonPreferences.Toggle.OFF);
  private final HerbalismHarvest harvest = new HerbalismHarvest(this, MATERIALS, (BlockRef crop) -> true,
      (PlayerRef p, boolean replanted) -> {
        if (replanted) {
          addStat(p, "harvest.planted", 1);
          addStat(p, "herbalism.replant.crops-replanted", 1);
        }
        addStat(p, "harvest.blocks", 1);
      });

  public HerbalismReplant() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("pumpkin_seeds"));
    setInterval(6090);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat_seeds"))
        .key("challenge_herbalism_replant_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("composter"))
            .key("challenge_herbalism_replant_25k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_replant_500", "herbalism.replant.crops-replanted", 500, 300);
    registerMilestone("challenge_herbalism_replant_25k", "herbalism.replant.crops-replanted", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getRadius(level), 1);
  }


  private int getCooldown(double factor, int level) {
    if (level == 1) {
      return (int) getConfig().cooldownLvl1;
    }

    return (int) ((getConfig().baseCooldown - (getConfig().cooldownFactor * factor)) + getConfig().bonusCooldown);
  }

  private float getRadius(int lvl) {
    return lvl - getConfig().radiusSub;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if ((action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) || e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if ((action == InteractAction.RIGHT_CLICK_BLOCK && e.useBlock() == UseResult.DENY)
        || (action == InteractAction.RIGHT_CLICK_AIR && e.useItem() == UseResult.DENY)) {
      return;
    }

    PlayerRef p = e.player();
    if (action == InteractAction.RIGHT_CLICK_AIR && AdaptApi.scheduler().isRegionized()) {
      return;
    }
    int lvl = getActiveLevel(p);
    if (lvl <= 0 || preferenceEnabled(p, SNEAK) && !p.isSneaking()) {
      return;
    }

    BlockRef target = e.clickedBlock();
    if (target == null) {
      target = p.targetBlock(5);
    }

    if (target == null) {
      return;
    }
    BlockStateRef targetAge = target.state();
    if (targetAge.age() < 0) {
      return;
    }

    if (targetAge.age() != targetAge.maxAge()) {
      return;
    }

    if (!canBlockBreak(p, target.location())) {
      return;
    }

    HerbalismHarvest.Hoe hoe = HerbalismHarvest.readyHoe(this, p);
    if (hoe == null) {
      return;
    }
    ItemRef harvestTool = hoe.tool();

    double footprint = Math.max(1.0D, getRadius(lvl));
    if (AdaptApi.scheduler().isRegionized()
        && !AdaptApi.scheduler().ownsArea(target.location(), footprint, footprint)) {
      return;
    }
    if (!harvest.harvest(p, target, harvestTool)) {
      return;
    }

    if (hoe.offHand()) {
      damageOffHand(p, 1 + ((lvl - 1) * 7));
    } else {
      damageHand(p, 1 + ((lvl - 1) * 7));
    }
    int cooldownTicks = getCooldown(getLevelPercent(p), lvl);
    p.setCooldown(hoe.material(), cooldownTicks);
    HudStatus.cooldown(p, this, cooldownTicks * 50L);

    if (lvl > 1) {
      int vertical = (int) Math.floor(getRadius(lvl));
      int horizontal = Math.round(getRadius(lvl));
      for (int x = target.x() - horizontal; x <= target.x() + horizontal; x++) {
        for (int y = target.y() - vertical; y <= target.y() + vertical; y++) {
          for (int z = target.z() - horizontal; z <= target.z() + horizontal; z++) {
            if (x == target.x() && y == target.y() && z == target.z()) {
              continue;
            }
            BlockRef i = target.world().blockAt(x, y, z);
            AdaptApi.scheduler().runAt(i.location(), () -> harvest.harvest(p, i, harvestTool), ThreadLocalRandom.current().nextInt(1, 6 + 1));
          }
        }
      }

      fx(target.center(), FxPriority.TRANSITION)
          .dustRing(LIME, footprint, Math.min(28, (int) Math.round(footprint * 8)), 1.0F)
          .chord(Sounds.ITEM_SHOVEL_FLATTEN, 1F, 0.66F, Sounds.BLOCK_BAMBOO_SAPLING_BREAK, 1F, 0.66F);
    }
  }

  /** The puff on a harvested crop; {@code planted} adds the planting sound. */
  public static void harvestFx(Adaptation<?> owner, BlockRef b, boolean planted) {
    Fx.now(owner, b.location().add(0.5, 0.6, 0.5), FxPriority.TRANSITION)
        .dustRing(LIME, 0.4D, 8, 0.8F)
        .particle(Particles.HAPPY_VILLAGER, 3, 0, 0, 0, 0.15D, 0.02D)
        .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D);

    if (planted) {
      Fx.now(owner, b.center(), FxPriority.TRANSITION)
          .sound(Sounds.ITEM_CROP_PLANT, 1F, 0.7F);
    }
  }

  public static void pocketFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.eyeLocation().add(0, -0.4, 0), FxPriority.TRANSITION)
        .chord(Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sounds.BLOCK_CALCITE_HIT, 0.3F, 1.2F);
  }

  @ConfigDescription("Right-click a crop with a hoe to harvest and replant it.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Cooldown Lvl1 for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownLvl1 = 2;
    @ConfigDoc(value = "Controls Base Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCooldown = 30;
    @ConfigDoc(value = "Controls Cooldown Factor for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownFactor = 30;
    @ConfigDoc(value = "Controls Bonus Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusCooldown = 20;
    @ConfigDoc(value = "Controls Radius Sub for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int radiusSub = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 2;
    }
  }
}
