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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.UUID;

import java.util.concurrent.ThreadLocalRandom;
public class BrewingSuperHeated extends SimpleAdaptation<BrewingSuperHeated.Config> {
  public static final Id ID = Id.adapt("brewing/super_heated");
  private final BrewingHeat heat = new BrewingHeat(this, BrewingSuperHeated::heatAmbience);

  public BrewingSuperHeated() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lava_bucket"));
    setInterval(253);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_powder"))
        .key("challenge_brewing_super_heated_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("magma_cream"))
            .key("challenge_brewing_super_heated_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewing_super_heated_100", "brewing.super-heated.brews-accelerated", 100, 300);
    registerMilestone("challenge_brewing_super_heated_2500", "brewing.super-heated.brews-accelerated", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getFireBoost(getLevelPercent(level)), 0), 1);
    statLore(v, Format.pc(getLavaBoost(getLevelPercent(level)), 0), 2);
  }

  public double getLavaBoost(double factor) {
    return (getConfig().lavaMultiplier) * (getConfig().multiplierFactor * factor);
  }

  public double getFireBoost(double factor) {
    return (getConfig().fireMultiplier) * (getConfig().multiplierFactor * factor);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryMoveItemEvent e) {
    heat.watch(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    BlockRef block = e.block();
    if (heat.watches(block)) {
      UUID owner = BlockOwners.owner(block).orElse(null);
      if (owner != null && ownerLevel(owner) > 0) {
        AdaptApi.players().find(owner).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.super-heated.brews-accelerated", 1));
        Loc loc = block.location().add(0.5D, 0.6D, 0.5D);
        fx(loc, FxPriority.TRANSITION)
            .particle(Particles.LAVA, 8, 0, 0.2D, 0, 0.4D, 0.0D)
            .particle(Particles.FLAME, 6, 0, 0.2D, 0, 0.35D, 0.02D)
            .particle(Particles.CAMPFIRE_COSY_SMOKE, 2, 0, 0.4D, 0, 0.2D, 0.02D)
            .chord(Sounds.ENTITY_BLAZE_SHOOT, 0.4F, 1.3F, Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.6F);
      }
    }
    heat.keepIfBrewing(block);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    heat.watch(e);
  }

  @Override
  public boolean hasTickDemand() {
    return !heat.isIdle();
  }

  @Override
  public void onTick() {
    heat.tick((UUID owner) -> ownerLevel(owner),
        (int level, double fire, double lava) -> BrewingHeat.computeWarpTicks(getInterval(),
            getFireBoost(getLevelPercent(level)), getLavaBoost(getLevelPercent(level)), fire, lava));
  }

  /** The crackle a heated stand makes while fire or lava speeds it up. */
  public static void heatAmbience(SimpleAdaptation<?> owner, Loc location, double fire, double lava) {
    FxEmitter emitter = Fx.now(owner, location, FxPriority.AMBIENT);
    emitter.sound(Sounds.BLOCK_FIRE_AMBIENT, 1.0F, 1.0F + (0.3F + ThreadLocalRandom.current().nextFloat() * (0.6F - 0.3F)));
    if (fire > 0) {
      emitter.particle(Particles.SMALL_FLAME, (int) fire, 0, 0.1D, 0, 0.25D, 0.01D)
          .sound(Sounds.ITEM_FIRECHARGE_USE, 0.2F, 1.4F);
    }
    if (lava > 0) {
      emitter.particle(Particles.LAVA, (int) lava, 0, 0.1D, 0, 0.25D, 0.0D)
          .particle(Particles.SMOKE, (int) lava, 0, 0.2D, 0, 0.2D, 0.01D)
          .sound(Sounds.BLOCK_LAVA_POP, 0.3F, 0.8F);
    }
  }

  @ConfigDescription("Brewing stands work faster when surrounded by fire or lava.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Multiplier Factor for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double multiplierFactor = 1.33;
    @ConfigDoc(value = "Controls Fire Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fireMultiplier = 0.14;
    @ConfigDoc(value = "Controls Lava Multiplier for the Brewing Super Heated adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lavaMultiplier = 0.69;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
