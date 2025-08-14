package io.halkyon;

@SuppressWarnings("unused")
public class ExposedAppStatus {

    private String host;
    private String message;

    public ExposedAppStatus() {
        message = "processing";
    }

    public ExposedAppStatus(String hostname, String endpoint) {
        if (hostname == null || endpoint == null) {
            this.message = "reconciled-not-exposed";
        } else {
            this.message = "exposed";
            this.host = endpoint != null && !endpoint.isBlank() ? hostname + '/' + endpoint : hostname;
        }
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
