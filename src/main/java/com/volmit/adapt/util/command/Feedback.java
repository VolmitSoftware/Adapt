package com.volmit.adapt.util.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.C;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class Feedback {
    @Singular
    private List<SoundFeedback> sounds;
    @Singular
    private List<TextComponent> messages;

    public void send(CommandSender serverOrPlayer) {
        if (serverOrPlayer instanceof Player p) {
            for (SoundFeedback i : sounds) {
                i.play(p);
            }
        }

        for (TextComponent i : messages) {
            //TODO: we should replace all legacy message to MiniMessage format
            //Suggestion: configurable prefix
            Component prefix = MiniMessage.miniMessage().deserialize("<gray>[<dark_red>Adapt</dark_red>]:</gray> ");
            //String prefix =  C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]:" ;
            Adapt.audiences.sender(serverOrPlayer).sendMessage(prefix.append(i));
        }
    }
}
