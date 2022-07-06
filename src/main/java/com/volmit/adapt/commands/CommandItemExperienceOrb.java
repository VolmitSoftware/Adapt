package com.volmit.adapt.commands;

import com.volmit.adapt.content.item.ExperienceOrb;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

import java.util.List;

public class CommandItemExperienceOrb extends MortarCommand {
    public CommandItemExperienceOrb() {
        super("experience", "xp", "x");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        sender.player().getInventory().addItem(ExperienceOrb.with(args[0], Integer.parseInt(args[1])));
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "<skill> <xp>";
    }
}
