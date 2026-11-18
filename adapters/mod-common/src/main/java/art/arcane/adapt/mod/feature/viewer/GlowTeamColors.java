package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.platform.viewer.GlowColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

final class GlowTeamColors {
    private GlowTeamColors() {
    }

    static void apply(PlayerTeam team, GlowColor color) {
        team.setColor(Optional.of(TeamColor.valueOf(color.name())));
    }
}
