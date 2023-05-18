package com.volmit.adapt.command;


import com.volmit.adapt.command.boost.CommandBoost;
import com.volmit.adapt.command.debug.*;
import com.volmit.adapt.command.gui.CommandGUI;
import com.volmit.adapt.command.item.CommandExperience;
import com.volmit.adapt.command.item.CommandKnowledge;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.base.Command;
import io.github.mqzn.commands.annotations.base.Default;
import io.github.mqzn.commands.annotations.subcommands.SubCommand;
import org.bukkit.command.CommandSender;

@Command(name = "adapt", permission = "adapt.main", aliases = {"ada", "adaptation", "skills"}, description = "The main command for Adapt")
@SubCommand(CommandBoost.class) // Boost
@SubCommand(CommandDebug.class) // Debug
@SubCommand(CommandParticle.class) // Debug
@SubCommand(CommandPAP.class) // Debug
@SubCommand(CommandPSP.class) // Debug
@SubCommand(CommandSound.class) // Debug
@SubCommand(CommandVerbose.class) // Debug
@SubCommand(CommandGUI.class) // GUI
@SubCommand(CommandExperience.class) // GUI
@SubCommand(CommandKnowledge.class) // GUI
public class CommandAdapt {

    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt  -  This command").send(sender);
        FConst.info("/adapt item  -  The Cheat Items Subcommand").send(sender);
        FConst.info("/adapt gui  -  The GUI Subcommand").send(sender);
        FConst.info("/adapt test  -  The developer testing Subcommands").send(sender);
        FConst.info("/adapt verbose  -  The verbose Subcommand").send(sender);

    }

}