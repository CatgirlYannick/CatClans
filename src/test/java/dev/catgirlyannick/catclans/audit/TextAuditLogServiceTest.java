package dev.catgirlyannick.catclans.audit;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAuditLogServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesUtf8AndRemovesControlCharacters() throws Exception {
        List<String> errors = new ArrayList<>();
        TextAuditLogService service = service(errors);
        Clan clan = clan();

        service.log(
                clan,
                "MEMBER_LEFT",
                clan.ownerId(),
                "Yännick\nInjected",
                "Grund\tmit\rZeilenumbruch"
        );

        Path clanDirectory = temporaryDirectory.resolve(clan.id().toString());
        Path logFile;
        try (var files = Files.list(clanDirectory)) {
            logFile = files.findFirst().orElseThrow();
        }
        String content = Files.readString(logFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("Yännick Injected"));
        assertTrue(content.contains("Grund mit Zeilenumbruch"));
        assertEquals(1, content.lines().count());
        assertTrue(errors.isEmpty());
    }

    @Test
    void deletesExpiredLogFiles() throws Exception {
        List<String> errors = new ArrayList<>();
        TextAuditLogService service = service(errors);
        Path clanDirectory = temporaryDirectory.resolve(UUID.randomUUID().toString());
        Files.createDirectories(clanDirectory);
        Path expired = clanDirectory.resolve("old.log");
        Path current = clanDirectory.resolve("current.log");
        Files.writeString(expired, "old", StandardCharsets.UTF_8);
        Files.writeString(current, "current", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(
                expired,
                FileTime.from(Instant.now().minus(15, ChronoUnit.DAYS))
        );

        service.cleanupExpiredFiles();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(current));
        assertTrue(errors.isEmpty());
    }

    @Test
    void readsTheLatestActionsForOnePlayerFromTextFiles() {
        List<String> errors = new ArrayList<>();
        TextAuditLogService service = service(errors);
        Clan clan = clan();
        UUID secondPlayer = UUID.randomUUID();

        service.log(
                clan,
                "VAULT_DEPOSIT",
                clan.ownerId(),
                "Yannick",
                "item=DIAMOND amount=4"
        );
        service.log(
                clan,
                "VAULT_WITHDRAW",
                secondPlayer,
                "Alex",
                "item=EMERALD amount=2"
        );
        service.log(
                clan,
                "VAULT_WITHDRAW",
                clan.ownerId(),
                "Yannick",
                "item=DIAMOND amount=1"
        );

        List<AuditLogEntry> entries = service.recent(
                clan.id(),
                clan.ownerId(),
                18
        );

        assertEquals(2, entries.size());
        assertEquals("VAULT_WITHDRAW", entries.getFirst().action());
        assertEquals("item=DIAMOND amount=1", entries.getFirst().details());
        assertEquals("VAULT_DEPOSIT", entries.get(1).action());
        assertTrue(errors.isEmpty());
    }

    private TextAuditLogService service(List<String> errors) {
        return new TextAuditLogService(
                temporaryDirectory,
                true,
                14,
                "yyyy-MM-dd'.log'",
                "yyyy-MM-dd HH:mm:ss.SSS XXX",
                errors::add
        );
    }

    private static Clan clan() {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        return new Clan(
                clanId,
                "Wächter",
                "wächter",
                "W7",
                "w7",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Yannick", RankId.OWNER, now))
        );
    }
}
