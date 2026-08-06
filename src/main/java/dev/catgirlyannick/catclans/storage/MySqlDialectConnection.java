package dev.catgirlyannick.catclans.storage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

final class MySqlDialectConnection {

    private static final Pattern DO_NOTHING = Pattern.compile(
            "(?is)\\s+ON\\s+CONFLICT\\s*\\([^)]*\\)\\s+DO\\s+NOTHING\\s*$"
    );
    private static final Pattern DO_UPDATE = Pattern.compile(
            "(?is)ON\\s+CONFLICT\\s*\\([^)]*\\)\\s+DO\\s+UPDATE\\s+SET"
    );
    private static final Pattern EXCLUDED_COLUMN = Pattern.compile(
            "(?i)excluded\\.([a-z0-9_]+)"
    );

    private MySqlDialectConnection() {
    }

    static Connection wrap(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                MySqlDialectConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> invokeConnection(delegate, method, arguments)
        );
    }

    static Connection reconnecting(
            ConnectionFactory factory,
            long validationIntervalMilliseconds
    ) throws Exception {
        ReconnectingHandler handler = new ReconnectingHandler(
                factory,
                validationIntervalMilliseconds
        );
        handler.connect();
        return (Connection) Proxy.newProxyInstance(
                MySqlDialectConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler::invoke
        );
    }

    private static Object invokeConnection(
            Connection delegate,
            Method method,
            Object[] arguments
    ) throws Throwable {
        Object[] adapted = arguments;
        if (arguments != null
                && arguments.length > 0
                && arguments[0] instanceof String sql
                && method.getName().startsWith("prepareStatement")) {
            adapted = arguments.clone();
            adapted[0] = adapt(sql);
        }
        try {
            Object result = method.invoke(delegate, adapted);
            if ("createStatement".equals(method.getName()) && result instanceof Statement statement) {
                return wrapStatement(statement);
            }
            return result;
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Statement wrapStatement(Statement delegate) {
        return (Statement) Proxy.newProxyInstance(
                MySqlDialectConnection.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, arguments) -> {
                    Object[] adapted = arguments;
                    String originalSql = null;
                    if (arguments != null
                            && arguments.length > 0
                            && arguments[0] instanceof String sql) {
                        originalSql = sql;
                        adapted = arguments.clone();
                        adapted[0] = adapt(sql);
                    }
                    try {
                        return method.invoke(delegate, adapted);
                    } catch (InvocationTargetException exception) {
                        Throwable cause = exception.getCause();
                        if (cause instanceof SQLException sqlException
                                && originalSql != null
                                && isDuplicateIndex(originalSql, sqlException)) {
                            return defaultResult(method.getReturnType());
                        }
                        throw cause;
                    }
                }
        );
    }

    static String adapt(String sqliteSql) {
        String adapted = sqliteSql
                .replaceAll("(?i)\\bTEXT\\b", "VARCHAR(255)")
                .replaceAll(
                        "(?i)formatted_tag\\s+VARCHAR\\(255\\)",
                        "formatted_tag TEXT"
                )
                .replaceAll("(?i)\\s+COLLATE\\s+NOCASE", "")
                .replaceAll(
                        "(?i)CREATE\\s+(UNIQUE\\s+)?INDEX\\s+IF\\s+NOT\\s+EXISTS",
                        "CREATE $1INDEX"
                );
        if (DO_NOTHING.matcher(adapted).find()) {
            adapted = adapted.replaceFirst("(?i)INSERT\\s+INTO", "INSERT IGNORE INTO");
            adapted = DO_NOTHING.matcher(adapted).replaceFirst("");
        } else {
            adapted = DO_UPDATE.matcher(adapted).replaceFirst("ON DUPLICATE KEY UPDATE");
            adapted = EXCLUDED_COLUMN.matcher(adapted).replaceAll("VALUES($1)");
        }
        return adapted;
    }

    private static boolean isDuplicateIndex(String sql, SQLException exception) {
        return sql.stripLeading().toUpperCase().startsWith("CREATE")
                && sql.toUpperCase().contains("INDEX")
                && exception.getErrorCode() == 1061;
    }

    private static Object defaultResult(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == long.class) {
            return 0;
        }
        return null;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws Exception;
    }

    private static final class ReconnectingHandler {

        private final ConnectionFactory factory;
        private final long validationIntervalNanos;
        private Connection delegate;
        private boolean autoCommit = true;
        private boolean closed;
        private long lastValidationNanos;

        private ReconnectingHandler(
                ConnectionFactory factory,
                long validationIntervalMilliseconds
        ) {
            this.factory = factory;
            this.validationIntervalNanos = validationIntervalMilliseconds * 1_000_000L;
        }

        private synchronized Object invoke(
                Object proxy,
                Method method,
                Object[] arguments
        ) throws Throwable {
            String methodName = method.getName();
            if ("close".equals(methodName)) {
                closed = true;
                if (delegate != null && !delegate.isClosed()) {
                    delegate.close();
                }
                return null;
            }
            if ("isClosed".equals(methodName)) {
                return closed;
            }
            if (closed) {
                throw new SQLNonTransientConnectionException(
                        "MySQL connection has already been closed"
                );
            }
            boolean enablingAutoCommit = "setAutoCommit".equals(methodName)
                    && arguments != null
                    && arguments.length == 1
                    && Boolean.TRUE.equals(arguments[0]);
            ensureConnected(enablingAutoCommit);

            Object[] adapted = arguments;
            if (arguments != null
                    && arguments.length > 0
                    && arguments[0] instanceof String sql
                    && methodName.startsWith("prepareStatement")) {
                adapted = arguments.clone();
                adapted[0] = adapt(sql);
            }
            try {
                Object result = method.invoke(delegate, adapted);
                if ("setAutoCommit".equals(methodName)) {
                    autoCommit = Boolean.TRUE.equals(arguments[0]);
                }
                if ("createStatement".equals(methodName)
                        && result instanceof Statement statement) {
                    return wrapStatement(statement);
                }
                return result;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private void ensureConnected(boolean allowTransactionReset) throws Exception {
            if (delegate != null && !delegate.isClosed()) {
                long now = System.nanoTime();
                if (validationIntervalNanos > 0L
                        && now - lastValidationNanos < validationIntervalNanos) {
                    return;
                }
                if (delegate.isValid(2)) {
                    lastValidationNanos = now;
                    return;
                }
            }
            if (!autoCommit && !allowTransactionReset) {
                throw new SQLNonTransientConnectionException(
                        "MySQL connection was lost during a transaction"
                );
            }
            closeSilently();
            connect();
            autoCommit = true;
        }

        private void connect() throws Exception {
            delegate = factory.open();
            lastValidationNanos = System.nanoTime();
        }

        private void closeSilently() {
            if (delegate == null) {
                return;
            }
            try {
                delegate.close();
            } catch (SQLException ignored) {
                // The new connection may open even when the previous session is broken.
            }
        }
    }
}
