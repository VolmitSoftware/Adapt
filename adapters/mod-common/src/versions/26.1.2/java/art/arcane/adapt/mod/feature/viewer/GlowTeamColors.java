package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.platform.viewer.GlowColor;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

final class GlowTeamColors {
    private GlowTeamColors() {
    }

    static void apply(PlayerTeam team, GlowColor color) {
        team.setColor(ChatFormatting.valueOf(color.name()));
    }
}
