package dev.catgirlyannick.catclans.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlDialectConnectionTest {

    @Test
    void translatesSqliteUpsertIntoMySqlSyntax() {
        String translated = MySqlDialectConnection.adapt("""
                INSERT INTO clan_role_limits(clan_id, maximum_roles)
                VALUES (?, ?)
                ON CONFLICT(clan_id) DO UPDATE SET
                    maximum_roles = excluded.maximum_roles
                """);

        assertTrue(translated.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(translated.contains("VALUES(maximum_roles)"));
        assertFalse(translated.contains("ON CONFLICT"));
    }

    @Test
    void translatesCompositeClanHomeUpsertIntoMySqlSyntax() {
        String translated = MySqlDialectConnection.adapt("""
                INSERT INTO clan_homes(clan_id, home_number, world_uuid, world_name)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(clan_id, home_number) DO UPDATE SET
                    world_uuid = excluded.world_uuid,
                    world_name = excluded.world_name
                """);

        assertTrue(translated.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(translated.contains("VALUES(world_uuid)"));
        assertTrue(translated.contains("VALUES(world_name)"));
        assertFalse(translated.contains("ON CONFLICT"));
    }

    @Test
    void translatesDoNothingAndPortableSchemaTypes() {
        String insert = MySqlDialectConnection.adapt("""
                INSERT INTO schema_meta(id, version)
                VALUES (1, 5)
                ON CONFLICT(id) DO NOTHING
                """);
        String schema = MySqlDialectConnection.adapt("""
                CREATE TABLE clans (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """);

        assertTrue(insert.contains("INSERT IGNORE INTO"));
        assertFalse(insert.contains("ON CONFLICT"));
        assertTrue(schema.contains("VARCHAR(255)"));
        assertFalse(schema.contains(" TEXT"));
    }

    @Test
    void keepsFormattedTagsLargeEnoughForValidatedMiniMessage() {
        String schema = MySqlDialectConnection.adapt("""
                CREATE TABLE clans (
                    id TEXT PRIMARY KEY,
                    formatted_tag TEXT NOT NULL
                )
                """);

        assertTrue(schema.contains("id VARCHAR(255)"));
        assertTrue(schema.contains("formatted_tag TEXT"));
    }

    @Test
    void reconnectsBeforeAnOperationWhenTheIdleConnectionIsInvalid() throws Exception {
        AtomicInteger openedConnections = new AtomicInteger();
        Connection connection = MySqlDialectConnection.reconnecting(
                () -> fakeConnection(openedConnections.incrementAndGet() > 1),
                0
        );

        assertTrue(connection.getAutoCommit());
        assertEquals(2, openedConnections.get());
        connection.close();
    }

    private static Connection fakeConnection(boolean valid) {
        return (Connection) Proxy.newProxyInstance(
                MySqlDialectConnectionTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isValid" -> valid;
                    case "isClosed" -> false;
                    case "getAutoCommit" -> true;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
