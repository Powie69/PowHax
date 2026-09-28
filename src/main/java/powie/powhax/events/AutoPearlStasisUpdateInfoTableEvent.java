package powie.powhax.events;

import org.jetbrains.annotations.Nullable;

public class AutoPearlStasisUpdateInfoTableEvent {
    public String username, connection;
    public Boolean pearlStatus;

    public AutoPearlStasisUpdateInfoTableEvent(@Nullable String username, @Nullable String connection, @Nullable Boolean pearlStatus) {
        this.username = username;
        this.connection = connection;
        this.pearlStatus = pearlStatus;
    }

    public AutoPearlStasisUpdateInfoTableEvent(String username, String connection) {
        this(username, connection, null);
    }

    public AutoPearlStasisUpdateInfoTableEvent(boolean pearlStatus) {
        this(null, null, pearlStatus);
    }

    public AutoPearlStasisUpdateInfoTableEvent() {
        this(null, null, null);
    }
}
