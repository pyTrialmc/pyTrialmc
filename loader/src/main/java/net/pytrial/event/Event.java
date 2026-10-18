package net.pytrial.event;

public interface Event {

    interface Cancellable extends Event {
        boolean isCancelled();

        void setCancelled(boolean cancelled);
    }
}
