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

## M8 — 规则补丁 PATCH-01 / PATCH-02

- 按 PATCH-02 优先级将女巫规则写死为代码常量：不能自救、不能同晚双药；移除 `application.yml` 中旧的女巫可配置项。
- `PromptBuilder` 为所有角色固定前置公共规则简介和角色简化规则；女巫 prompt 明确写入“不能自救”“不能同晚双药”“解药只救当晚被刀者，毒药无视解药”。
- 夜晚流程支持显式不执行技能：狼人可空刀、预言家可不查验、女巫可不用药，并在 god-view 私有块中按“① 狼人 / ② 预言家 / ③ 女巫”记录。
- 新增 `DAY_SHERIFF_ELECTION` 相位，第 1 天按“首夜行动 → 警长竞选 → 公布首夜死讯 → 发言 → 投票”推进。
- 实现警长竞选基础流程：上警、警上发言退水、警下投票、PK 重投、无人当选/警徽流失、警徽移交或撕毁。
- `VoteResolver` 支持警长 1.5 票权，按最高非整数分唯一性判断出局或平票。
- 每轮投票后生成玩家表现分表，写入 `god-view.md`；结构化分数同步写入 `meta.json.scoreboardByRound` 和 `replay.json`。
- 对局结束后生成 `review.md` 与 `review.json`；Qwen 返回不可解析 JSON 时生成规则侧兜底复盘。
- 新增/更新测试：女巫硬规则、警长票权、角色 prompt 规则前置。执行 `mvn test`：18 个测试通过。
- 执行 `mvn clean package` 并使用 mock LLM 跑通整局，对局目录 `matches/match-20260601-204002-483205ad` 包含 `god-view.md`、`meta.json`、`replay.json`、`review.md`、`review.json` 和各座位私有日志。

## M9 — README 重构升级

- 仿照成熟开源项目排版重写 `README.md`：补充徽章、目录导航、emoji 分区的功能说明。
- 新增命令行参数表、规则边界表、里程碑表，用 ASCII 流程图画出完整相位状态机。
- 补充 `application.yml` 配置说明、三层架构概览、输出产物目录树与常见问题（FAQ）。
- 明确"记忆在对局内跨轮保留、对局之间清空"的核心模型，并修正旧 README 遗漏或不准确的细节（`--rounds-cap`/`--llm` 参数、女巫硬规则、警长 1.5 票权、`review.md`/`review.json`、夜晚动作净化、`DASHSCOPE_API_KEY`）。

## M10 — 夜晚动作目标丢失修复

- 定位真实 Qwen 首夜"reasoning 写明目标、结构化字段 `targetSeat` 却为 null"的根因：`PromptBuilder` 给模型的 JSON 示例只含 `{"type":"WOLF_KILL"}`，从未告知 `targetSeat`/`poisonSeat`/`useAntidote` 等字段，`taskRule` 也仅对 `SHERIFF_VOTE`/`BADGE_TRANSFER` 说明目标字段，导致模型把座位号只写进 reasoning，动作退化为意外空刀/弃验。
- 新增 `jsonSchemaHint`：按动作类型输出含真实字段的 JSON 示例；`systemPrompt` 明确要求字段按给定键名填写、座位号不得只写进 reasoning。
- 扩充 `taskRule`：为 `WOLF_KILL`、`SEER_CHECK`、`WITCH`、`HUNTER_SHOOT`、`VOTE` 补充目标字段说明。
- `MockLLMClient` 不受影响（仍按"当前要求动作"识别并总是输出明确目标）。
- 新增 `PromptBuilderTest` 回归用例，断言夜晚动作 prompt 携带 `targetSeat` 字段。执行 `mvn test`：19 个测试通过。
- 同步在 `README.md` 补充"每动作 JSON 字段 schema"的说明。

## M11 — 动作目标回填（prompt 之外的代码兜底）

- 现象：M10 强化 prompt 后，真实 Qwen 首夜仍偶发"reasoning 写明刀/查目标、`targetSeat` 却为 null"，退化为非预期空刀/弃验（对局目录如 `matches/match-20260601-235913-ba2a91a2`、`matches/match-20260601-233414-2ec4df6c`）。
- 根因：只靠 prompt 约束模型严格输出结构化字段并不可靠，prompt 只能降低概率、无法消除。
- 方案：在 `ActionParser` 增加代码兜底——当 `WOLF_KILL`/`SEER_CHECK`/`HUNTER_SHOOT`/`VOTE` 目标为空时，按"动作动词 + 座位号"（刀/砍/杀、查/验、投/放逐、选/锁定/针对 等，后接阿拉伯或中文数字）从 reasoning 回填，并用合法目标集校验。
- 防误判：仅在动作动词紧跟座位号时回填；"避免动 1、2、5、7 位"等无关座位不会被误当成目标；无动词锚定的真实空刀/弃验保持不变。
- 回填时写入"目标字段缺失，已据思考回填座位X"告警，落入私有系统备注，便于审计。
- 新增 3 个 `ActionParserTest` 用例（刀人回填、查验回填、避免误回填），并将 README 测试计数更新为 22。执行 `mvn test`：22 个测试通过。
