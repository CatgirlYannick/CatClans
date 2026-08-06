package dev.catgirlyannick.catclans.gui;

enum ConfirmAction {
    INVITE("invite"),
    KICK("kick"),
    JOIN("join"),
    ACCEPT("accept"),
    DENY("deny"),
    LEAVE("leave"),
    JOIN_MODE_OPEN("join-mode-open"),
    JOIN_MODE_INVITE("join-mode-invite"),
    ALLY_REQUEST("ally-request"),
    WAR_REQUEST("war-request");

    private final String configKey;

    ConfirmAction(String configKey) {
        this.configKey = configKey;
    }

    String configKey() {
        return configKey;
    }
}
