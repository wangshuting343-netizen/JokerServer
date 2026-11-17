> GitHub branch edition: this directory contains the runnable integration. The repository root src/ remains the unchanged A baseline. Historical databases and generated files are intentionally excluded; startup creates a new database. Existing local backups remain in the original integrated/data directory.

# BattleJoker 整合工程（成员 C）

此目录是独立整合副本，原有项目和 ZIP 未改动。来源比较见 SOURCES.md。A 来源 commit：`b7d772848f6ea89a3db04987a9adba749bd153fe`，原 A 源码保存在仓库根目录 src/，实际启动源码在 server-src/。

## 启动

需要 JDK 25、Windows JavaFX 25.0.4。SQLite 3.45.2.0 与 SLF4J API 1.7.36 从 group ZIP 原样保留在 lib/；正式客户端不加载数据库驱动。SLF4J 缺少日志绑定会有提示，不影响数据库读写。

在当前目录打开 PowerShell：

```powershell
./run.ps1 build
./run.ps1 server
# 另一个窗口
./run.ps1 client
# 独立排行榜（不需要启动游戏客户端）
./run.ps1 scoreboard
./run.ps1 test
./run.ps1 ui-test
```

脚本支持 `-JavaHome <JDK目录> -JavaFx <JavaFX的lib目录>`；也读取 JAVA_HOME、PATH_TO_FX。本机已提供路径自动回退，换电脑请显式指定。客户端 `-HostAddress 192.168.x.x -TcpPort 12345`；服务器与榜单可用 `-Nic <网卡名称> -UdpPort 15557 -Group 239.25.30.15`。默认 TTL=1。组播自动选有 IPv4 的活跃组播网卡；多网卡环境应两端显式选择同一局域网网卡。防火墙需允许相应 Java TCP/UDP 流量。

## 实际协议（兼容 A，不是完整 v2 实现）

保留 A 的 DataInputStream/DataOutputStream 整数通信。所有客户端请求均为 I(type)+S(payload)，S 为 I(UTF-8 字节数)+字节；5/6/8 的 payload 是空串，仍含长度 0。7 的空串后跟 16 个棋盘整数。8 是 GET_TOP10，**不是共享 v2 的 GET_ROOM_STATE**。0 NAME，1 COMMAND，3 PUZZLE，4 PLAYER_INFO（姓名+score），5 START_GAME，6 UNDO，7 UPLOAD。0..7 原有有效消息体不变；修正 UTF-8 字节长度和 readFully，长度上限 4096，未知编号关闭连接。

C 扩展用 21 TOP、22 RESULT、24 ERROR、25 GET_RESULT（payload 为本连接的 gameId），避免挤占草案 9..20；23 预留。不得将本项目客户端与 v2 编码器混用。

- TOP：S(serverId), L(sequence), I(count<=10)，每行 S(gameId),S(name),I(score),I(level),S(time)。TCP 和 UDP 完全相同；一个 UDP 数据报 I(21)+上述数据，最大 8192 字节，无尾随内容。
- RESULT：S(gameId),S(persistence),S(winner),I(count<=4)，每行 S(name),I(score)。弃权者在姓名后标记；行在结束时冻结，后续断线不改结算内容。状态 PENDING/SAVED/FAILED:原因/CONFLICT，同 gameId 更新原窗口。
- ERROR：S(message)。历史查询失败明确显示错误，不返回假成功榜单。

独立组播榜每秒刷新 UI；发送端每两秒发送，榜单变化立即发送。按 serverId 分开，同服务器旧 sequence 丢弃，同 sequence 更新存活时间；6 秒无数据提示可能断线，保留旧成绩。后台收包，JavaFX 定时更新，关闭释放 socket 和线程。

## 数据库与迁移

