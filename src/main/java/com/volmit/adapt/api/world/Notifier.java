package com.volmit.adapt.api.world;

import com.volmit.adapt.api.notification.Notification;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.KList;
import org.bukkit.entity.Player;

public class Notifier extends TickedObject
{
    private int busyTicks;
    private int delayTicks;
    private KList<Notification> queue;
    private final Player target;

    public Notifier(Player target)
    {
        super("notifications", target.getUniqueId().toString() + "-notify", 97);
        queue = new KList<>();
        this.target = target;
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
