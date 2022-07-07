package com.volmit.adapt.commands;

import com.volmit.adapt.util.Command;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

import java.util.List;

public class CommandAdapt extends MortarCommand {
    @Command
    private CommandItem item = new CommandItem();
    @Command
    private CommandTest test = new CommandTest();
    public CommandAdapt() {
        super("adapt", "ada", "a");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        printHelp(sender);
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
