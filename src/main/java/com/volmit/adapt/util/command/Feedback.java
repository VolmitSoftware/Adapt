package com.volmit.adapt.util.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.C;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.TextComponent;
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
            String prefix =  C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]:" ;
            Adapt.audiences.sender(serverOrPlayer).sendMessage(i.content(prefix + " " + i.content()));
        }
    }
}
