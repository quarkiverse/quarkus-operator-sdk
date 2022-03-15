package io.halkyon;

public class ExposedAppStatus {

    private String host;
    private String message;

    public ExposedAppStatus() {
    }

    public ExposedAppStatus(String message, String hostname) {
        this.message = message;
        this.host = hostname;
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
}
