package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.PlayerRef;

public interface FixtureSender {
    void send(String text);

    PlayerRef player();

    boolean console();
}
