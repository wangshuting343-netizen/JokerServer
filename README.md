# BattleJoker — Member C integration branch

The original A server in `src/` is preserved. The runnable integrated server, client, independent leaderboard and tests are in **member-c/**.

## Build and run on Windows

Install JDK 25 and Windows JavaFX 25.0.4. Set `JAVA_HOME` to the JDK directory and `PATH_TO_FX` to the JavaFX `lib` directory, then run from this repository:

```powershell
./member-c/run.ps1 build
./member-c/run.ps1 test
./member-c/run.ps1 server
```

In separate terminals:

```powershell
./member-c/run.ps1 client
./member-c/run.ps1 scoreboard
```

You can also pass `-JavaHome <JDK directory>` and `-JavaFx <JavaFX lib directory>` explicitly. JavaFX is not bundled. SQLite JDBC and SLF4J API are included in member-c/lib from the supplied group archive.

This branch excludes local databases, build outputs and machine logs. A new server database is created in member-c/data. Back up any existing database before importing it; the original local integration and its database backups were not modified by branch preparation.

- [Integration instructions and known limits](member-c/README.md)
- [Source comparison](member-c/SOURCES.md)
- [Original bug reproduction](member-c/BUG-EVIDENCE.md)
- [Recorded validation scope](member-c/evidence/VALIDATION.txt)

The branch does not claim cross-computer verification or completed report/video materials. Original teacher GameEngine.java and Database.java were restored verbatim; separate ServerDisplayState handles online rendering.
