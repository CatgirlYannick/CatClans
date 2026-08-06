package dev.catgirlyannick.catclans.audit;

import dev.catgirlyannick.catclans.model.Clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TextAuditLogService {

    private static final Pattern LOG_LINE = Pattern.compile(
            "^\\[([^]]+)] \\[([^]]+)] actor=([^/]+)/(.+?) "
                    + "clan=([^/]+)/(.+?) details=(.*)$"
    );
    private static final int MAXIMUM_READ_BYTES_PER_LOG_FILE = 2_097_152;

    private final Path rootDirectory;
    private final boolean enabled;
    private final int retentionDays;
    private final DateTimeFormatter fileFormatter;
    private final DateTimeFormatter timestampFormatter;
    private final Consumer<String> errorLogger;

    public TextAuditLogService(
            Path rootDirectory,
            boolean enabled,
            int retentionDays,
            String filePattern,
            String timestampPattern,
            Consumer<String> errorLogger
    ) {
        this.rootDirectory = rootDirectory;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.fileFormatter = DateTimeFormatter.ofPattern(filePattern);
        this.timestampFormatter = DateTimeFormatter.ofPattern(timestampPattern);
        this.errorLogger = errorLogger;
    }

    public void log(
            Clan clan,
            String action,
            UUID actorId,
            String actorName,
            String details
    ) {
        if (!enabled) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        Path clanDirectory = rootDirectory.resolve(clan.id().toString());
        Path logFile = clanDirectory.resolve(fileFormatter.format(now));
        String line = "[%s] [%s] actor=%s/%s clan=%s/%s details=%s%n".formatted(
                timestampFormatter.format(now),
                sanitize(action),
                actorId,
                sanitize(actorName),
                clan.id(),
                sanitize(clan.name()),
                sanitize(details)
        );
        try {
            Files.createDirectories(clanDirectory);
            Files.writeString(
                    logFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            errorLogger.accept("Text audit log could not be written: " + exception.getMessage());
        }
    }

    public void cleanupExpiredFiles() {
        if (!enabled || !Files.isDirectory(rootDirectory)) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Stream<Path> files = Files.walk(rootDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .forEach(path -> deleteIfExpired(path, cutoff));
        } catch (IOException exception) {
            errorLogger.accept("Text audit logs could not be cleaned up: " + exception.getMessage());
        }
    }

    public List<AuditLogEntry> recent(
            UUID clanId,
            UUID actorId,
            int requestedLimit
    ) {
        if (!enabled || requestedLimit < 1) {
            return List.of();
        }
        int limit = Math.min(requestedLimit, 100);
        Path clanDirectory = rootDirectory.resolve(clanId.toString());
        if (!Files.isDirectory(clanDirectory)) {
            return List.of();
        }
        List<AuditLogEntry> entries = new ArrayList<>(limit);
        try (Stream<Path> files = Files.list(clanDirectory)) {
            List<Path> orderedFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparing(
                            (Path path) -> path.getFileName().toString()
                    ).reversed())
                    .toList();
            for (Path file : orderedFiles) {
                List<String> lines = readLogTail(file);
                for (int index = lines.size() - 1; index >= 0; index--) {
                    parse(lines.get(index))
                            .filter(entry -> actorId == null
                                    || actorId.equals(entry.actorId()))
                            .ifPresent(entries::add);
                    if (entries.size() >= limit) {
                        return List.copyOf(entries);
                    }
                }
            }
        } catch (IOException exception) {
            errorLogger.accept("Text audit logs could not be read: "
                    + exception.getMessage());
        }
        return List.copyOf(entries);
    }

    private static List<String> readLogTail(Path file) throws IOException {
        long fileSize = Files.size(file);
        long start = Math.max(0L, fileSize - MAXIMUM_READ_BYTES_PER_LOG_FILE);
        int length = Math.toIntExact(fileSize - start);
        byte[] data;
        try (InputStream input = Files.newInputStream(file)) {
            input.skipNBytes(start);
            data = input.readNBytes(length);
        }
        String content = new String(data, StandardCharsets.UTF_8);
        if (start > 0L) {
            int firstCompleteLine = content.indexOf('\n');
            content = firstCompleteLine < 0
                    ? ""
                    : content.substring(firstCompleteLine + 1);
        }
        return content.lines().toList();
    }

    private void deleteIfExpired(Path path, Instant cutoff) {
        try {
            if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            errorLogger.accept("Expired text audit log could not be deleted: "
                    + path + " (" + exception.getMessage() + ")");
        }
    }

    private java.util.Optional<AuditLogEntry> parse(String line) {
        Matcher matcher = LOG_LINE.matcher(line);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new AuditLogEntry(
                    parseTimestamp(matcher.group(1)),
                    matcher.group(2),
                    UUID.fromString(matcher.group(3)),
                    matcher.group(4),
                    matcher.group(7)
            ));
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private Instant parseTimestamp(String timestamp) {
        TemporalAccessor parsed = timestampFormatter.parseBest(
                timestamp,
                ZonedDateTime::from,
                OffsetDateTime::from,
                LocalDateTime::from
        );
        if (parsed instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (parsed instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((LocalDateTime) parsed).atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("[\\p{Cntrl}&&[^ ]]", "")
                .trim();
    }
}
