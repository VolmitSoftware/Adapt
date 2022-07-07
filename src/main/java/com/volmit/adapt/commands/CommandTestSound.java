package com.volmit.adapt.commands;

import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Sound;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandTestSound extends MortarCommand {
    public CommandTestSound() {
        super("sound", "s");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        sender.player().playSound(sender.player(), Sound.valueOf(args[0])
            , Float.parseFloat(args.length > 1 ? args[1] : "1")
            , Float.parseFloat(args.length > 2 ? args[2] : "1"));
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
        if(args.length < 2) {
            String query = args.length == 1 ? args[0] : null;
            list.addAll(Arrays.stream(Sound.values()).filter(i -> query != null ? i.name().contains(query.toUpperCase(Locale.ROOT)) : true).map(i -> i.name()).collect(Collectors.toList()));
        }
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
