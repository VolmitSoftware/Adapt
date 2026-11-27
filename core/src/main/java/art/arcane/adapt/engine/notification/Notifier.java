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

package art.arcane.adapt.engine.notification;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.tick.TickedObject;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.XpEarnCoalescer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Notifier extends TickedObject {
  private static final long ACTIVE_INTERVAL_MS = 97L;
  private static final long IDLE_INTERVAL_MS = Long.MAX_VALUE;
  private static final int MAX_PENDING_NOTIFICATIONS = 64;

  private final Queue<Notification> queue;
  private final Object queueLock;
  private final AdaptPlayer target;
  private final UUID playerId;
  private final Object earnLock;
  private XpEarnCoalescer earned;
  private final KMap<String, Long> lastSkills;
  private final KMap<String, Double> lastSkillValues;
  private final AtomicBoolean xpDirty;
  private int busyTicks;
  private int delayTicks;
  private long lastInstance;
  private volatile String latestXpSkill;

  public Notifier(AdaptPlayer target) {
    super("notifications", target.getPlayer().uuid() + "-notify", IDLE_INTERVAL_MS);
    queue = new ConcurrentLinkedQueue<>();
    queueLock = new Object();
    lastSkills = new KMap<>();
    lastSkillValues = new KMap<>();
    xpDirty = new AtomicBoolean();
    busyTicks = 0;
    delayTicks = 0;
    this.target = target;
    playerId = target.getPlayer().uuid();
    earnLock = new Object();
    lastInstance = 0;
  }

  public void notifyXP(String line, double value) {
    if (ClientHud.capable(playerId, ClientProtocol.HUD)) {
      return;
    }
    try {
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
      activate();
    } catch (Throwable e) {
      AdaptLog.verbose(() -> "Skipped XP notification: "
          + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
  }

  /**
   * Sends the award to a player with the client HUD as it is earned, at most every
   * {@link XpEarnCoalescer#INTERVAL_MILLIS} per skill. Pooled payout is unaffected.
   */
  public void xpEarned(PlayerSkillLine line, double amount) {
    if (!ClientHud.capable(playerId, ClientProtocol.HUD) || !AdaptConfig.get().isActionbarNotifyXp()) {
      return;
    }
    boolean waiting;
    synchronized (earnLock) {
      if (earned == null) {
        earned = new XpEarnCoalescer();
      }
      double ready = earned.earn(line.getLine(), amount, M.ms());
      if (ready > 0D) {
        sendXpEarned(line, ready);
      }
      waiting = earned.waiting();
    }
    if (waiting) {
      activate();
    }
  }

  public void queue(Notification... f) {
    if (f == null || f.length == 0) {
      return;
    }

    boolean queued = false;
    synchronized (queueLock) {
      Set<String> replacementGroups = new HashSet<>();
      for (Notification notification : f) {
        if (notification == null) {
          continue;
        }

        String group = notification.getGroup();
        if (group != null && !group.isBlank() && !Notification.DEFAULT_GROUP.equals(group)) {
          replacementGroups.add(group);
        }
      }
      if (!replacementGroups.isEmpty()) {
        queue.removeIf(existing -> replacementGroups.contains(existing.getGroup()));
      }
      for (Notification notification : f) {
        if (notification == null) {
          continue;
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
    boolean earnWaiting = drainXpEarned();
    if (queue.isEmpty() && busyTicks <= 0 && delayTicks <= 0 && lastSkills.isEmpty() && !earnWaiting) {
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
    n.play(target);
  }

  private void parkIfIdle() {
    if (!queue.isEmpty() || busyTicks > 0 || delayTicks > 0 || xpDirty.get() || xpEarnWaiting()) {
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
    activateRuntime();
    if (!isBursting()) {
      retick();
    }
  }

  int pendingNotifications() {
    return queue.size();
  }

  private boolean drainXpEarned() {
    synchronized (earnLock) {
      if (earned == null) {
        return false;
      }
      earned.drain(M.ms(), (String skillId, double amount) -> {
        PlayerSkillLine line = target.getData().getSkillLineNullable(skillId);
        if (line != null) {
          sendXpEarned(line, amount);
        }
      });
      return earned.waiting();
    }
  }

  private boolean xpEarnWaiting() {
    synchronized (earnLock) {
      return earned != null && earned.waiting();
    }
  }

  private void sendXpEarned(PlayerSkillLine line, double amount) {
    double total = line.getXp() + line.getPooledXp();
    double absoluteLevel = XP.getLevelForXp(total);
    int level = (int) Math.floor(absoluteLevel);
    ClientHud.send(playerId, ClientProtocol.HUD,
        new ServerFrame.XpEarned(line.getLine(), amount, total, level, absoluteLevel - level));
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
        .displayDurationMillis(AdaptConfig.get().getActionbarXpDurationMillis())
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
  }

  @Override
  protected EntityRef getTickOwner() {
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
