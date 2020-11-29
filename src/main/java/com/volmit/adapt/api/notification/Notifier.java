package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.notification.Notification;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.M;
import org.bukkit.entity.Player;

public class Notifier extends TickedObject
{
    private int busyTicks;
    private int delayTicks;
    private KList<Notification> queue;
    private final Player target;
    private double lastValue;
    private String lastSkill;
    private long lastInstance;

    public Notifier(Player target)
    {
        super("notifications", target.getUniqueId().toString() + "-notify", 97);
        queue = new KList<>();
        this.target = target;
        lastValue = 0;
        lastSkill = "";
        lastInstance = 0;
    }

    public void notifyXP(String line, double value)
    {
        if(M.ms() - lastInstance > 3100 || !lastSkill.equals(line))
        {
            lastSkill = line;
            lastValue = 0;
        }

        lastValue += value;
        lastInstance = M.ms();

        if(isBusy())
        {
            return;
        }

        Skill sk = getServer().getSkillRegistry().getSkill(line);
        Adapt.actionbar(target, sk.getDisplayName() + C.RESET + C.GRAY + " +" + C.WHITE + C.UNDERLINE + Form.f((int)lastValue) + C.RESET + C.GRAY + "XP");
    }

    public void queue(Notification... f)
    {
        queue.add(f);
    }

    public boolean isBusy()
    {
        return busyTicks > 1 || queue.isNotEmpty();
    }

    @Override
    public void onTick() {
        if(busyTicks > 6)
        {
            busyTicks = 6;
        }

        if(busyTicks-- > 0)
        {
            return;
        }

        if(busyTicks < 0)
        {
            busyTicks = 0;
        }

        delayTicks--;
        if(delayTicks > 0)
        {
            return;
        }

        if(delayTicks < 0)
        {
            delayTicks = 0;
        }

        Notification n = queue.pop();

        if(n == null)
        {
            return;
        }

        delayTicks += (n.getTotalDuration()/50D) + 1;
        n.play(target);
    }
}
