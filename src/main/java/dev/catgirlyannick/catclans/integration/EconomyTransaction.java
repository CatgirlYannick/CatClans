package dev.catgirlyannick.catclans.integration;

public record EconomyTransaction(boolean successful, String errorMessage) {

    public static EconomyTransaction success() {
        return new EconomyTransaction(true, "");
    }

    public static EconomyTransaction failure(String errorMessage) {
        return new EconomyTransaction(false, errorMessage == null ? "" : errorMessage);
    }
}
