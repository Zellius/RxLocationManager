package ru.solodovnikov.rxlocationmanager.error;

public class ProviderDisabledException extends RuntimeException {
    private final String provoder;

    public ProviderDisabledException(String provoder) {
        super(String.format("The %s provider is disabled", provoder));
        this.provoder = provoder;
    }

    public String getProvoder() {
        return provoder;
    }
}
