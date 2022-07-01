package com.volmit.adapt.commands;

import com.volmit.adapt.content.item.KnowledgeOrb;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

public class CommandItemKnowledgeOrb extends MortarCommand {
    public CommandItemKnowledgeOrb() {
        super("knowledge", "k");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        sender.player().getInventory().addItem(KnowledgeOrb.with(args[0], Integer.parseInt(args[1])));
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "<skill> <knowledge>";
    }
}
