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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RangedWebBomb extends SimpleAdaptation<RangedWebBomb.Config> {
  private static final Id COBWEB = Id.minecraft("cobweb");
  private final Map<UUID, UUID> activeSnowballs = new ConcurrentHashMap<>();
  private final RangedWebs webs = new RangedWebs(this);

  public RangedWebBomb() {
    super("ranged-webshot");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.web_shot");
    setIcon(COBWEB);
    setInterval(4900);
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("ranged-web-bomb"),
        List.of(
            "III",
            "ISI",
            "III"),
        Map.of('I', Ingredient.of(COBWEB), 'S', Ingredient.of(Id.minecraft("snowball"))),
        BoundSnowBall.io.withData(new BoundSnowBall.Data(null))));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cobweb"))
        .key("challenge_ranged_web_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_web_200", "ranged.web-bomb.mobs-trapped", 200, 300);
  }

  @Override
  protected void onRuntimeActivated() {
    webs.queueLoadedChunkRecovery();
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent event) {
    if (event.action().isRightClick()
        && event.item() != null && BoundSnowBall.isBindableItem(event.item())
        && preferenceEnabled(event.player(), RangedPreferences.SNEAK) && !event.player().isSneaking()) {
      event.setCancelled(true);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK);
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Override
  public int getLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getLevel(p), (Adaptation<?> upgrade) -> upgrade.getLevel(p));
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RangedMessages.WEB_SHOT_LORE1));
    statLore(v, C.YELLOW, "+ ", level, 2);
  }


  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileHitEvent e) {
    ProjectileRef snowball = e.projectile();
    if (!snowball.type().equals(RangedProjectiles.SNOWBALL)) {
      return;
    }
    UUID shooterId = activeSnowballs.remove(snowball.uuid());
    if (shooterId == null) {
      return;
    }
    WebImpact impact = captureImpact(e);
    snowball.remove();
    if (impact == null) {
      return;
    }
    PlayerRef p = AdaptCore.platform().host().player(shooterId);
    if (p == null) {
      return;
    }
    Tasks.runEntity(p, () -> authorizeImpact(p, impact));
  }

  private void authorizeImpact(PlayerRef p, WebImpact impact) {
    if (!p.isOnline()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    List<RangedWebs.WebPlacement> placements = RangedWebs.placementCandidates(impact.world(), impact.x(), impact.y(), impact.z());
    if (Tasks.isFoliaThreading()) {
      for (RangedWebs.WebPlacement placement : placements) {
        if (!Tasks.isOwnedByCurrentRegion(placement.location())) {
          return;
        }
      }
    }

    Loc center = impact.center();
    Tasks.runAt(center, () -> showImpactFx(center, impact.hitEntity()));

    long durationTicks = Math.max(1L, (long) level * 20L);
    long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
    AtomicBoolean trapCredited = new AtomicBoolean();
    for (RangedWebs.WebPlacement placement : placements) {
      webs.place(p, placement, expiresAt, () -> creditTrap(p, impact.hitEntity(), trapCredited));
    }

    Tasks.runAt(center, () -> fx(center, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 6, 0.3D)
        .sound(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.7F, 1.0F),
        RangedWebs.delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private WebImpact captureImpact(ProjectileHitEvent event) {
    Loc location;
    boolean hitEntity = event.hitEntity() != null;
    if (hitEntity) {
      location = event.hitEntity().location();
    } else if (event.hitBlock() != null) {
      location = event.hitBlock().location();
    } else {
      location = event.projectile().location();
    }
    WorldRef world = location.world();
    return world == null
        ? null
        : new WebImpact(world, location.blockX(), location.blockY() + 1, location.blockZ(), hitEntity);
  }

  private void showImpactFx(Loc center, boolean hitEntity) {
    FxPresets.chargeRing(this, center, 8);
    fx(center, FxPriority.GAMEPLAY)
        .burst(Particles.WHITE_ASH, 12, 0.4D)
        .burst(Particles.CLOUD, 6, 0.3D)
        .chord(Sounds.BLOCK_WOOL_PLACE, 0.9F, 0.7F, Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.7F, 0.9F);
    if (hitEntity) {
      fx(center, FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0.5D, 0, 0.3D, 0.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.6D, 0, 0.35D, 0.0D)
          .sound(Sounds.BLOCK_WOOL_PLACE, 0.5F, 1.3F);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef snowball = e.projectile();
    if (snowball.shooter() instanceof PlayerRef p && snowball.type().equals(RangedProjectiles.SNOWBALL) && hasActiveAdaptation(p)) {
      if (preferenceEnabled(p, RangedPreferences.SNEAK) && !p.isSneaking()) {
        return;
      }
      if (BoundSnowBall.isBindableItem(snowball.item())) {
        activeSnowballs.put(snowball.uuid(), p.uuid());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (e.entity().type().equals(RangedProjectiles.SNOWBALL)) {
      activeSnowballs.remove(e.entity().uuid());
    }
  }

  private void creditTrap(PlayerRef player, boolean hitEntity, AtomicBoolean trapCredited) {
    if (hitEntity && trapCredited.compareAndSet(false, true)
        && !Tasks.runEntity(player, () -> addStat(player, "ranged.web-bomb.mobs-trapped", 1))) {
      trapCredited.set(false);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(ChunkEvent event) {
    webs.onChunk(event);
  }

  //prevent piston from moving blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockPistonEvent e) {
    webs.onPiston(e);
  }

  //prevent TNT from destroying blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockExplodeEvent e) {
    webs.onExplode(e);
  }

  //prevent block from being destroyed // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    webs.onBreak(e);
  }

  //prevent Entities from destroying blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityExplodeEvent e) {
    webs.onExplode(e);
  }

  @Override
  public void unregister() {
    super.unregister();
    activeSnowballs.clear();
    webs.clear();
  }

  private record WebImpact(WorldRef world, int x, int y, int z, boolean hitEntity) {
    private Loc center() {
      return new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }
  }

  @ConfigDescription("Throw a crafted web snare to trap targets in cobwebs.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
