package io.quarkiverse.operatorsdk.it.cdi;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestUUIDBean {

    private final String id = UUID.randomUUID().toString();

    public String uuid() {
        return id;
    }
}
