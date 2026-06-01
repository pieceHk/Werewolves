# 开发阶段汇报

本文档记录本仓库按 `SPEC.md` 里程碑进行的开发总结。API Key 不写入仓库，真实 Qwen 调用统一读取环境变量 `DASHSCOPE_API_KEY`。

## M0 — 项目初始化

- 建立 Maven 单模块 Java 17 项目。
- 将桌面开发文档复制为仓库根目录 `SPEC.md`。
- 增加 `.gitignore`，忽略 `target/`、`matches/`、`.env` 与可能包含密钥的文件。
- 增加 `application.yml`，配置规则参数、输出目录与 Qwen OpenAI 兼容端点。
- 安装本机开发工具链：OpenJDK 17 与 Maven。

## M1 — 领域模型与状态机

- 实现 `core.model`：阵营、角色、阶段、玩家状态、死亡原因、规则参数、玩家与对局聚合。
- 实现 `GameFactory.standardNine`，支持固定 seed 的 9 人预女猎角色分配。
- 实现 `core.event`：通用事件、可见性 `PUBLIC/PRIVATE`、可见座位 audience 与简单 `EventBus`。
- 实现 `GameStateMachine`，限制 `PREPARING → NIGHT → DAY_ANNOUNCE → DAY_DISCUSS → DAY_VOTE → NIGHT`，任意阶段可进入 `GAME_OVER`。
- 增加状态机单测，覆盖合法流转与非法流转。

## M2 — 规则引擎

- 实现 `NightResolver`：狼人刀、女巫解药/毒药、首夜自救、同晚双药限制与毒药无视解药。
- 实现 `VoteResolver`：多数票、弃票、首次平票重投、二次平票无人出局。
- 实现 `WinChecker`：好人狼全灭胜、狼人屠边/屠城规则。
- 增加夜晚结算、平票和胜负判定单测。

## M3 — LLM 客户端

- 定义 `LLMClient`、`ChatMessage`、`ChatOptions` 与 `LLMException`。
- 实现 `QwenLLMClient`，使用 DashScope OpenAI 兼容 `/chat/completions`，API Key 仅从环境变量传入，不进入配置文件。
- 实现连接超时、请求超时、重试、并发限流和响应 JSON 解析。
- 增加 `MockLLMClient`，用于测试和本地离线跑通整局，测试不联网。

## M4 — Agent 层

- 实现 `AgentAction` 密封接口与发言、投票、狼人击杀、预言家查验、女巫行动、猎人开枪、空操作等动作。
- 实现 `AgentContext` 与 `ContextBuilder`，基于事件 `PUBLIC/PRIVATE + audience` 做信息隔离。
- 实现 `PromptBuilder`，中文 prompt 中明确要求严格 JSON 与发言不超过 100 个汉字。
- 实现 `ActionParser`，支持 Markdown 围栏剥离、首个 JSON 提取、非法目标降级、发言按码点截断并返回告警。
- 实现 `AgentJournal` 作为每个座位的本对局私有记忆，跨轮累积但不跨对局。
- 增加 `PlayerController`、`LLMPlayerController` 和 v2 预留的 `HumanPlayerController` stub。
- 实现 `AgentOrchestrator`：狼人夜晚串行商讨、预言家/女巫行动、白天严格按座位串行发言、投票并发。

## M5 — 引擎编排、CLI 与日志

- 实现 `PhaseScheduler` 与 `GameService`，按夜晚、公布、遗言、白天发言、投票、胜负检测推进对局。
- 实现 `MatchManager`，每场对局创建全新 `Game`、全新玩家控制器与全新 `AgentJournal`。
- 实现 `MatchWorkspace`，每场对局创建独立目录，并预留 `human-view.md`。
- 实现 `god-view.md`、`agents/seat-N.md`、`replay.json`、`meta.json` 写出。
- 实现 CLI 入口 `MatchRunner`，支持 `--seed`、`--rounds-cap`、`--out`、`--llm mock|qwen`。
- 默认使用 `MockLLMClient` 离线跑通；真实 Qwen 模式需要先设置环境变量 `DASHSCOPE_API_KEY`。

## M6 — 文档与运行验证

- 扩展 `README.md`，补充项目能力、目录结构、本地运行、Qwen 环境变量与输出文件说明。
- 修正 `MockLLMClient` 的动作识别逻辑，改为读取 prompt 中的“当前要求动作”，避免白天 prompt 中包含历史 `WOLF_KILL` 事件时误判为狼人刀人。
- 执行 `mvn test`，共 15 个测试通过。
- 执行 `mvn clean package`，成功生成可运行 shaded jar：`target/multiagent-werewolf.jar`。
- 使用 mock LLM 执行 `java -jar target/multiagent-werewolf.jar --seed 12345 --rounds-cap 8 --out ./matches`，成功生成对局目录 `matches/match-20260601-191029-39523a60`。
- 对输出目录检查确认包含 `god-view.md`、`human-view.md`、`agents/seat-1.md` 至 `agents/seat-9.md`、`replay.json` 与 `meta.json`。
- 对最新对局日志执行关键字检查，未发现“非法目标”“解析失败”“系统备注”等异常降级记录。

## M7 — 真实 Qwen 容错与实时日志

- 修复真实 Qwen 输出非法女巫动作时整局中断的问题：夜晚动作在进入 `NightResolver` 前会先净化，女巫二夜及以后自救、无刀口救人、同晚双药、药品不可用、无效目标等情况会被降级并写入私有系统备注。
- 增加 `MatchSnapshotWriter`，每次 `Game.addEvent` 后刷新 `god-view.md`、玩家私有日志、`replay.json` 与 `meta.json`，对局运行中也能查看最新进展。
- `meta.json` 增加 `phase` 字段，用于判断当前对局阶段或是否结束。
- `AgentJournal` 增加同步保护，避免并发投票阶段写记忆时与实时快照读取冲突。
- `README.md` 增加 `tail -f "$(ls -td matches/match-* | head -1)/god-view.md"` 的实时查看方式。
- 执行 `mvn test`，共 15 个测试通过。
- 执行 `mvn clean package`，成功重新生成 `target/multiagent-werewolf.jar`。
- 使用 mock LLM 再次执行整局，对局目录 `matches/match-20260601-194655-13dc069c` 成功生成，未发现“非法目标”“解析失败”等异常降级记录。
