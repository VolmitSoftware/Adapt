package art.arcane.adapt.mod.platform;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Hand and click position of an entity interaction packet. Before 26.x one packet type carries attacks, plain
 * interactions and interactions at a position; an attack reads as no hand.
 */
public final class ModInteractPacket implements ServerboundInteractPacket.Handler {
    private InteractionHand hand;
    private Vec3 location;

    private ModInteractPacket() {
    }

    public static ModInteractPacket read(ServerboundInteractPacket packet) {
        ModInteractPacket read = new ModInteractPacket();
        packet.dispatch(read);
        return read;
    }

    /** The interacting hand, or null for an attack. */
    public InteractionHand hand() {
        return hand;
    }

    /** The clicked point relative to the entity, or null when the packet carries none. */
    public Vec3 location() {
        return location;
    }

    @Override
    public void onInteraction(InteractionHand interactionHand) {
        hand = interactionHand;
    }

    @Override
    public void onInteraction(InteractionHand interactionHand, Vec3 clicked) {
        hand = interactionHand;
        location = clicked;
    }

    @Override
    public void onAttack() {
    }
}
