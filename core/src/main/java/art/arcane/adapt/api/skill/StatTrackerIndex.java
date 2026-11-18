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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.world.AdaptStatTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StatTrackerIndex {
  private static final StatTrackerIndex EMPTY = new StatTrackerIndex(Map.of(), 0);

  private final Map<String, List<Binding>> bindingsByStat;
  private final int bindingCount;

  private StatTrackerIndex(Map<String, List<Binding>> bindingsByStat, int bindingCount) {
    this.bindingsByStat = bindingsByStat;
    this.bindingCount = bindingCount;
  }

  static StatTrackerIndex empty() {
    return EMPTY;
  }

  static StatTrackerIndex build(Iterable<? extends Skill<?>> skills) {
    if (skills == null) {
      return EMPTY;
    }

    Map<String, List<Binding>> mutableBindings = new LinkedHashMap<>();
    int indexedBindings = 0;
    for (Skill<?> skill : skills) {
      if (skill == null) {
        continue;
      }

      indexedBindings += addTrackers(mutableBindings, skill, null, skill.getStatTrackers());
      List<Adaptation<?>> adaptations = skill.getAdaptations();
      if (adaptations == null) {
        continue;
      }
      for (Adaptation<?> adaptation : adaptations) {
        if (adaptation instanceof SimpleAdaptation<?> simpleAdaptation) {
          indexedBindings += addTrackers(mutableBindings, skill, adaptation, simpleAdaptation.getStatTrackers());
        }
      }
    }

    if (indexedBindings == 0) {
      return EMPTY;
    }

    Map<String, List<Binding>> immutableBindings = new LinkedHashMap<>(mutableBindings.size());
    for (Map.Entry<String, List<Binding>> entry : mutableBindings.entrySet()) {
      List<Binding> bindings = entry.getValue();
      sortSkillGroupsByGoal(bindings);
      immutableBindings.put(entry.getKey(), List.copyOf(bindings));
    }
    return new StatTrackerIndex(Collections.unmodifiableMap(immutableBindings), indexedBindings);
  }

  List<Binding> bindingsFor(String stat) {
    List<Binding> bindings = bindingsByStat.get(stat);
    return bindings == null ? List.of() : bindings;
  }

  Set<String> trackedStats() {
    return bindingsByStat.keySet();
  }

  int bindingCount() {
    return bindingCount;
  }

  int trackedStatCount() {
    return bindingsByStat.size();
  }

  private static int addTrackers(Map<String, List<Binding>> bindingsByStat, Skill<?> skill,
                                 Adaptation<?> adaptation, Iterable<AdaptStatTracker> trackers) {
    if (trackers == null) {
      return 0;
    }

    int added = 0;
    for (AdaptStatTracker tracker : trackers) {
      if (tracker == null || tracker.getStat() == null || tracker.getStat().isBlank()
          || tracker.getAdvancement() == null || tracker.getAdvancement().isBlank()) {
        continue;
      }

      bindingsByStat.computeIfAbsent(tracker.getStat(), ignored -> new ArrayList<>())
          .add(new Binding(skill, adaptation, tracker));
      added++;
    }
    return added;
  }

  private static void sortSkillGroupsByGoal(List<Binding> bindings) {
    int groupStart = 0;
    while (groupStart < bindings.size()) {
      Skill<?> skill = bindings.get(groupStart).skill();
      int groupEnd = groupStart + 1;
      while (groupEnd < bindings.size() && bindings.get(groupEnd).skill() == skill) {
        groupEnd++;
      }

      bindings.subList(groupStart, groupEnd)
          .sort(Comparator.comparingDouble(binding -> binding.tracker().getGoal()));
      groupStart = groupEnd;
    }
  }

  record Binding(Skill<?> skill, Adaptation<?> adaptation, AdaptStatTracker tracker) {
  }
}
