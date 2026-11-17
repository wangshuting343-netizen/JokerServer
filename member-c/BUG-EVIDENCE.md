# 原版 Bug：姓名中的引号导致数据库保存失败

来源：用户提供的原始 BattleJoker.zip，路径 BattleJoker/src/Database.java；证据副本 evidence/original/Database.java，未改动。此问题不是 section2 新增功能缺失。

触发：原版 Database.connect 后，调用 Database.putScore("O'Brien",10,2)。普通玩家名字包含合法英文单引号即可触发。OriginalBugTest 使用隔离测试数据库和原版类，不向正式库写入测试成绩。

实际：原版 String.format 拼接 SQL 得到被引号打断的 VALUES，SQLite 抛出 `[SQLITE_ERROR] ... near "Brien": syntax error`。预期：保存姓名、分数、等级，不因姓名中的合法字符报错。

修复：服务端 GameRepository.save 使用 PreparedStatement 占位符绑定姓名，事务提交；客户端不再直接写数据库。RepositoryTest 将完全相同的 O'Brien 中文 保存、重复提交、关闭重开，确认成绩仍在，且重复不新增。

复现命令（integrated 目录；编译原版类到独立目录）：

```powershell
javac -encoding UTF-8 -d evidence/original-test evidence/original/Database.java tests/OriginalBugTest.java
cd evidence/original-test
java --enable-native-access=ALL-UNNAMED -cp '.;../../lib/*' OriginalBugTest
```

本次实际输出：`REPRODUCED original Database.putScore quote failure: [SQLITE_ERROR] SQL error or missing database (near "Brien": syntax error)`。
修复后实际输出：`PASS repository: quote/unicode, duplicate, conflict, top10, restart`。

这是方法级真实数据库复现，没有录制原版 GUI 视频。报告和视频可先展示原始拼接 SQL 与方法级错误，再展示参数绑定及修复测试；如需声称完整 GUI 操作证据，须三人另行实际录制。不要将 A 最新版横向索引错误混充本条原版证据。
