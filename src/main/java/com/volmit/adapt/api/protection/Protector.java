package com.volmit.adapt.api.protection;

import com.volmit.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Protector {
    boolean canBuild(Player player, Location location, Adaptation<?> adaptation);

    String getName();

    boolean isEnabledByDefault();
}
