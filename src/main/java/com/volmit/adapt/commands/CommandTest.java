package com.volmit.adapt.commands;

import com.volmit.adapt.util.Command;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Material;

public class CommandTest extends MortarCommand {
    @Command
    private CommandTestParticle particle = new CommandTestParticle();
    @Command
    private CommandTestSound sound = new CommandTestSound();

    public CommandTest() {
        super("test", "t");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        printHelp(sender);
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