正式路径 data/battleJoker.db，来源为 group ZIP；data/group-original.db 是未修改备份。原 ZIP/兄弟目录数据库均未覆盖。新赢家表 winners(game_id PRIMARY KEY,name,score,level,ended_at)。旧 scores 表保持原状，按原 rowid 赋予 legacy:<rowid>，重复初始化不重复迁移。不要直接拼接不同来源数据库以免同一旧成绩被重复算入；如需选择另一份历史库，先停服备份，再使用相应数据库副本启动。

GameRepository 使用参数化 SQL、事务、资源关闭与串行访问。相同 gameId 相同内容返回 ALREADY_SAVED，不同内容返回 CONFLICT。历史按分数降序、结束时间升序、gameId 升序前十。旧 time 原样保留，原版未标注时区，不能声称已转换为 UTC。

SettlementService 专用单线程执行 SQL，失败按 1/2/5/10/30 秒（之后每 30 秒）重试。只有成功持久化才发布成绩与增加 sequence。服务器生成每局 UUID，客户端不指定赢家或 gameId。全员成绩窗口与历史窗口分开；客户端正式启动路径已移除本地 Database 调用与原测试写库程序，ServerDisplayState 只承载显示状态；group ZIP 中的 GameEngine.java 与 Database.java 已原样恢复，不参与正式联网路径的本地游戏运算或写库。

## 已验证与边界

自动测试源码在 tests/。RepositoryTest 验证引号/中文、重复、冲突、前十、重启；MigrationTest 验证旧表保留、重复迁移、两线程结算；SettlementTest 用真实不可用路径验证失败、无假榜单、恢复自动重试；EngineTest 验证 A 横向满盘与 Joker 结束修复；CacheTest 验证乱序/重复/新服务器。

实际网络验证：`python tests/run_network.py` 自动启停测试服务器，使用独立 evidence/tcp-test.db，验证分段中文 NAME、开局、上传、真实 Joker 结束、PENDING/SAVED 和服务器历史查询。脚本 java 路径目前为本机路径，其他电脑请修改。`java -cp build MulticastTest` 在测试端口 15559 验证本机真实立即发送、周期刷新、中文数据报与关闭。

本次通过服务端和 JavaFX 客户端编译，另已实际启动游戏 FXML/图片与独立排行榜并自动关闭（evidence/GuiSmokeTest.java）；已补充 JavaFX 自动界面检查，验证结算状态更新、单一历史榜窗口、断线弹窗、实际组播显示和超时保留成绩；未进行真实跨电脑测试或人工逐屏验收。不得将本机 TCP/组播测试写成跨电脑验收。原版 Bug 证据见 BUG-EVIDENCE.md。

## 仍需处理的限制（不能宣称完整 Version 3 / 全附加分已验收）

A 的部分非 C 逻辑仍需继续联调：撤销目前只恢复棋盘/剩余步数，尚未恢复分数、等级和随机状态；缺少完整房间/回合快照、请求确认/超时/心跳、慢客户端发送队列；游戏 UI 尚未显示完整回合状态。客户端尚无保存上传/撤销按钮（测试工具可发送 A 已有请求）；上传校验尚不等同 v2 全部规则。本次没有强行以 v2 重写 A，也没有将这些缺项算成完成。

结果现由独立 ResultCache 保存，最后一次结算状态更新后保留至少 30 分钟，编号 25 仅向原参与连接返回结果，查询不再依赖当前房间状态，也不会向全房重复广播；断线客户端需通过历史榜查看已保存赢家；服务端保存重试任务在进程内，进程在保存前崩溃会丢失尚未落库任务，不能声称跨崩溃保证。存储冲突停止自动覆盖，需人工核查。三人仍须共同参与报告、视频和真实跨电脑验收；未填写姓名或贡献比例。



本轮范围：不进行跨电脑验证，不编写报告或视频素材。缓存使用服务器进程内会话身份，不以姓名认证，断线重连不会取得旧身份。原版 GameEngine/Database 恢复为 ZIP 中逐字节一致版本；新增 ServerDisplayState 隔离联网显示，未改写老师原游戏算法。
