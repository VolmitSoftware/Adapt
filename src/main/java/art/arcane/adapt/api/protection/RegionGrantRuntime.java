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

package art.arcane.adapt.api.protection;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RegionGrantRuntime {
  private static volatile Catalog catalog = Catalog.EMPTY;

  private RegionGrantRuntime() {
  }

  public static void refresh(AdaptPlayer adaptPlayer, Location location) {
    if (adaptPlayer == null) {
      return;
    }

    PlayerData data = adaptPlayer.getData();
    if (data == null) {
      return;
    }

    RegionPolicy policy = RegionPolicyService.resolve(adaptPlayer.getPlayer(), location);
    int previousBonus = data.getRegionPowerBonus();
    data.setRegionPowerBonus(policy.powerBonus());
    if (policy.powerBonus() < previousBonus && data.getUsedPower() > data.getMaxPower()) {
      data.pruneAdaptationsForPowerBudget();
    }
    Set<String> desired = desiredAdaptations(policy);
    if (desired.isEmpty() && data.getRegionGrantedCount() == 0) {
      return;
    }

    int active = revokeUnjustified(data, desired);
    active += grantMissing(data, desired);
    data.setRegionGrantedCount(active);
  }

  private static Set<String> desiredAdaptations(RegionPolicy policy) {
    if (!policy.grantsAnyAdaptation()) {
      return Set.of();
    }

    if (policy.unlocksEverything()) {
      return catalog().ids();
    }

    return policy.unlockedAdaptations();
  }

  private static int revokeUnjustified(PlayerData data, Set<String> desired) {
    int active = 0;
    for (PlayerSkillLine line : data.getSkillLines().v()) {
      if (line == null) {
        continue;
      }

      for (String adaptationName : line.getAdaptations().k()) {
        PlayerAdaptation granted = line.getAdaptations().get(adaptationName);
        if (granted == null || !granted.isRegionGranted()) {
          continue;
        }

        if (desired.contains(adaptationName.toLowerCase(Locale.ROOT))) {
          active++;
          continue;
        }

        revoke(line, adaptationName);
      }
    }
    return active;
  }

  private static int grantMissing(PlayerData data, Set<String> desired) {
    int granted = 0;
    Catalog current = catalog();
    for (String adaptationName : desired) {
      Adaptation<?> adaptation = current.find(adaptationName);
      if (adaptation == null || !adaptation.isEnabled()) {
        continue;
      }

      Skill<?> skill = adaptation.getSkill();
      if (skill == null || !skill.isEnabled()) {
        continue;
      }

      PlayerSkillLine line = data.getSkillLine(skill.getName());
      if (line == null || line.getAdaptationLevel(adaptation.getName()) > 0) {
        continue;
      }

      line.setAdaptation(adaptation, 1);
      PlayerAdaptation created = line.getAdaptation(adaptation.getName());
      if (created == null) {
        continue;
      }

      created.setRegionGranted(true);
      granted++;
    }
    return granted;
  }

  private static void revoke(PlayerSkillLine line, String adaptationName) {
    Adaptation<?> adaptation = catalog().find(adaptationName);
    if (adaptation == null) {
      line.getAdaptations().remove(adaptationName);
      return;
    }

    line.setAdaptation(adaptation, 0);
  }

  private static Catalog catalog() {
    SkillRegistry registry = registry();
    if (registry == null) {
      return Catalog.EMPTY;
    }

    long revision = registry.getCatalogRevision();
    Catalog current = catalog;
    if (current.revision() == revision) {
      return current;
    }

    Catalog rebuilt = build(registry, revision);
    catalog = rebuilt;
    return rebuilt;
  }

  private static Catalog build(SkillRegistry registry, long revision) {
    Map<String, Adaptation<?>> adaptations = new HashMap<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (skill == null) {
        continue;
      }

      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation == null || adaptation.getName() == null || adaptation.getName().isBlank()) {
          continue;
        }

        adaptations.put(adaptation.getName().toLowerCase(Locale.ROOT), adaptation);
      }
    }

    return new Catalog(revision, Map.copyOf(adaptations), Set.copyOf(adaptations.keySet()));
  }

  private static SkillRegistry registry() {
    Adapt instance = Adapt.instance;
    if (instance == null) {
      return null;
    }

    AdaptServer server = instance.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }

  private record Catalog(long revision, Map<String, Adaptation<?>> adaptations, Set<String> ids) {
    private static final Catalog EMPTY = new Catalog(Long.MIN_VALUE, Map.of(), Set.of());

    private Adaptation<?> find(String adaptationName) {
      return adaptationName == null ? null : adaptations.get(adaptationName.toLowerCase(Locale.ROOT));
    }
  }
}
