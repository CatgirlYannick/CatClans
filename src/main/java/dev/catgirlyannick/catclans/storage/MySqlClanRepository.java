package dev.catgirlyannick.catclans.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

public final class MySqlClanRepository extends SqliteClanRepository {

    private final String host;
    private final int port;
    private final String database;
    private final String usernameEnvironmentVariable;
    private final String passwordEnvironmentVariable;
    private final boolean useSsl;
    private final boolean verifyServerCertificate;
    private final int connectTimeoutMilliseconds;
    private final int socketTimeoutMilliseconds;
    private final int validationIntervalSeconds;

    public MySqlClanRepository(
            String host,
            int port,
            String database,
            String usernameEnvironmentVariable,
            String passwordEnvironmentVariable,
            boolean useSsl,
            boolean verifyServerCertificate,
            int connectTimeoutMilliseconds,
            int socketTimeoutMilliseconds,
            int validationIntervalSeconds
    ) {
        super();
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.database = Objects.requireNonNull(database, "database");
        this.usernameEnvironmentVariable = Objects.requireNonNull(
                usernameEnvironmentVariable,
                "usernameEnvironmentVariable"
        );
        this.passwordEnvironmentVariable = Objects.requireNonNull(
                passwordEnvironmentVariable,
                "passwordEnvironmentVariable"
        );
        this.useSsl = useSsl;
        this.verifyServerCertificate = verifyServerCertificate;
        this.connectTimeoutMilliseconds = connectTimeoutMilliseconds;
        this.socketTimeoutMilliseconds = socketTimeoutMilliseconds;
        this.validationIntervalSeconds = validationIntervalSeconds;
    }

    @Override
    protected void prepareStorage() {
        // MySQL manages storage outside the plugin data folder.
    }

    @Override
    protected Connection openConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return MySqlDialectConnection.reconnecting(
                this::openRawConnection,
                validationIntervalSeconds * 1000L
        );
    }

    private Connection openRawConnection() throws Exception {
        String username = requiredEnvironmentValue(usernameEnvironmentVariable);
        String password = requiredEnvironmentValue(passwordEnvironmentVariable);
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true"
                + "&characterEncoding=UTF-8"
                + "&connectionCollation=utf8mb4_unicode_ci"
                + "&serverTimezone=UTC"
                + "&useSSL=" + useSsl
                + "&verifyServerCertificate=" + verifyServerCertificate
                + "&connectTimeout=" + connectTimeoutMilliseconds
                + "&socketTimeout=" + socketTimeoutMilliseconds
                + "&tcpKeepAlive=true";
        Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+00:00'");
        }
        return connection;
    }

    @Override
    protected void configureConnection(Connection configuredConnection) {
        // Every new raw connection is fully configured before use.
    }

    @Override
    protected void lockClanPair(UUID firstClanId, UUID secondClanId)
            throws java.sql.SQLException {
        String first = firstClanId.toString().compareTo(secondClanId.toString()) <= 0
                ? firstClanId.toString()
                : secondClanId.toString();
        String second = first.equals(firstClanId.toString())
                ? secondClanId.toString()
                : firstClanId.toString();
        try (PreparedStatement statement = currentConnection().prepareStatement("""
                SELECT id
                FROM clans
                WHERE id IN (?, ?)
                ORDER BY id
                FOR UPDATE
                """)) {
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet ignored = statement.executeQuery()) {
                while (ignored.next()) {
                    // The complete read keeps both row locks until commit.
                }
            }
        }
    }

    @Override
    protected void lockBankAccount(UUID clanId) throws java.sql.SQLException {
        try (PreparedStatement statement = currentConnection().prepareStatement("""
                SELECT balance
                FROM clan_bank_accounts
                WHERE clan_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new java.sql.SQLException("Clan bank account is missing after initialization");
                }
            }
        }
    }

    private static String requiredEnvironmentValue(String variableName) {
        String value = System.getenv(variableName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required MySQL environment variable is missing: " + variableName
            );
        }
        return value;
    }
}
