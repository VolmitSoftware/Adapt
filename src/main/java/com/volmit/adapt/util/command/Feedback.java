package com.volmit.adapt.util.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.VolmitSender;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
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

        Component prefix = Component.text("[").color(NamedTextColor.GRAY)
                .append(Component.text("Adapt").color(NamedTextColor.DARK_RED))
                .append(Component.text("] "));
        for (TextComponent i : messages) {
            Adapt.audiences.sender(serverOrPlayer).sendMessage(Component.text()
                    .append(prefix)
                    .append(i)
                    .build());
        }
    }

    public void send(VolmitSender sender) {
        send(sender.getS());
    }
}
