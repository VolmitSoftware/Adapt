package com.volmit.adapt.api.protection;

import com.volmit.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public abstract class Protector {
    public abstract boolean canBuild(Player player, Location location, Adaptation<?> adaptation);
}
