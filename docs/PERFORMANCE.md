# Performance

The plugin is designed to avoid database access in hot GUI and placeholder paths.

- Clan, member, ranking, bank, and battlepass views use caches.
- Database work and log cleanup run asynchronously.
- Ranking refreshes and activity awards are batched.
- Text-log retention and configuration backups are bounded.
- No shaded database drivers inflate the JAR or duplicate class memory.

Monitor TPS, MSPT, heap usage, GC pauses, SQL latency, and asynchronous queue growth during beta tests. Use Spark for server-wide profiling.

---
Made By CatgirlYannick
