package dev.catgirlyannick.catclans.model;

import java.util.Optional;

public record DiplomacyView(
        boolean allied,
        Optional<ClanWar> activeWar,
        Optional<DiplomacyRequest> incomingAllyRequest,
        Optional<DiplomacyRequest> outgoingAllyRequest,
        Optional<DiplomacyRequest> incomingWarRequest,
        Optional<DiplomacyRequest> outgoingWarRequest
) {

    public static DiplomacyView empty() {
        return new DiplomacyView(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
