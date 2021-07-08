package com.volmit.adapt.api.xp;

import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.M;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import xyz.xenondevs.particle.ParticleEffect;

@Data
public class SpatialXP {
    private Location location;
    private double radius;
    private Skill skill;
    private double xp;
    private long ms;

    public SpatialXP(Location l, Skill s, double xp, double radius, long duration)
    {
        this.location = l;
        this.skill = s;
        this.xp = xp;
        this.ms = M.ms() + duration;
        this.radius = radius;
    }
}
