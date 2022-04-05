package io.quarkiverse.operatorsdk.samples.pingpong;

public class Status {
    public enum State {
        PROCESSED,
        UNKNOWN
    }

    private Status.State state = Status.State.UNKNOWN;

    public Status() {

    }

    public Status(Status.State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
