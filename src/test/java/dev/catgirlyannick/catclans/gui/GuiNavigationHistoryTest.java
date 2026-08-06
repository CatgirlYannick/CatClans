package dev.catgirlyannick.catclans.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiNavigationHistoryTest {

    private static final UUID PLAYER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID CLAN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );

    @Test
    void returnsToTheImmediatelyPreviousMenu() {
        GuiNavigationHistory navigation = new GuiNavigationHistory();
        ClanMenuState main = state(ClanMenuType.MAIN, 0);
        ClanMenuState profile = state(ClanMenuType.PROFILE, 0);
        ClanMenuState permissions = state(ClanMenuType.PERMISSION_HOME, 0);

        navigation.opened(PLAYER_ID, null, main);
        navigation.opened(PLAYER_ID, main, profile);
        navigation.opened(PLAYER_ID, profile, permissions);

        assertEquals(profile, navigation.back(PLAYER_ID).orElseThrow());
        navigation.opened(PLAYER_ID, permissions, profile);
        assertEquals(main, navigation.back(PLAYER_ID).orElseThrow());
    }

    @Test
    void replacementsAndRefreshesDoNotPolluteHistory() {
        GuiNavigationHistory navigation = new GuiNavigationHistory();
        ClanMenuState main = state(ClanMenuType.MAIN, 0);
        ClanMenuState firstPage = state(ClanMenuType.CLAN_LIST, 0);
        ClanMenuState secondPage = state(ClanMenuType.CLAN_LIST, 1);

        navigation.opened(PLAYER_ID, null, main);
        navigation.opened(PLAYER_ID, main, firstPage);
        navigation.replaceNext(PLAYER_ID, secondPage);
        navigation.opened(PLAYER_ID, firstPage, secondPage);
        navigation.opened(PLAYER_ID, secondPage, secondPage);

        assertEquals(1, navigation.depth(PLAYER_ID));
        assertEquals(main, navigation.back(PLAYER_ID).orElseThrow());
    }

    @Test
    void capsHistoryDepth() {
        GuiNavigationHistory navigation = new GuiNavigationHistory();
        ClanMenuState current = state(ClanMenuType.CLAN_LIST, 0);

        for (int page = 1; page <= 40; page++) {
            ClanMenuState next = state(ClanMenuType.CLAN_LIST, page);
            navigation.opened(PLAYER_ID, current, next);
            current = next;
        }

        assertEquals(32, navigation.depth(PLAYER_ID));
        for (int index = 0; index < 32; index++) {
            assertTrue(navigation.back(PLAYER_ID).isPresent());
        }
        assertTrue(navigation.back(PLAYER_ID).isEmpty());
    }

    private static ClanMenuState state(ClanMenuType type, int page) {
        return new ClanMenuState(type, CLAN_ID, null, page, null, null, null);
    }
}
