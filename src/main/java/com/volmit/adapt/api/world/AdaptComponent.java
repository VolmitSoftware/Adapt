package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import org.bukkit.entity.Player;

public interface AdaptComponent {
    public default AdaptServer getServer()
    {
        return  Adapt.instance.getAdaptServer();
    }

    public default AdaptPlayer getPlayer(Player p)
    {
        return getServer().getPlayer(p);
    }
}
