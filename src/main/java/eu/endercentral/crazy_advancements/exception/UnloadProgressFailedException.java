package eu.endercentral.crazy_advancements.exception;

import java.util.UUID;

public class UnloadProgressFailedException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = 5052062325162108824L;

    private final UUID uuid;
    private String message = "Unable to unload Progress for online Players!";

    public UnloadProgressFailedException(UUID uuid) {
        this.uuid = uuid;
    }

    public UnloadProgressFailedException(UUID uuid, String message) {
        this.uuid = uuid;
        this.message = message;
    }

    @Override
    public String getMessage() {
        return "Failed to unload Advancement Progress for Player with UUID " + uuid + ": " + message;
    }


}