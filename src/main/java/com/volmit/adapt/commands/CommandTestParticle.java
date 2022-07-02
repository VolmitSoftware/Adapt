package com.volmit.adapt.commands;

import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandTestParticle extends MortarCommand {
    public CommandTestParticle() {
        super("particle", "p");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        J.sr(() -> J.attempt(() -> sender.player().spawnParticle(Particle.valueOf(args[0]),
            sender.player().getLocation().clone().add(sender.player().getEyeLocation().getDirection().clone().multiply(7)), 3)), 0, 20);
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
        if(args.length < 2) {
            String query = args.length == 1 ? args[0] : null;
            list.addAll(Arrays.stream(Particle.values()).filter(i -> query != null ? i.name().contains(query.toUpperCase(Locale.ROOT)) : true).map(i -> i.name()).collect(Collectors.toList()));
        }
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
