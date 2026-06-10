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

package art.arcane.adapt.api.notification;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import lombok.Data;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Data
public class Notifier extends TickedObject {
  private final Queue<Notification> queue;
  private final AdaptPlayer target;
  private final KMap<String, Long> lastSkills;
  private final KMap<String, Double> lastSkillValues;
  private int busyTicks;
  private int delayTicks;
  private long lastInstance;

  public Notifier(AdaptPlayer target) {
    super("notifications", target.getPlayer().getUniqueId() + "-notify", 97);
    queue = new ConcurrentLinkedQueue<>();
    lastSkills = new KMap<>();
    lastSkillValues = new KMap<>();
    busyTicks = 0;
    delayTicks = 0;
    this.target = target;
    lastInstance = 0;
  }

  public void notifyXP(String line, double value) {
    try {
      if (!lastSkills.containsKey(line)) {
        lastSkillValues.put(line, 0d);
      }

      lastSkills.put(line, M.ms());
      lastSkillValues.put(line, lastSkillValues.get(line) + value);
      lastInstance = M.ms();


      StringBuilder sb = new StringBuilder();

      for (String i : lastSkills.sortKNumber().reverse()) {
        Skill sk = getServer().getSkillRegistry().getSkill(i);
        sb.append(i.equals(line) ? sk.getDisplayName() : sk.getShortName())
            .append(C.RESET).append(C.GRAY)
            .append(" +").append(C.WHITE)
            .append(line.equals(i) ? C.UNDERLINE : "")
            .append(Form.f(lastSkillValues.get(i).intValue()))
            .append(C.RESET).append(C.GRAY)
            .append("XP ");
      }

      while (lastSkills.size() > 5) {
        String s = lastSkills.sortKNumber().reverse().get(0);
        lastSkills.remove(s);
        lastSkillValues.remove(s);
      }

      target.getActionBarNotifier().queue(ActionBarNotification.builder()
          .duration(0)
          .maxTTL(M.ms() + 100)
          .title(sb.toString())
          .group("xp")
          .build());
    } catch (Throwable e) {
      Adapt.verbose("Failed to notify xp: " + e.getMessage());
    }
  }

  public void queue(Notification... f) {
    queue.addAll(Arrays.asList(f));
  }

  public boolean isBusy() {
    return busyTicks > 1 || !queue.isEmpty();
  }

  @Override
  public void onTick() {
    cleanupSkills();

    if (busyTicks > 6) {
      busyTicks = 6;
    }

    if (busyTicks-- > 0) {
      return;
    }

    if (busyTicks < 0) {
      busyTicks = 0;
    }

    delayTicks--;
    if (delayTicks > 0) {
      return;
    }

    if (delayTicks < 0) {
      delayTicks = 0;
    }


    if (!isBusy()) {
      cleanupStackedNotifications();
    }

    Notification n = queue.poll();

    if (n == null) {
      return;
    }

    delayTicks += (n.getTotalDuration() / 50D) + 1;
    Adapt.verbose("Playing Notification " + n + " --> " + System.identityHashCode(this));
    n.play(target);
  }

  private void cleanupStackedNotifications() {

  }

  private void cleanupSkills() {
    if (lastSkills.isEmpty()) {
      return;
    }

    long now = M.ms();
    Iterator<Map.Entry<String, Long>> iterator = lastSkills.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Long> entry = iterator.next();
      long last = entry.getValue();
      if (now - last > 10000 || (now - lastInstance > 3100 && now - last > 3100)) {
        iterator.remove();
        lastSkillValues.remove(entry.getKey());
      }
    }
  }

  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }
}
