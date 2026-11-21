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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;

public class PickaxeChisel extends SimpleAdaptation<PickaxeChisel.Config> {
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private static final Id MENDING = Id.minecraft("mending");

  public PickaxeChisel() {
    super("pickaxe-chisel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_nugget"));
    setInterval(7433);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe"))
        .key("challenge_pickaxe_chisel_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_chisel_500", "pickaxe.chisel.extra-ores", 500, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.REQUIRE_SNEAK, PickaxePreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDropChance(getLevelPercent(level)), 0), 1);
    statLore(v, C.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 2);
  }

  private int getCooldownTime(double levelPercent) {
    return getConfig().cooldownTime;
  }

  private double getDropChance(double levelPercent) {
    return ((levelPercent) * getConfig().dropChanceFactor) + getConfig().dropChanceBase;
  }

  private double getBreakChance(double levelPercent) {
    return getConfig().breakChance;
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().damagePerBlockBase + (getConfig().damageFactorInverseMultiplier * ((1D - levelPercent))));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if ((action == InteractAction.RIGHT_CLICK_BLOCK && e.useBlock() == UseResult.DENY)
        || (action == InteractAction.RIGHT_CLICK_AIR && e.useItem() == UseResult.DENY)) {
      return;
    }

    PlayerRef p = e.player();
    if (!isPickaxe(p.inventory().mainHand()) || !hasActiveAdaptation(p)
        || (preferenceEnabled(p, PickaxePreferences.REQUIRE_SNEAK) && !p.isSneaking())
        || !preference(p, PickaxePreferences.RESERVE).permits(p.inventory().mainHand(), getDamagePerBlock(getLevelPercent(p)))) {
      return;
    }
    if (action == InteractAction.RIGHT_CLICK_AIR && Tasks.isFoliaThreading()) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (hand.enchantments().containsKey(SILK_TOUCH) || hand.enchantments().containsKey(MENDING)) {
      return;
    }
    if (p.cooldownTicks(hand.type()) > 0) {
      return;
    }

    BlockRef target = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (target == null) {
      return;
    }

    if (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(p) || !Tasks.isOwnedByCurrentRegion(target.location()))) {
      return;
    }

    if (!canBlockBreak(p, target.location())) {
      return;
    }
    BlockStateRef b = target.state();
    if (isOre(b)) {
      ItemRef is = getDropFor(b);
      if (is == null) {
        return;
      }
      if (!ProtectionProbes.attemptBlockBreakProbe(p, target)) {
        return;
      }

      Vec3 hit = Tasks.isFoliaThreading() ? null : rayHit(p, 8);
      Loc c = hit != null ? Loc.of(p.world(), hit) : target.center();

      fx(c, FxPriority.GAMEPLAY)
          .particle(Particles.CRIT, 6, 0, 0, 0, 0.08, 0.1)
          .particle(Particles.ELECTRIC_SPARK, 4, 0, 0, 0, 0.06, 0.05)
          .chord(Sounds.BLOCK_DEEPSLATE_PLACE, 1.25f, 1.4f, Sounds.BLOCK_METAL_HIT, 1.25f, 1.7f, Sounds.BLOCK_STONE_HIT, 0.6f, 1.9f);

      int cooldownTicks = getCooldownTime(getLevelPercent(p));
      p.setCooldown(hand.type(), cooldownTicks);
      AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
      damageHand(p, getDamagePerBlock(getLevelPercent(p)));

      if (M.r(getDropChance(getLevelPercent(p)))) {
        fx(c, FxPriority.GAMEPLAY)
            .particle(Particles.itemCrack(is), 14, 0, 0, 0, 0.08, 0.1)
            .chord(Sounds.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.787f, Sounds.BLOCK_AMETHYST_BLOCK_PLACE, 0.55f, 1.89f);
        timeline(c)
            .duration(6)
            .priority(FxPriority.TRANSITION)
            .frame((fxE, tick, progress) -> {
              fxE.ring(Particles.END_ROD, 0.6D - (0.5D * progress), 6, 0.1D);
              if (tick == 0 || tick == 3 || tick == 5) {
                fxE.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, (float) (1.2D + (progress * 0.7D)));
              }
            })
            .start();
        target.world().dropItemNaturally(c.position().subtract(p.location().direction().multiply(0.1)), is);
        addStat(p, "pickaxe.chisel.extra-ores", 1);
      } else {
        fx(c, FxPriority.TRANSITION)
            .particle(Particles.itemCrack(is), 3, 0, 0, 0, 0.02, 0.1)
            .particle(Particles.blockCrack(b), 9, 0, 0, 0, 0.1, 0.0)
            .particle(Particles.SMOKE, 2, 0, 0.1, 0, 0.05, 0.01);
      }

      if (M.r(getBreakChance(getLevelPercent(p)))) {
        fx(c, FxPriority.COMBAT)
            .particle(Particles.blockCrack(b), 18, 0, 0, 0, 0.12, 0.05)
            .ring(Particles.CRIT_MAGIC, 0.7D, 12, 0.1D)
            .chord(Sounds.BLOCK_BASALT_BREAK, 1.25f, 0.4f, Sounds.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.887f);
        target.breakNaturally(p.inventory().mainHand());
      }
    }
  }

  static Vec3 rayHit(PlayerRef player, double range) {
    Loc eye = player.eyeLocation();
    Vec3 start = eye.position();
    Vec3 direction = eye.direction();
    BlockRef hit = player.world().rayTraceBlock(start, direction, range, false);
    if (hit == null) {
      return null;
    }
    List<Box> boxes = hit.collisionBoxes();
    if (boxes.isEmpty()) {
      boxes = List.of(new Box(hit.x(), hit.y(), hit.z(), hit.x() + 1D, hit.y() + 1D, hit.z() + 1D));
    }
    double nearest = Double.POSITIVE_INFINITY;
    for (Box box : boxes) {
      double entry = entryDistance(start, direction, box);
      if (entry >= 0D && entry <= range && entry < nearest) {
        nearest = entry;
      }
    }
    return Double.isInfinite(nearest) ? null : start.add(direction.multiply(nearest));
  }

  static double entryDistance(Vec3 start, Vec3 direction, Box box) {
    double near = 0D;
    double far = Double.POSITIVE_INFINITY;
    double[] origin = {start.x(), start.y(), start.z()};
    double[] step = {direction.x(), direction.y(), direction.z()};
    double[] min = {box.minX(), box.minY(), box.minZ()};
    double[] max = {box.maxX(), box.maxY(), box.maxZ()};
    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(step[axis]) < 1.0E-9D) {
        if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
          return -1D;
        }
        continue;
      }
      double first = (min[axis] - origin[axis]) / step[axis];
      double second = (max[axis] - origin[axis]) / step[axis];
      near = Math.max(near, Math.min(first, second));
      far = Math.min(far, Math.max(first, second));
      if (near > far) {
        return -1D;
      }
    }
    return near;
  }

  private ItemRef getDropFor(BlockStateRef b) {
    Id type = b.type();
    if (!type.isMinecraft()) {
      return null;
    }
    String drop = switch (type.path()) {
      case "coal_ore", "deepslate_coal_ore" -> "coal";
      case "copper_ore", "deepslate_copper_ore" -> "raw_copper";
      case "gold_ore", "deepslate_gold_ore", "nether_gold_ore" -> "raw_gold";
      case "iron_ore", "deepslate_iron_ore" -> "raw_iron";
      case "diamond_ore", "deepslate_diamond_ore" -> "diamond";
      case "lapis_ore", "deepslate_lapis_ore" -> "lapis_lazuli";
      case "emerald_ore", "deepslate_emerald_ore" -> "emerald";
      case "nether_quartz_ore" -> "quartz";
      case "redstone_ore", "deepslate_redstone_ore" -> "redstone";
      default -> null;
    };
    return drop == null ? null : AdaptCore.platform().host().items().create(Id.minecraft(drop));
  }

  @ConfigDescription("Right-click ores to chisel extra ore at a severe durability cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Time for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int cooldownTime = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Break Chance for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Block Base for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor Inverse Multiplier for the Pickaxe Chisel adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactorInverseMultiplier = 2;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      maxLevel = 7;
      initialCost = 5;
    }
  }
}
