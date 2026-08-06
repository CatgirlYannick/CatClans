package dev.catgirlyannick.catclans.gui;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GuiNavigationHistory {

    private static final int MAXIMUM_DEPTH = 32;

    private final Map<UUID, ArrayDeque<ClanMenuState>> historyByPlayer = new HashMap<>();
    private final Map<UUID, ClanMenuState> replacementByPlayer = new HashMap<>();

    void opened(UUID playerId, ClanMenuState current, ClanMenuState opened) {
        ClanMenuState replacement = replacementByPlayer.get(playerId);
        if (opened.equals(replacement)) {
            replacementByPlayer.remove(playerId);
            return;
        }
        if (current == null) {
            replacementByPlayer.remove(playerId);
            if (opened.type() == ClanMenuType.MAIN) {
                historyByPlayer.remove(playerId);
            }
            return;
        }
        if (current.equals(opened)) {
            return;
        }
        ArrayDeque<ClanMenuState> history = historyByPlayer.computeIfAbsent(
                playerId,
                ignored -> new ArrayDeque<>()
        );
        if (!current.equals(history.peekLast())) {
            history.addLast(current);
        }
        while (history.size() > MAXIMUM_DEPTH) {
            history.removeFirst();
        }
    }

    Optional<ClanMenuState> back(UUID playerId) {
        ArrayDeque<ClanMenuState> history = historyByPlayer.get(playerId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        ClanMenuState previous = history.removeLast();
        replacementByPlayer.put(playerId, previous);
        if (history.isEmpty()) {
            historyByPlayer.remove(playerId);
        }
        return Optional.of(previous);
    }

    void replaceNext(UUID playerId, ClanMenuState target) {
        replacementByPlayer.put(playerId, target);
    }

    void clear(UUID playerId) {
        historyByPlayer.remove(playerId);
        replacementByPlayer.remove(playerId);
    }

    int depth(UUID playerId) {
        ArrayDeque<ClanMenuState> history = historyByPlayer.get(playerId);
        return history == null ? 0 : history.size();
    }
}
