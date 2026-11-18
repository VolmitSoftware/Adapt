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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
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
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;

public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {
  private static final Rgb LIME = new Rgb(0, 255, 0);
  private static final Id AIR = Id.minecraft("air");
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", HerbalismMessages.PREFERENCE_HERBALISMREPLANT_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", HerbalismMessages.PREFERENCE_HERBALISMREPLANT_SNEAK, CommonPreferences.Toggle.OFF);


  public HerbalismReplant() {
    super("herbalism-replant");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("pumpkin_seeds"));
    setInterval(6090);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat_seeds"))
        .key("challenge_herbalism_replant_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("composter"))
            .key("challenge_herbalism_replant_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
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
    if (action == InteractAction.RIGHT_CLICK_AIR && Tasks.isFoliaThreading()) {
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

    ItemRef right = p.inventory().mainHand();
    ItemRef left = p.inventory().offHand();
    ItemRef harvestTool;
    Id cooldownMaterial;
    boolean offHand;

    if (isTool(left) && isHoe(left) && !p.hasCooldown(left.type())) {
      harvestTool = left.copy();
      cooldownMaterial = left.type();
      offHand = true;
    } else if (isTool(right) && isHoe(right) && !p.hasCooldown(right.type())) {
      harvestTool = right.copy();
      cooldownMaterial = right.type();
      offHand = false;
    } else {
      return;
    }

    double footprint = Math.max(1.0D, getRadius(lvl));
    if (Tasks.isFoliaThreading()
        && !Tasks.isOwnedByCurrentRegion(target.location(), footprint, footprint)) {
      return;
    }
    if (!hit(p, target, harvestTool)) {
      return;
    }

    if (offHand) {
      damageOffHand(p, 1 + ((lvl - 1) * 7));
    } else {
      damageHand(p, 1 + ((lvl - 1) * 7));
    }
    p.setCooldown(cooldownMaterial, getCooldown(getLevelPercent(p), lvl));

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
            Tasks.runAt(i.location(), () -> hit(p, i, harvestTool), M.irand(1, 6));
          }
        }
      }

      fx(target.center(), FxPriority.TRANSITION)
          .dustRing(LIME, footprint, Math.min(28, (int) Math.round(footprint * 8)), 1.0F)
          .chord(Sounds.ITEM_SHOVEL_FLATTEN, 1F, 0.66F, Sounds.BLOCK_BAMBOO_SAPLING_BREAK, 1F, 0.66F);
    }
  }

  private boolean hit(PlayerRef p, BlockRef b, ItemRef harvestTool) {
    if (b == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(p))) {
      return false;
    }
    BlockStateRef ageable = b.state();
    if (!preference(p, MATERIALS).allows(b.type()) || ageable.age() < 0
        || ageable.age() != ageable.maxAge()
        || getActiveBlockBreakLevel(p, b.location()) <= 0
        || !canBlockBreak(p, b.location())
        || !ProtectionProbes.attemptBlockBreakProbe(p, b)) {
      return false;
    }
    BlockStateRef current = b.state();
    if (current.age() < 0
        || current.age() != current.maxAge()) {
      return false;
    }
    Id cropType = b.type();

    boolean dropToInventory = getActiveSiblingBlockBreakLevel(
        p,
        "herbalism-drop-to-inventory",
        b.location()
    ) > 0;
    List<ItemRef> items = new ArrayList<>(b.drops(harvestTool, p));
    boolean seedConsumed = consumeSeed(items, current.placementItem());
    if (seedConsumed && (!canBlockPlace(p, b.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(p, b))) {
      return false;
    }
    BlockStateRef commitData = b.state();
    if (!b.type().equals(cropType)
        || commitData.age() < 0
        || commitData.age() != commitData.maxAge()) {
      return false;
    }

    xp(p, b.center(), ((SkillHerbalism.Config) getSkill().getConfig()).harvestPerAgeXP * commitData.age());
    if (seedConsumed) {
      xp(p, b.center(), ((SkillHerbalism.Config) getSkill().getConfig()).plantCropSeedsXP);
    }
    if (dropToInventory) {
      boolean caught = false;
      for (ItemRef i : items) {
        if (!isItem(i) || i.amount() <= 0) {
          continue;
        }
        boolean allowedDrop = true;
        for (Adaptation<?> sibling : getSkill().getAdaptations()) {
          if (sibling instanceof HerbalismDropToInventory pickup) {
            allowedDrop = pickup.acceptsDrop(p, i.type());
            break;
          }
        }
        if (!allowedDrop) {
          p.world().dropItemNaturally(b.center().position(), i);
          continue;
        }
        List<ItemRef> overflow = p.inventory().addItem(i);
        if (overflow == null || overflow.isEmpty()) {
          caught = true;
        } else {
          for (ItemRef rest : overflow) {
            p.world().dropItem(p.location().position(), rest);
          }
        }
      }
      if (caught) {
        fx(p.eyeLocation().add(0, -0.4, 0), FxPriority.TRANSITION)
            .chord(Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sounds.BLOCK_CALCITE_HIT, 0.3F, 1.2F);
      }
    } else {
      for (ItemRef i : items) {
        if (!isItem(i) || i.amount() <= 0) {
          continue;
        }
        p.world().dropItemNaturally(b.center().position(), i);
      }
    }

    if (seedConsumed) {
      b.setState(commitData.with("age", "0"), true);
      addStat(p, "harvest.planted", 1);
      addStat(p, "herbalism.replant.crops-replanted", 1);
    } else {
      b.setType(AIR, true);
    }

    addStat(p, "harvest.blocks", 1);

    fx(b.location().add(0.5, 0.6, 0.5), FxPriority.TRANSITION)
        .dustRing(LIME, 0.4D, 8, 0.8F)
        .particle(Particles.HAPPY_VILLAGER, 3, 0, 0, 0, 0.15D, 0.02D)
        .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D);

    if (M.r(1D / (double) getLevel(p))) {
      fx(b.center(), FxPriority.TRANSITION)
          .sound(Sounds.ITEM_CROP_PLANT, 1F, 0.7F);
    }
    return true;
  }

  private boolean consumeSeed(List<ItemRef> drops, Id seedType) {
    if (seedType == null || seedType.isAir()) {
      return false;
    }

    for (ItemRef drop : drops) {
      if (isItem(drop) && drop.type().equals(seedType) && drop.amount() > 0) {
        drop.setAmount(drop.amount() - 1);
        return true;
      }
    }

    return false;
  }



  @ConfigDescription("Right-click a crop with a hoe to harvest and replant it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Lvl1 for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownLvl1 = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCooldown = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Factor for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownFactor = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusCooldown = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Sub for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int radiusSub = 1;

    public Config() {
      baseCost = 6;
      costFactor = 0.95;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
