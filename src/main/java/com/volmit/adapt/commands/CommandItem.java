package com.volmit.adapt.commands;

import com.volmit.adapt.util.Command;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

public class CommandItem extends MortarCommand {
    @Command
    private CommandItemKnowledgeOrb skillOrb = new CommandItemKnowledgeOrb();
    @Command
    private CommandItemExperienceOrb xpOrb = new CommandItemExperienceOrb();

    public CommandItem() {
        super("item", "i");
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
