package io.halkyon;

@SuppressWarnings("unused")
public class ExposedAppStatus {

    private String host;
    private String message;

    private long waitTime = System.currentTimeMillis();
    private boolean ready = false;

    public ExposedAppStatus() {
        message = "processing";
    }

    public ExposedAppStatus(String hostname) {
        this.message = "exposed";
        this.host = hostname;
        ready = true;
        waitTime = System.currentTimeMillis() - waitTime;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getWaitTime() {
        if (!ready) {
            waitTime = System.currentTimeMillis() - waitTime;
        }
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }
}
