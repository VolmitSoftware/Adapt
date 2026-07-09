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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class TamingPackLeaderAura extends SimpleAdaptation<TamingPackLeaderAura.Config> {
  private final Cooldowns ringCd = cooldowns();
  private int ownerCursor = 0;

  public TamingPackLeaderAura() {
    super("tame-pack-leader-aura");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.pack_leader_aura");
    setIcon(Material.BONE);
    setInterval(30);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_taming_pack_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_pack_72k", "taming.pack-leader.buffed-ticks", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore1"));
    v.addLore(C.GREEN + "+ " + (1 + getAmplifier(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore2"));
  }

  @Override
  public void onTick() {
    List<OwnerAuraState> owners = collectOwners();
    if (owners.isEmpty()) {
      return;
    }

    List<OwnerAuraState> batch = selectBatch(owners);
    if (J.isFoliaThreading()) {
      for (OwnerAuraState state : batch) {
        J.runEntity(state.owner(), () -> applyAura(state));
      }
      return;
    }

    if (!J.isPrimaryThread()) {
      J.s(this::onTick);
      return;
    }

    for (OwnerAuraState state : batch) {
      applyAura(state);
    }
  }

  private List<OwnerAuraState> collectOwners() {
    List<OwnerAuraState> owners = new ArrayList<>();
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player owner = adaptPlayer.getPlayer();
      int level = getActiveLevel(owner);
      if (level <= 0) {
        continue;
      }

      double radius = getRadius(level);
      owners.add(new OwnerAuraState(adaptPlayer, owner, radius, radius * radius, getAmplifier(level)));
    }

    return owners;
  }

  private List<OwnerAuraState> selectBatch(List<OwnerAuraState> owners) {
    int size = owners.size();
    int limit = Math.max(1, Math.min(size, getConfig().maxOwnersPerPass));
    int start = Math.floorMod(ownerCursor, size);
    List<OwnerAuraState> batch = new ArrayList<>(limit);
    for (int i = 0; i < limit; i++) {
      int index = (start + i) % size;
      batch.add(owners.get(index));
    }
    ownerCursor = (start + limit) % size;
    return batch;
  }

  private void applyAura(OwnerAuraState state) {
    Player owner = state.owner();
    if (owner == null || !owner.isOnline()) {
      return;
    }

    Location ownerLocation = owner.getLocation();
    boolean buffedAny = false;
    for (Entity nearby : owner.getNearbyEntities(state.radius(), state.radius(), state.radius())) {
      if (!(nearby instanceof Tameable tameable) || !tameable.isTamed()) {
        continue;
      }
      if (!(tameable.getOwner() instanceof Player petOwner) || !petOwner.getUniqueId().equals(owner.getUniqueId())) {
        continue;
      }
      if (ownerLocation.distanceSquared(tameable.getLocation()) > state.radiusSquared()) {
        continue;
      }

      tameable.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, getConfig().effectTicks, state.amplifier(), false, false));
      tameable.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getConfig().effectTicks, state.amplifier(), false, false));
      state.ownerData().getData().addStat("taming.pack-leader.buffed-ticks", 1);
      buffedAny = true;
    }

    if (buffedAny && ringCd.isReady(owner.getUniqueId(), 1000)) {
      ringCd.mark(owner.getUniqueId());
      fx(ownerLocation, FxPriority.AMBIENT)
          .dustRing(Color.fromRGB(0xE8E0C8), 1.8D + (state.amplifier() * 0.4D), 8, 1.0F);
    }
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
  }

  private record OwnerAuraState(AdaptPlayer ownerData, Player owner,
                                double radius, double radiusSquared,
                                int amplifier) {
  }

  @ConfigDescription("Nearby tamed companions gain speed and regeneration near their owner.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int effectTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum owners processed per aura pass.", impact = "Lower values reduce burst workload but spread updates across more passes.")
    int maxOwnersPerPass = 120;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      initialCost = 3;
    }
  }
}
