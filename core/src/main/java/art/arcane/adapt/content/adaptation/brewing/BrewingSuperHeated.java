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

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BrewingSuperHeated extends SimpleAdaptation<BrewingSuperHeated.Config> {

  private static final int MAX_CHECKS_BEFORE_REMOVE = 20;
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id FIRE = Id.minecraft("fire");
  private static final BlockFace[] HEAT_FACES = {BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
  private final Map<BlockRef, Integer> activeStands = new ConcurrentHashMap<>();

  public BrewingSuperHeated() {
    super("brewing-super-heated");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lava_bucket"));
    setInterval(253);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_powder"))
        .key("challenge_brewing_super_heated_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("magma_cream"))
            .key("challenge_brewing_super_heated_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewing_super_heated_100", "brewing.super-heated.brews-accelerated", 100, 300);
    registerMilestone("challenge_brewing_super_heated_2500", "brewing.super-heated.brews-accelerated", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getFireBoost(getLevelPercent(level)), 0), 1);
    statLore(v, Form.pc(getLavaBoost(getLevelPercent(level)), 0), 2);
  }

  public double getLavaBoost(double factor) {
    return (getConfig().lavaMultiplier) * (getConfig().multiplierFactor * factor);
  }

  public double getFireBoost(double factor) {
    return (getConfig().fireMultiplier) * (getConfig().multiplierFactor * factor);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryMoveItemEvent e) {
    if (e.destinationKind() != InventoryKind.BREWING || e.destinationBlock() == null) {
      return;
    }

    activeStands.put(e.destinationBlock(), MAX_CHECKS_BEFORE_REMOVE);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    BlockRef block = e.block();
    if (activeStands.containsKey(block)) {
      UUID owner = worldData().blockOwner(block);
      if (owner != null && getServer().getOnlineAdaptationLevel(owner, getSkill().getName(), getName()) > 0) {
        getServer().addStat(owner, "brewing.super-heated.brews-accelerated", 1);
        Loc loc = block.location().add(0.5D, 0.6D, 0.5D);
        fx(loc, FxPriority.TRANSITION)
            .particle(Particles.LAVA, 8, 0, 0.2D, 0, 0.4D, 0.0D)
            .particle(Particles.FLAME, 6, 0, 0.2D, 0, 0.35D, 0.02D)
            .particle(Particles.CAMPFIRE_COSY_SMOKE, 2, 0, 0.4D, 0, 0.2D, 0.02D)
            .chord(Sounds.ENTITY_BLAZE_SHOOT, 0.4F, 1.3F, Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.6F);
      }
    }
    Optional<BrewingStandRef> stand = block.brewingStand();
    if (stand.isPresent() && stand.get().brewingTime() > 0) {
      activeStands.put(block, MAX_CHECKS_BEFORE_REMOVE);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.clickedKind() == null) {
      return;
    }
    if (e.viewKind() == InventoryKind.BREWING) {
      Optional<BlockRef> standBlock = e.player().openContainerBlock();
      if (standBlock.isEmpty()) {
        return;
      }
      activeStands.put(standBlock.get(), MAX_CHECKS_BEFORE_REMOVE);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !activeStands.isEmpty();
  }

  @Override
  public void onTick() {
    if (activeStands.isEmpty()) {
      return;
    }

    for (BlockRef block : activeStands.keySet()) {
      if (block == null) {
        continue;
      }

      Tasks.runAt(block.location(), () -> tickStand(block));
    }
  }

  private void tickStand(BlockRef block) {
    Optional<BrewingStandRef> state = block.brewingStand();
    if (state.isEmpty()) {
      activeStands.remove(block);
      return;
    }
    BrewingStandRef brewingStand = state.get();

    if (brewingStand.brewingTime() <= 0) {
      if (brewingStand.brewingTime() <= 0) {
        Integer remainingChecks = activeStands.get(block);
        if (remainingChecks == null) {
          return;
        }

        if (remainingChecks <= 0) {
          activeStands.remove(block);
        } else {
          activeStands.put(block, remainingChecks - 1);
        }
      }
      return;
    }

    UUID owner = worldData().blockOwner(block);
    if (owner == null) {
      activeStands.remove(block);
      return;
    }

    int level = getServer().getOnlineAdaptationLevel(owner, getSkill().getName(), getName());
    if (level <= 0) {
      activeStands.remove(block);
      return;
    }

    updateHeat(brewingStand, getLevelPercent(level));
  }

  static int computeWarpTicks(long intervalMillis, double fireBoost, double lavaBoost, double fireSources, double lavaSources) {
    double pct = (fireBoost * fireSources) + (lavaBoost * lavaSources);
    if (pct <= 0D) {
      return 0;
    }

    return (int) Math.ceil((intervalMillis / 50D) * pct);
  }

  private void updateHeat(BrewingStandRef b, double factor) {
    double l = 0;
    double f = 0;

    for (BlockFace face : HEAT_FACES) {
      Id type = b.block().relative(face).type();
      if (type.equals(LAVA)) {
        l = l + 1;
      } else if (type.equals(FIRE)) {
        f = f + 1;
      }
    }

    int warp = computeWarpTicks(getInterval(), getFireBoost(factor), getLavaBoost(factor), f, l);
    if (warp <= 0) {
      return;
    }

    b.setBrewingTime(Math.max(1, b.brewingTime() - warp));

    if (M.r(1D / (333D / getInterval()))) {
      Loc hl = b.block().location().add(0.5D, 0.9D, 0.5D);
      FxEmitter emitter = fx(hl, FxPriority.AMBIENT);
      emitter.sound(Sounds.BLOCK_FIRE_AMBIENT, 1.0F, 1.0F + RNG.r.f(0.3F, 0.6F));
      if (f > 0) {
        emitter.particle(Particles.SMALL_FLAME, (int) f, 0, 0.1D, 0, 0.25D, 0.01D)
            .sound(Sounds.ITEM_FIRECHARGE_USE, 0.2F, 1.4F);
      }
      if (l > 0) {
        emitter.particle(Particles.LAVA, (int) l, 0, 0.1D, 0, 0.25D, 0.0D)
            .particle(Particles.SMOKE, (int) l, 0, 0.2D, 0, 0.2D, 0.01D)
            .sound(Sounds.BLOCK_LAVA_POP, 0.3F, 0.8F);
      }
    }
  }

  private static WorldDataPort worldData() {
    return AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE);
  }

  @ConfigDescription("Brewing stands work faster when surrounded by fire or lava.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Multiplier Factor for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double multiplierFactor = 1.33;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fireMultiplier = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lava Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lavaMultiplier = 0.69;

    public Config() {
      baseCost = 3;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
