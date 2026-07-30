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
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Entity;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Notifier extends TickedObject {
  private static final long ACTIVE_INTERVAL_MS = 97L;
  private static final long IDLE_INTERVAL_MS = Long.MAX_VALUE;
  private static final int MAX_PENDING_NOTIFICATIONS = 64;

  private final Queue<Notification> queue;
  private final Object queueLock;
  private final AdaptPlayer target;
  private final KMap<String, Long> lastSkills;
  private final KMap<String, Double> lastSkillValues;
  private final AtomicBoolean xpDirty;
  private int busyTicks;
  private int delayTicks;
  private long lastInstance;
  private volatile String latestXpSkill;

  public Notifier(AdaptPlayer target) {
    super("notifications", target.getPlayer().getUniqueId() + "-notify", IDLE_INTERVAL_MS);
    queue = new ConcurrentLinkedQueue<>();
    queueLock = new Object();
    lastSkills = new KMap<>();
    lastSkillValues = new KMap<>();
    xpDirty = new AtomicBoolean();
    busyTicks = 0;
    delayTicks = 0;
    this.target = target;
    lastInstance = 0;
  }

  public void notifyXP(String line, double value) {
    try {
      activate();
      if (!lastSkills.containsKey(line)) {
        lastSkillValues.put(line, 0d);
      }

      lastSkills.put(line, M.ms());
      lastSkillValues.put(line, lastSkillValues.get(line) + value);
      lastInstance = M.ms();
      latestXpSkill = line;
      xpDirty.set(true);

      while (lastSkills.size() > 5) {
        String s = lastSkills.sortKNumber().reverse().get(0);
        lastSkills.remove(s);
        lastSkillValues.remove(s);
      }
    } catch (Throwable e) {
      Adapt.verbose("Failed to notify xp: " + e.getMessage());
    }
  }

  public void queue(Notification... f) {
    if (f == null || f.length == 0) {
      return;
    }

    boolean queued = false;
    synchronized (queueLock) {
      for (Notification notification : f) {
        if (notification == null) {
          continue;
        }

        String group = notification.getGroup();
        if (group != null && !group.isBlank() && !Notification.DEFAULT_GROUP.equals(group)) {
          queue.removeIf(existing -> group.equals(existing.getGroup()));
        }
        while (queue.size() >= MAX_PENDING_NOTIFICATIONS) {
          queue.poll();
        }
        queue.add(notification);
        queued = true;
      }
    }
    if (queued) {
      activate();
    }
  }

  public boolean isBusy() {
    return busyTicks > 1 || !queue.isEmpty();
  }

  @Override
  public void onTick() {
    if (queue.isEmpty() && busyTicks <= 0 && delayTicks <= 0 && lastSkills.isEmpty()) {
      setInterval(IDLE_INTERVAL_MS);
      return;
    }

    cleanupSkills();
    flushXpNotification();

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

    Notification n;
    synchronized (queueLock) {
      n = queue.poll();
    }

    if (n == null) {
      parkIfIdle();
      return;
    }

    delayTicks += (n.getTotalDuration() / 50D) + 1;
    Adapt.verbose("Playing Notification " + n + " --> " + System.identityHashCode(this));
    n.play(target);
  }

  private void parkIfIdle() {
    if (!queue.isEmpty() || busyTicks > 0 || delayTicks > 0 || xpDirty.get()) {
      return;
    }
    if (lastSkills.isEmpty()) {
      setInterval(IDLE_INTERVAL_MS);
      return;
    }

    long untilExpiry = (lastInstance + 3_101L) - M.ms();
    setInterval(Math.max(ACTIVE_INTERVAL_MS, untilExpiry));
  }

  private void activate() {
    setInterval(ACTIVE_INTERVAL_MS);
    if (!isBursting()) {
      retick();
    }
  }

  int pendingNotifications() {
    return queue.size();
  }

  private void flushXpNotification() {
    if (!xpDirty.compareAndSet(true, false) || lastSkills.isEmpty()) {
      return;
    }

    String latest = latestXpSkill;
    StringBuilder title = new StringBuilder();
    KList<String> orderedSkills = lastSkills.sortKNumber().reverse();
    for (String skillName : orderedSkills) {
      Skill<?> skill = getServer().getSkillRegistry().getSkill(skillName);
      if (skill == null) {
        continue;
      }
      title.append(skillName.equals(latest) ? skill.getDisplayName() : skill.getShortName())
          .append(C.RESET).append(C.GRAY)
          .append(" +").append(C.WHITE)
          .append(skillName.equals(latest) ? C.UNDERLINE : "")
          .append(Form.f(lastSkillValues.get(skillName).intValue()))
          .append(C.RESET).append(C.GRAY)
          .append("XP ");
    }
    if (title.isEmpty()) {
      return;
    }

    target.getActionBarNotifier().queue(ActionBarNotification.builder()
        .duration(0)
        .maxTTL(M.ms() + 100)
        .title(title.toString())
        .group("xp")
        .build());
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
    if (lastSkills.isEmpty()) {
      AdaptHud.clearXp(target.getPlayer());
    }
  }

  @Override
  protected Entity getTickOwner() {
    return target.getPlayer();
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
