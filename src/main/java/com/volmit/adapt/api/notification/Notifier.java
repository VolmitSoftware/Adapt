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

package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.collection.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@EqualsAndHashCode(callSuper = true)
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
        for (String i : lastSkills.k()) {
            if (lastSkills.get(i) == null) { // Shouldn't happen, but just in case I guess.
                return;
            }
            if (M.ms() - lastSkills.get(i) > 10000 || (M.ms() - lastInstance > 3100 && M.ms() - lastSkills.get(i) > 3100)) {
                lastSkills.remove(i);
                lastSkillValues.remove(i);
            }
        }
    }
}
