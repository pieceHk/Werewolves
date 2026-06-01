# 多 Agent 狼人杀游戏 · 开发文档 v2（Development Specification）

> 本文件是一份面向 AI 编程助手（Claude Code / Codex）的工程规格说明，同时充当 PRD、架构设计与实现指引。请按文档中的模块边界、接口签名、规则参数与里程碑**增量式**实现，每完成一个里程碑应能独立编译、测试通过。

---

## 0. 文档使用说明（How to use this document）

- **目标读者**：Claude Code / Codex 等 AI 编程代理，以及人类维护者。
- **使用方式**：将本文件作为根目录下的 `SPEC.md`，每次开发任务作为上下文输入；任务按「里程碑」拆分（见第 14 节）。
- **硬约束（必须遵守）**：
  1. 语言 **Java 17+**，构建工具 **Maven**。
  2. **仅后端，无任何前端**（无 HTML/JS/CSS）。对局过程通过**上帝视角日志文件 + 每 Agent 记忆文档 + JSON 复盘**呈现，REST/SSE 可选。
  3. LLM 使用 **Qwen3.7-Max（通义千问，DashScope OpenAI 兼容端点）**。**绝不硬编码 API Key**，一律从环境变量读取。
  4. **核心领域层（core）与 Agent 层（agent）保持框架无关**（纯 Java，不依赖 Spring）。Spring 仅用于装配。
  5. 一切随机性（角色分配、平票裁决等）须可由固定随机种子复现。

### 0.1 术语（Terminology，务必区分，避免实现歧义）
- **对局（Match）**：一场完整游戏，从开始到 `GAME_OVER`。**每个对局拥有独立的输出文件夹、全新的玩家（Agent）、独立且互不互通的记忆。**
- **轮（Round）**：一个对局**内部**的一个「夜晚 + 白天」循环。**同一对局内，Agent 的记忆跨轮累积**（第 2 轮能记得第 1 轮发生了什么）。
- 一句话：**记忆在「对局内跨轮保留」，在「对局之间清空」。**

---

## 1. 项目概述（Project Overview）

### 1.1 一句话描述
一个由多个 LLM Agent 各自扮演角色、自动进行完整对局的「9 人狼人杀」后端引擎与对局平台。

### 1.2 核心目标
- 实现规则完备、状态可复现的 **9 人「预女猎」** 狼人杀引擎。
- 实现多 Agent 调度系统，每名玩家由独立 LLM Agent 驱动，具备严格**信息隔离**。
- 接入 **Qwen3.7-Max** 作为推理后端，支持流式输出与结构化动作解析。
- 产出**人类可读的上帝视角日志**与**机器可读的复盘数据**。

### 1.3 范围（Scope）
- **只支持 9 人「预女猎」局**（3 狼、3 民、预言家、女巫、猎人）。不做 12 人局、不引入守卫。
- v1：8+1 全部为 Agent（即 9 个 Agent）自动对局。
- v2：**预留真人参与**——8 个 Agent + 1 个真人（仅项目维护者「我」可参与），真人同样随机分配角色，并为其维护「我的视角」日志。v2 此部分**先做接口与文件预留，不在 v1 实现**。

### 1.4 非目标（Out of Scope）
- 不做前端、不做账号/付费/排行榜、不做多模型并行竞技（接口可插拔即可）。

### 1.5 仓库信息
- GitHub：`https://github.com/pieceHk`
- 建议仓库名：`multiagent-werewolf`（可调整）
- 基础包名：`com.piecehk.werewolf`
- 许可证：`MIT`

---

## 2. 游戏规则（Game Rules · 9 人「预女猎」）

> 采用大陆最主流的 9 人标准局。技能交互的边界情形以「规则参数」显式定义，**实现时不得自行假设**。

### 2.1 角色定义（Roles）

| 角色 | 英文标识 | 阵营 | 技能描述 |
|---|---|---|---|
| 狼人 | `WEREWOLF` | 狼人阵营 | 夜晚共同选择击杀一名玩家；狼人之间互相可见身份 |
| 平民 | `VILLAGER` | 好人阵营 | 无技能，仅靠白天发言与投票 |
| 预言家 | `SEER` | 好人阵营（神职） | 每晚查验一名玩家，得知其为「好人」或「狼人」 |
| 女巫 | `WITCH` | 好人阵营（神职） | 解药 ×1、毒药 ×1；解药救当晚被刀者，毒药毒杀一人 |
| 猎人 | `HUNTER` | 好人阵营（神职） | 出局时可开枪带走一名玩家（受规则参数约束，见 2.5） |

### 2.2 局型（唯一预设 STANDARD_9）
```
STANDARD_9：
  3 × WEREWOLF
  3 × VILLAGER
  1 × SEER, 1 × WITCH, 1 × HUNTER
  共 9 人；座位号 1..9，开局随机分配角色（受 seed 控制可复现）
```

### 2.3 阶段流程（Phase Flow）
一个对局由若干「轮」组成，每轮含夜晚与白天。

**夜晚阶段（NIGHT）行动顺序（严格按序）：**
1. `WEREWOLF` 集体商讨并选择唯一击杀目标（多狼调度见 7.4）。
2. `SEER` 查验一名玩家，私下获知阵营结果。
3. `WITCH` 获知当晚被刀者，决定是否使用解药 / 毒药。

**白天阶段（DAY）流程：**
1. 公布夜晚死亡名单（`ANNOUNCE`）。
2. 死亡玩家发表遗言（`LAST_WORDS`，由规则参数控制；含猎人开枪结算）。
3. 存活玩家依**固定座位顺序严格串行**发言（`DISCUSS`，见 7.4）。
4. 投票放逐（`VOTE`）：每名存活玩家投一票或弃票。
5. 公布放逐结果并由被放逐者发表遗言（含猎人开枪结算）。

**每次有玩家出局后立即检测胜负（见 2.6）。**

### 2.4 夜晚结算顺序（Night Settlement）
```
输入：
  wolfTarget   K     (可空，狼人弃刀则空)
  witchSave    saved (boolean，女巫是否对 K 用解药)
  witchPoison  P     (可空，女巫毒杀目标)

结算逻辑：
  1. 若 K 非空：
       若 saved == true → K 存活
       否则             → K 死亡 → 加入 deaths
  2. 若 P 非空：P 死亡 → 加入 deaths（毒药无视解药）
  3. 返回 deaths
（9 人局无守卫，故无「同守同救」逻辑）
```

### 2.5 规则参数（Rule Parameters · 必须实现为可配置）

| 参数 | 默认值 | 说明 |
|---|---|---|
| `RULE_WIN_CONDITION` | `KILL_SIDE`（屠边） | 屠边：杀光所有神职 **或** 杀光所有平民则狼胜；屠城：杀光全部好人才狼胜 |
| `RULE_WITCH_SELF_SAVE` | `FIRST_NIGHT_ONLY` | 女巫对自己用解药：从不 / 仅首夜 / 总是 |
| `RULE_WITCH_BOTH_POTIONS_SAME_NIGHT` | `false` | 女巫能否同一晚同时用解药与毒药 |
| `RULE_HUNTER_SHOOT_WHEN_POISONED` | `false` | 猎人被女巫毒杀时能否开枪（默认不能） |
| `RULE_HUNTER_SHOOT_WHEN_VOTED` | `true` | 猎人被投票放逐时能否开枪 |
| `RULE_ALLOW_FIRST_NIGHT_LAST_WORDS` | `true` | 首夜死亡玩家是否可发遗言 |
| `RULE_VOTE_TIE` | `REVOTE_THEN_NONE` | 平票：仅平票者重投一次，再平票则本轮无人出局 |
| `RULE_MAX_SPEECH_CHARS` | `100` | **单次发言字数上限（汉字计），适用于白天发言、遗言、狼人频道发言；见 7.5/7.6** |

### 2.6 胜负判定（Win Conditions）
```
aliveWolves     = 存活狼人数
aliveGods       = 存活神职数（SEER/WITCH/HUNTER）
aliveVillagers  = 存活平民数

好人胜利：aliveWolves == 0
狼人胜利（KILL_SIDE/屠边）：aliveGods == 0  或  aliveVillagers == 0
狼人胜利（KILL_ALL/屠城）：aliveGods + aliveVillagers == 0
同时满足时，好人胜利优先（狼全灭即好人赢）。
每次死亡结算后立即判定；分出胜负即进入 GAME_OVER。
```

---

## 3. 技术栈（Tech Stack）

| 维度 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17（LTS） | record、sealed interface、switch 模式匹配、增强枚举 |
| 构建 | Maven 3.9+ | 单模块起步 |
| 装配框架 | Spring Boot 3.2+ | 仅 DI/配置/可选 REST；**core 与 agent 包不得引入 Spring** |
| HTTP 客户端 | `java.net.http.HttpClient`（内置） | 调用 Qwen；流式可选 OkHttp 4.x |
| JSON | Jackson 2.x | 领域对象序列化、LLM 结构化输出解析 |
| 异步并发 | `CompletableFuture` + 自定义线程池 | 仅夜晚独立角色 / 投票阶段并发；白天发言串行 |
| 日志 | SLF4J + Logback（系统日志） | 与「上帝视角对局日志」分离，见第 10 节 |
| 测试 | JUnit 5 + Mockito + AssertJ | LLM 必须可 mock，测试不联网 |
| 配置 | Spring `application.yml` + 环境变量 | API Key 走环境变量 |

---

## 4. 系统架构（Architecture）

### 4.1 分层
```
┌──────────────────────────────────────────────────────┐
│ 接入层 api/      CLI Runner · REST/SSE(可选)                     │
├──────────────────────────────────────────────────────┤
│ 游戏引擎层 game/ GameService · PhaseScheduler · MatchManager      │
│                  PlayerController(LLM/Human 抽象, 见 13)          │
├──────────────────────────────────────────────────────┤
│ Agent 层 agent/  AgentOrchestrator · AgentContext · ContextBuilder │
│                  PromptBuilder · ActionParser · AgentJournal · LLMClient │
├──────────────────────────────────────────────────────┤
│ 核心领域层 core/ Game · Player · Role · Phase · 状态机 · 规则引擎 · 事件 │
├──────────────────────────────────────────────────────┤
│ 基础设施 infra/  对局输出(上帝日志/Agent日志/复盘) · 配置 · 线程池      │
└──────────────────────────────────────────────────────┘
```
依赖方向单向向下：`api → game → agent → core`；`infra` 被各层按需使用；**core 不依赖任何上层。**

### 4.2 包结构（Package Layout）
```
com.piecehk.werewolf
├── core
│   ├── model          // Game, Player, Role/RoleType, Camp, GamePhase, PlayerStatus, CheckResult
│   ├── statemachine   // GameStateMachine, PhaseTransition
│   ├── rule           // RuleConfig, RuleEngine, WinChecker, NightResolver, VoteResolver
│   └── event          // GameEvent(密封接口)、各事件、Visibility、EventBus
├── agent
│   ├── AgentOrchestrator      // 阶段调度（夜晚/白天串行 · 投票并发）
│   ├── AgentContext           // 单 Agent 只读信息视图（信息隔离核心）
│   ├── ContextBuilder         // 按角色过滤构造 AgentContext
│   ├── PromptBuilder          // 角色 + 阶段 → prompt（含发言限长约束）
│   ├── ActionParser           // LLM JSON → AgentAction（含容错与限长截断）
│   ├── AgentJournal           // 单 Agent 的本对局记忆/思考文档（落盘，见 10.3）
│   ├── action                 // AgentAction(密封接口): Speak/Vote/WolfKill/SeerCheck/Witch/HunterShoot/NoOp
│   └── llm                    // LLMClient(接口) · QwenLLMClient · MockLLMClient · dto
├── game
│   ├── GameService            // 一个对局的生命周期编排
│   ├── PhaseScheduler         // 阶段推进
│   ├── MatchManager           // 多对局管理（每对局独立文件夹/记忆，见 10.1）
│   └── player                 // PlayerController(接口) · LLMPlayerController · HumanPlayerController(v2 预留)
├── api
│   ├── cli                    // MatchRunner（main 入口，跑完整对局）
│   └── rest                   // (可选) MatchController + SSE
├── infra
│   ├── output                 // GodViewLogWriter · AgentJournalWriter · ReplayWriter · MatchWorkspace
│   └── config                 // 线程池、Jackson、Qwen 配置 Bean
└── WerewolfApplication.java
```

---

## 5. 核心领域模型（Domain Model）

> 全部位于 `core.model`，尽量不可变。**领域对象不得含任何 LLM 调用或文件 IO。**

### 5.1 枚举
```java
public enum Camp { WEREWOLF, GOOD }

public enum RoleType {
    WEREWOLF(Camp.WEREWOLF),
    VILLAGER(Camp.GOOD),
    SEER(Camp.GOOD),
    WITCH(Camp.GOOD),
    HUNTER(Camp.GOOD);
    private final Camp camp;
    // getCamp(); isGod() → SEER/WITCH/HUNTER 为 true
}

public enum GamePhase { PREPARING, NIGHT, DAY_ANNOUNCE, DAY_DISCUSS, DAY_VOTE, GAME_OVER }
public enum PlayerStatus { ALIVE, DEAD }
public enum CheckResult { GOOD, WEREWOLF }
public enum DeathCause { WOLF_KILL, WITCH_POISON, VOTE_OUT, HUNTER_SHOT }
```

### 5.2 实体
```java
public final class Player {
    int seatNo;             // 1..9
    String name;            // 如 "Player-3"
    RoleType role;
    PlayerStatus status;
    String agentId;
    boolean human;          // v2 预留：是否为真人玩家（v1 恒为 false）
    boolean isAlive();
}

public final class WitchState { boolean antidoteAvailable; boolean poisonAvailable; }

public final class Game {                 // 一个对局的聚合根
    String matchId;
    GamePhase phase;
    int roundNo;                          // 当前轮，从 1 开始
    List<Player> players;
    RuleConfig ruleConfig;
    WitchState witchState;
    List<GameEvent> eventLog;             // 全量事件（含 private），上帝视角可读
    long randomSeed;
    // 查询：alivePlayers()/playersByRole()/aliveCountByCamp() ...
}
```

### 5.3 夜晚行动收集体
```java
public final class NightActions {
    Integer wolfTarget;   // seatNo, 可空（弃刀）
    Integer seerTarget;   // seatNo
    boolean witchSave;
    Integer witchPoison;  // seatNo, 可空
}
```

---

## 6. 游戏引擎（Game Engine）

### 6.1 GameStateMachine（`core.statemachine`）
唯一可变更 `Game` 状态的入口。合法转换：
```
PREPARING → NIGHT
NIGHT → DAY_ANNOUNCE → DAY_DISCUSS → DAY_VOTE → NIGHT  // 下一轮
任意阶段 → GAME_OVER
```
非法转换抛 `IllegalPhaseTransitionException`。

### 6.2 RuleEngine（`core.rule`，纯函数式，无副作用）
- `NightResolver.resolve(Game, NightActions) -> NightOutcome`：按 2.4 计算死亡集合，并校验女巫自救（`RULE_WITCH_SELF_SAVE`）与同晚双药（`RULE_WITCH_BOTH_POTIONS_SAME_NIGHT`）。
- `VoteResolver.resolve(Map<Integer,Integer> votes, RuleConfig) -> VoteOutcome`：统计票数、弃票、平票（`RULE_VOTE_TIE`）。
- `WinChecker.check(Game) -> Optional<Camp>`：按 2.6 判定。

### 6.3 PhaseScheduler（`game`）
驱动一轮内各阶段顺序推进，在每阶段调用 `AgentOrchestrator` 收集行动 → `RuleEngine` 结算 → `GameStateMachine` 应用 → `EventBus` 广播 → 写入上帝日志与 Agent 日志。
```
runRound(game):
  // NIGHT（夜晚行动全程为 PRIVATE 事件，仅授权角色可见，上帝日志可读）
  nightActions = orchestrator.collectNightActions(game)
  outcome = ruleEngine.resolveNight(game, nightActions)
  apply(outcome); publishEvents(); checkWin()
  // DAY
  to(DAY_ANNOUNCE); announce(outcome.deaths)           // PUBLIC
  handleLastWords(outcome.deaths)                       // 含猎人开枪
  checkWin()
  to(DAY_DISCUSS); orchestrator.collectDiscussion(game) // 严格串行，PUBLIC
  to(DAY_VOTE); votes = orchestrator.collectVotes(game) // 并发产出，PUBLIC
  voteOutcome = ruleEngine.resolveVote(votes)
  apply(voteOutcome); handleExileLastWords()            // 含猎人开枪
  checkWin()
  to(NIGHT 或 GAME_OVER)
```

### 6.4 EventBus 与事件（`core.event`）
`GameEvent` 为密封接口；每个事件带 `Visibility` 与可选 `audience`（可见座位集合）：
```
Visibility: PUBLIC | PRIVATE
事件示例：
  GameStarted, RoleAssigned(PRIVATE/上帝), PhaseChanged(PUBLIC),
  WolfDiscussion(PRIVATE, audience=狼座位, text),
  WolfKillDecided(PRIVATE, target),
  SeerChecked(PRIVATE, audience=预言家, target, result),
  WitchActed(PRIVATE, audience=女巫, save, poison),
  PlayerDied(PUBLIC, seat, DeathCause),
  PlayerSpoke(PUBLIC, seat, text),
  VoteCast(PUBLIC, voter, target),
  PlayerExiled(PUBLIC, seat),
  HunterShot(PUBLIC, hunter, target),
  GameOver(PUBLIC, winner)
```
`ContextBuilder` 据 `visibility/audience` 与玩家角色过滤；**上帝视角日志读取全部事件（含 PRIVATE），但 Agent 之间严格隔离**（见 7.2 与 10.2）。

---

## 7. Agent 系统（Multi-Agent System）

### 7.1 设计要点
- 每名玩家对应一个逻辑 Agent（`agentId`），共享同一 `LLMClient` 实例，但各自拥有**独立的 `AgentContext` 与 `AgentJournal`**。
- **信息隔离是正确性的核心**：构造每个 Agent 的 prompt 时，只能注入其有权看到的信息。
- **每个对局开局都是全新玩家**：Agent 记忆只在本对局内跨轮累积，对局结束清空（见 0.1、10.1）。

### 7.2 AgentContext 与 ContextBuilder（信息隔离核心）
`AgentContext` 是「某玩家在某时刻可见信息」的只读视图，至少含：
- 自己的座位、角色、阵营。
- 公开信息：存活名单、历史公开发言、历史投票、公开死亡/出局记录、当前轮次与阶段。
- 角色私有信息：
  - 狼人：队友座位、历次狼人商讨记录、历史刀人目标。
  - 预言家：自己历次查验目标与结果。
  - 女巫：解药/毒药剩余、当晚被刀者（仅女巫行动阶段提供）。
- 自己的私有记忆（来自本 Agent 的 `AgentJournal`）。

`ContextBuilder.build(game, player, phaseContext) -> AgentContext`：遍历 `game.eventLog`，按事件 `visibility/audience` 与角色过滤。**严禁把非授权信息（如狼队友身份、预言家结果）注入平民上下文。** 该逻辑必须有专门隔离单测（见 15.2）。

### 7.3 AgentAction（`agent.action`，密封接口）
```java
public sealed interface AgentAction permits
    SpeakAction, VoteAction, WolfKillAction, SeerCheckAction,
    WitchAction, HunterShootAction, NoOpAction {}

record SpeakAction(String speech) implements AgentAction {}        // speech 受 RULE_MAX_SPEECH_CHARS 限制
record VoteAction(Integer targetSeat /*null=弃票*/) implements AgentAction {}
record WitchAction(boolean useAntidote, Integer poisonSeat) implements AgentAction {}
// ……
```
每个动作附带 LLM 隐藏推理 `reasoning`：**只写入该 Agent 的 `AgentJournal`，不进上帝日志、不公开给其他 Agent**（见 10.2/10.3）。

### 7.4 AgentOrchestrator（调度核心 · 已按新规则修订）
- **白天发言（DAY_DISCUSS）— 严格串行**：按固定座位顺序，逐个调用 Agent；**后发言者的 `AgentContext` 必须包含本轮已落库的先发言者发言内容**，即每条发言产出并写入 `eventLog`/上帝日志后，再为下一位构造上下文。不并发。
- **夜晚狼人商讨（NIGHT-WEREWOLF）— 多轮串行 + 收敛**：最多 K 轮（默认 2）串行发言，每轮每个狼人可见队友本轮已发表意见；最后一轮各狼提交击杀目标，取众数；平票由首座狼人或随机种子裁决。商讨为 `PRIVATE`（audience=狼座位）。
- **SEER / WITCH 夜晚 — 独立串行**：各自单次调用，互不可见，均为 `PRIVATE`。
- **投票（DAY_VOTE）— 可并发**：所有存活玩家并发产出投票，统一结算（投票阶段无「看到他人投票后再改」需求，故可并发）。
- 并发任务使用 `infra` 提供的固定线程池；每请求设超时、整体设超时。
- **路由到 PlayerController**：Orchestrator 不直接调 LLM，而是调用每个玩家的 `PlayerController.decide(...)`；v1 全部为 `LLMPlayerController`，v2 真人为 `HumanPlayerController`（见 13）。

### 7.5 PromptBuilder（含发言限长）
- `build(agentContext, requiredActionType) -> List<ChatMessage>`：产出 system + user 消息。
- system：游戏规则摘要、该玩家角色与目标、**输出格式约束（严格 JSON）**、**发言长度约束**。
- **发言限长要求**：凡涉及发言（`SpeakAction`：白天发言、遗言、狼人频道发言），prompt 必须明确：「发言须紧扣你的判断/意图展开，**不超过 `RULE_MAX_SPEECH_CHARS`（默认 100）个汉字**，禁止冗长铺陈」。
- **结构化输出契约**（仅返回 JSON，无多余文本/代码围栏）：
  ```json
  {
    "reasoning": "你的隐藏推理（仅写入你自己的记忆文档，不公开）",
    "action": {
      "type": "SPEAK | VOTE | WOLF_KILL | SEER_CHECK | WITCH | HUNTER_SHOOT | NOOP",
      "targetSeat": 3,
      "speech": "发言内容（仅 SPEAK；≤100 字）",
      "useAntidote": false,
      "poisonSeat": null
    }
  }
  ```
- 附录 A 提供各角色 system prompt 模板。

### 7.6 ActionParser（含容错与限长截断）
- 解析 JSON 为 `AgentAction`，并做容错：剥离 Markdown 围栏（```json）、提取首个 JSON 对象、字段缺省降级。
- **发言长度校验**：对 `speech` 按**码点（汉字）计数**，超过 `RULE_MAX_SPEECH_CHARS` 时**截断到上限并记录告警**（同时写入上帝日志的备注）。
- 非法/越界 `targetSeat` → 降级为弃票 / NoOp 并告警。
- 完全解析失败 → 按策略重试一次（追加「请仅输出 JSON 且发言≤100字」纠正提示）→ 仍失败则回退安全动作（弃票/不用药）。

### 7.7 AgentJournal（每 Agent 本对局记忆/思考文档）
- 每个 `agentId` 维护一份**本对局**记忆，落盘为 `matches/{matchId}/agents/seat-{N}.md`（见 10.3）。
- 内容：该 Agent 的 `reasoning` 累积、对各座位的身份猜测、关键判断笔记。
- 每次该 Agent 行动后**追加**其 reasoning；下次行动时由 PromptBuilder 读取并作为「你的笔记」注入，提升一致性。
- **对局之间不互通**：新对局创建全新 journal（见 10.1）。
- 说明：该文档同时充当本 Agent 的记忆持久层，并非冗余设计——它既满足「每 Agent 独立思考文档」的需求，又是 prompt 记忆注入的数据来源。

---

## 8. Qwen API 集成（LLM Integration）

### 8.1 信息隔离与 API 调用模型（关键说明）
- **无需多个 API Key。** Qwen 的 `/chat/completions` 端点**无状态**：每次调用只包含本次 messages，服务端不在调用间保留任何记忆。
- **调用模型 = 单 Key + 多次独立调用**：每个 Agent 在每个决策点发起一次独立请求，**各自携带被 `ContextBuilder` 过滤后的隔离上下文**（自身 system prompt + 自身可见局势 + 自身 journal）。
- **隔离责任在应用层**：只要 `ContextBuilder` 不注入越权信息，9 次调用之间天然零串扰。多个 Key 仅对绕过限流/配额有用，对隔离无意义。
- 含义：跨 Agent 的「思考与信息不泄漏」由第 7.2 节的隔离 + 第 10.2 节的日志可见性约束共同保证，**不依赖 API 层任何特性**。

### 8.2 接口抽象
```java
public interface LLMClient {
    String chat(List<ChatMessage> messages, ChatOptions options);             // 非流式
    void chatStream(List<ChatMessage> messages, ChatOptions options,
                    Consumer<String> onDelta, Runnable onComplete);           // 流式
}
// ChatMessage{ role: "system"|"user"|"assistant", content }
// ChatOptions{ model, temperature, maxTokens, timeout, jsonMode }
```

### 8.3 QwenLLMClient（DashScope OpenAI 兼容实现）
- **端点（配置化二选一）**：
  - 中国（北京）：`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
  - 国际（新加坡）：`https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions`
- **鉴权**：HTTP 头 `Authorization: Bearer ${DASHSCOPE_API_KEY}`。
- **模型名**：配置项 `qwen.model`，默认占位 `qwen3-max`；**拿到 access token 后替换为对应的 Qwen3.7-Max preview 模型 ID（preview 阶段以官方控制台/文档为准，故必须可配置、不可写死）**。
- **请求体**：
  ```json
  { "model": "${qwen.model}",
    "messages": [ {"role":"system","content":"..."}, {"role":"user","content":"..."} ],
    "temperature": 0.8, "stream": false }
  ```
  思考模式 / 大上下文等 Qwen 特有参数封装在 `ChatOptions` 扩展字段，按文档透传。
- **响应解析**：取 `choices[0].message.content`；流式解析 SSE `data:` 增量 `delta.content`，遇 `[DONE]` 结束。
- **健壮性**：连接/读取超时可配（默认 10s/60s）；对 5xx 与网络异常指数退避重试（默认 3 次）；客户端并发上限 + 令牌桶防 429；失败抛 `LLMException` 由 Orchestrator 兜底；**绝不记录 API Key，请求日志脱敏**。

### 8.4 配置读取
- API Key 仅从环境变量 `DASHSCOPE_API_KEY` 读取；其余走 `application.yml`（见第 11 节）。

---

## 9. 接入层 / 入口（API / Entry Point）

### 9.1 CLI Runner（必做）
`api.cli.MatchRunner`（含 `main`）：加载配置 → 运行一个完整对局：
- 控制台实时打印阶段、公开发言、死亡/出局、最终胜负。
- 为本对局创建独立文件夹并写入上帝日志、Agent 日志、复盘（见第 10 节）。
- 命令行参数：`--seed 12345`、`--rounds-cap 20`（防失控）、`--out ./matches`。

### 9.2 REST + SSE（可选）
- `POST /api/matches`：异步开始一个对局，返回 `matchId`。
- `GET /api/matches/{id}/events`（SSE）：仅推送 `PUBLIC` 事件。
- `GET /api/matches/{id}/replay`：结束后返回复盘 JSON。
- **安全**：REST 不接受任何 API Key 入参；**SSE 绝不推送 PRIVATE 事件**（狼队友、查验等）。

---

## 10. 对局运行的呈现、日志与持久化（Run Presentation, Logging & Persistence）

> 本节是本次修订的重点。目标：**无前端**也能清晰复看对局；上帝视角可读全部信息，但 Agent 之间仍严格隔离。

### 10.1 每对局独立文件夹（Per-Match Workspace）
每个对局开始时由 `MatchManager` + `infra.output.MatchWorkspace` 创建独立目录，**所有内容仅存放于该目录**；**对局之间记忆不互通、玩家全新**。
```
matches/
└── match-{yyyyMMdd-HHmmss}-{shortId}/
    ├── god-view.md       # 上帝视角主日志（人类可读，段落鲜明，含 public+private）
    ├── agents/
    │   ├── seat-1.md     # 座位1 的本对局记忆/思考文档（仅该 Agent 的 reasoning 与笔记）
    │   ├── seat-2.md
    │   └── ... seat-9.md
    ├── human-view.md     # v2 预留：真人「我」的视角日志（仅“我”可见的信息）
    ├── replay.json       # 结构化复盘（机器可读）
    └── meta.json         # matchId, seed, preset, seating(上帝视角角色表), winner, totalRounds
```

### 10.2 上帝视角日志 god-view.md（GodViewLogWriter）
要求：**段落鲜明、便于观看、上帝视角（可读全部 public 与 private）**。

- **文件开头记录所有角色的 system prompt**（满足「所有角色 system prompt 在此 log 记录」）。
  - 至少记录每个座位的「角色级 system prompt」；如某阶段使用了不同的任务级 prompt，可在对应事件块以「任务提示」简述。
  - **不在此记录 Agent 的 `reasoning`（思考内容）**——思考写入各自 `agents/seat-N.md`（见 10.3）。
- **此后按时间顺序记录事件块**；每个事件块的开头必须注明：
  1. **可见性**：`[PUBLIC]` 或 `[PRIVATE]`（PRIVATE 还需注明可见者，如「仅狼人(2,5,7)可见」）。
  2. **发布者**：座位号 + 角色（上帝视角可标注真实角色）。
  3. **轮次与阶段**。
- **所有 PRIVATE 事件**（如夜晚狼人商讨、预言家查验、女巫用药）**依旧对其他 Agent 隔离**（不进入他人 `AgentContext`），但**完整写入 god-view.md 供上帝视角阅读**。

god-view.md 段落格式示例（实现以此风格为准）：
```
# 对局 god-view 日志  match-20260601-2130-a1b2
seed=12345 · preset=STANDARD_9
座位角色（上帝视角）：1=村民 2=狼 3=预言家 4=村民 5=狼 6=女巫 7=狼 8=猎人 9=村民

## 各角色 System Prompt
### 座位2（狼人）
<完整 system prompt 文本>
### 座位3（预言家）
<完整 system prompt 文本>
…（共 9 个座位）…

────────────────────────────────────────────
【第 1 轮 · 夜晚 · 狼人商讨】 [PRIVATE · 仅狼人(2,5,7)可见]  发布：狼人频道
────────────────────────────────────────────
座位2(狼)：建议刀3，他像预言家。
座位5(狼)：同意。
座位7(狼)：刀3。
决议：击杀 座位3

────────────────────────────────────────────
【第 1 轮 · 夜晚 · 查验】 [PRIVATE · 仅预言家(3)可见]  发布：座位3(预言家)
────────────────────────────────────────────
查验 座位2 → 结果：狼人

────────────────────────────────────────────
【第 1 轮 · 夜晚 · 女巫】 [PRIVATE · 仅女巫(6)可见]  发布：座位6(女巫)
────────────────────────────────────────────
今晚被刀：座位3 ；决定：使用解药救3，不使用毒药

────────────────────────────────────────────
【第 1 轮 · 白天 · 公布】 [PUBLIC]  发布：系统
────────────────────────────────────────────
昨夜平安夜，无人死亡

────────────────────────────────────────────
【第 1 轮 · 白天 · 发言】 [PUBLIC]  发布：座位1(村民)
────────────────────────────────────────────
我是好人，先听大家发言再判断。（≤100字）
…
```
> 备注：若 ActionParser 触发了发言截断或动作降级，应在对应事件块追加一行 `※ 系统备注：发言超长已截断 / 非法目标已降级为弃票`。

### 10.3 每 Agent 记忆/思考文档 agents/seat-N.md（AgentJournalWriter）
- 记录该 Agent 的 `reasoning`（隐藏推理）累积、身份猜测、关键笔记，**本对局内跨轮累积**。
- 每次行动后追加；作为该 Agent 下次 prompt 的「你的笔记」来源（见 7.7）。
- **隔离**：seat-N.md 仅服务于座位 N 自己；**绝不被其他 Agent 读取**；上帝视角可查看（用于分析），但其内容**不进入 god-view.md 主时间线**。
- 追加格式示例：
  ```
  ## 第1轮·夜晚（我是预言家·座位3）
  reasoning：座位2发言不多，先验最可疑，决定查2。
  查验结果记录：座位2=狼。
  笔记：明天可考虑跳预言家报2。
  ```

### 10.4 复盘 replay.json（ReplayWriter，机器可读）
```
matchId, preset, seed, ruleConfig,
seating: [ {seat, role} ],
rounds: [ { roundNo,
   night:{ wolfDiscussion:[...], wolfTarget, seerCheck:{target,result}, witch:{save,poison}, deaths },
   day:{ announcements, speeches:[{seat,text}], votes:[{voter,target}], exiled, hunterShot } } ],
winner, totalRounds,
agentTrace: [ {seat, round, phase, action, reasoning} ]   // 含隐藏推理，供分析
```
- v1：内存维护 `Game`，结束后写 replay.json。后续如需 DB（PostgreSQL）再以 `ReplayRepository` 抽象扩展。

---

## 11. 配置（Configuration）

`src/main/resources/application.yml` 示例：
```yaml
werewolf:
  preset: STANDARD_9
  seed: 0                 # 0=随机；非 0=固定种子（可复现）
  rounds-cap: 20
  output-dir: ./matches   # 每对局在此下新建独立文件夹
  rules:
    win-condition: KILL_SIDE
    witch-self-save: FIRST_NIGHT_ONLY
    witch-both-potions-same-night: false
    hunter-shoot-when-poisoned: false
    hunter-shoot-when-voted: true
    allow-first-night-last-words: true
    vote-tie: REVOTE_THEN_NONE
    max-speech-chars: 100        # 单次发言汉字上限

  # v2 预留：真人参与
  human:
    enabled: false               # v1 恒 false
    seat: null                   # null=随机分配座位与角色；否则指定座位（角色仍随机）

qwen:
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen3-max               # 拿到 token 后替换为对应模型 ID
  temperature: 0.8
  timeout-connect-ms: 10000
  timeout-read-ms: 60000
  max-retries: 3
  max-concurrency: 6
  # api-key 不在此；从环境变量 DASHSCOPE_API_KEY 读取
```
环境变量：`export DASHSCOPE_API_KEY=sk-xxxx`（由用户后续提供，切勿入库）。

---

## 12. 项目结构（Full Directory Layout）
```
multiagent-werewolf/
├── pom.xml
├── README.md
├── SPEC.md                         # 本文件
├── LICENSE
├── .gitignore
├── matches/                        # 运行时生成，每对局一个子文件夹（git 忽略）
└── src
    ├── main
    │   ├── java/com/piecehk/werewolf
    │   │   ├── WerewolfApplication.java
    │   │   ├── core/{model,statemachine,rule,event}/...
    │   │   ├── agent/{action,llm,...}/...
    │   │   ├── game/{player,...}/...
    │   │   ├── api/{cli,rest}/...
    │   │   └── infra/{output,config}/...
    │   └── resources/application.yml
    └── test/java/com/piecehk/werewolf
        ├── core/rule/NightResolverTest.java
        ├── core/rule/WinCheckerTest.java
        ├── core/rule/VoteResolverTest.java
        ├── agent/ContextBuilderIsolationTest.java
        ├── agent/ActionParserTest.java            // 含发言截断/降级
        └── game/MatchServiceIntegrationTest.java  // 用 MockLLMClient 跑完整对局
```
`.gitignore` 关键项：`target/`、`matches/`、`.env`、`*.log`、IDE 文件、**任何含密钥的文件**。

---

## 13. v2 真人参与预留（Human Participation · Reserved，先做接口与文件预留，不在 v1 实现）

### 13.1 目标
- **只支持一名真人**（项目维护者「我」）参与：**8 个 Agent + 1 个真人 = 9 人**。
- 真人**同样随机分配角色**（与 Agent 一起参与随机分配，受 seed 控制）。
- 为真人维护一份「我的视角」日志 `human-view.md`，**信息可见性与该真人玩家的角色完全一致**（即与同角色 Agent 的 `AgentContext` 同等隔离），供真人据以决策。

### 13.2 抽象接口（v1 即定义，便于 v2 接入）
```java
public interface PlayerController {
    AgentAction decide(AgentContext context, ActionType required);
    boolean isHuman();
}

// v1 实现：包装一个 LLM Agent
public final class LLMPlayerController implements PlayerController {
    // 调 PromptBuilder + LLMClient + ActionParser，写 AgentJournal
    public boolean isHuman() { return false; }
}

// v2 预留：真人控制器（v1 仅留 stub / 抛 UnsupportedOperationException）
public final class HumanPlayerController implements PlayerController {
    // decide(): 不调 LLM，而是：
    //   1) 将 context 渲染追加到 human-view.md（仅“我”可见的信息）
    //   2) 通过 CLI（或后续 REST）读取真人输入，构造 AgentAction
    public boolean isHuman() { return true; }
}
```
`AgentOrchestrator` 统一通过 `PlayerController.decide(...)` 取得动作；**v1 全部为 `LLMPlayerController`**；v2 将「我」所在座位替换为 `HumanPlayerController`。

### 13.3 human-view.md（v2）
- 与 god-view.md 不同：**human-view.md 受信息隔离约束**，只写入「我」这一角色有权看到的内容（公开信息 + 我的角色私有信息 + 我的历史动作）。
- 每个需要「我」决策的节点前，先把当前可见局势渲染进 human-view.md，再等待我的输入。
- v1：仅创建空文件占位 + 在 `MatchWorkspace` 预留写入接口；不产生真实交互。

### 13.4 v1 需要预留的最小改动
- `Player.human` 字段（已在 5.2 预留）。
- `PlayerController` 接口与 `LLMPlayerController` 实现；`HumanPlayerController` 留 stub。
- 配置 `werewolf.human.enabled/seat`（已在第 11 节预留，默认关闭）。
- `MatchWorkspace` 预留 `human-view.md` 写入入口。

---

## 14. 开发里程碑（Milestones / Roadmap）

> 每个里程碑结束都应可编译、测试绿、可独立演示。建议逐个实现并提交。

- **M1 — 领域模型与状态机**：`core.model` 全类型（含 `Player.human` 预留）、`GameStateMachine`、`EventBus` 与带 `Visibility/audience` 的事件。阶段转换单测。
- **M2 — 规则引擎**：`NightResolver`（无守卫）、`VoteResolver`、`WinChecker` 与全部规则参数。重点单测：夜晚结算、女巫自救/同晚双药限制、猎人开枪条件、平票、胜负判定。
- **M3 — LLM 客户端**：`LLMClient`、`QwenLLMClient`（非流式优先）、DTO、重试/超时/限流；可注入的 `MockLLMClient`（脚本化返回动作）。
- **M4 — Agent 层**：`AgentContext`、`ContextBuilder`（信息隔离单测）、`PromptBuilder`（含发言限长）、`ActionParser`（容错 + 发言截断 + 降级单测）、`AgentJournal`、`AgentOrchestrator`（**白天严格串行**、夜晚狼人串行、投票并发）。
- **M5 — 引擎编排 + CLI + 日志**：`PhaseScheduler`、`GameService`、`MatchManager`、`MatchRunner`；`infra.output`（**god-view.md 上帝日志**、**agents/seat-N.md 记忆文档**、replay.json、meta.json、**每对局独立文件夹**）。用 `MockLLMClient` 跑通整局并产出全部文件。集成测试。
- **M6 — 接入真实 Qwen**：用真实 `DASHSCOPE_API_KEY` 跑通端到端；加流式输出（发言可流式写入日志）。
- **M7（可选）— REST + SSE**：暴露创建对局与公开事件流（绝不暴露 PRIVATE）。
- **M8 — v2 真人参与（预留实现）**：落地 `PlayerController` 抽象与 `LLMPlayerController`；`HumanPlayerController` 接通 CLI 输入 + `human-view.md` 渲染（信息隔离）。仅一名真人，角色随机。

---

## 15. 测试策略（Testing）

### 15.1 总则
- `core` 与 `agent` 纯逻辑必须有单测；LLM 一律 `MockLLMClient` 注入，**测试不得联网**。
- 固定 `seed` 保证角色分配、平票裁决等可复现。

### 15.2 关键必测场景
- 夜晚结算：弃刀；解药救人；毒药致死（无视解药）；女巫首夜自救 vs 之后自救被拒；同晚双药开关。
- 猎人：被狼刀出局可开枪；被投票出局可开枪；被毒杀不可开枪（默认）。
- 投票：正常多数；弃票；平票重投；二次平票无人出局。
- 胜负：屠边（杀光神/杀光民）、屠城、狼全灭好人胜。
- **信息隔离（最高优先级）**：平民 `AgentContext` **不含**狼队友身份与预言家查验结果；狼人含队友；预言家含自身查验历史。
- **白天串行可见性**：第 k 个发言者的 context 必须包含本轮前 k-1 位的发言。
- **发言限长**：`speech` 超过 100 字时被截断且产出告警；prompt 含限长指令。
- ActionParser：带围栏 JSON、非法/越界目标、缺字段、完全非 JSON（触发重试与兜底）。
- **日志与隔离一致性**：god-view.md 含某条 PRIVATE 狼人商讨；同一内容**不出现在**非狼座位的 `AgentContext` 中。

### 15.3 集成测试
- `MatchServiceIntegrationTest`：脚本化 `MockLLMClient` 驱动 9 人局跑到 `GAME_OVER`，断言事件序列合法、胜负正确、**对局文件夹内 god-view.md / agents/*.md / replay.json / meta.json 均生成且结构正确**。

---

## 16. 编码规范（Coding Standards）
- 命名：类 `UpperCamelCase`，方法/字段 `lowerCamelCase`，常量 `UPPER_SNAKE`；包名全小写。
- 优先不可变：能用 `record` 就用；集合对外返回不可变视图。
- `core` / `agent` 包**禁止** Spring 注解、HTTP、文件 IO（IO 收敛到 `infra` / `api`）。
- 并发代码需注释线程模型；共享可变状态需说明同步策略。
- 异常：用语义明确的自定义异常（`IllegalPhaseTransitionException`、`LLMException` 等），不吞异常。
- 日志：关键阶段、Agent 动作、LLM 调用耗时打系统日志；**严禁输出 API Key 或完整密钥**。
- 注释：公共类/方法写简洁 Javadoc，复杂规则处引用本文件章节号（如 `// 见 SPEC 2.4`）。
- 提交：Conventional Commits（`feat:`/`fix:`/`test:`/`refactor:`/`docs:`/`chore:`）。

---

## 17. 仓库初始化（README / Repo Bootstrap）
请同时生成 `README.md`，至少包含：项目简介与架构层次（文字即可）；环境要求（JDK 17、Maven）；快速开始：
```bash
export DASHSCOPE_API_KEY=sk-xxxx
mvn clean package
java -jar target/multiagent-werewolf.jar --seed 12345 --out ./matches
```
以及配置说明（指向 `application.yml` 与环境变量）、对局文件夹结构与日志说明（指向第 10 节）、规则参数表（指向 2.5）、路线图（对应里程碑）、许可证与免责声明（API Key 不入库）。

---

## 附录 A：角色 System Prompt 模板（示例，置于 PromptBuilder）

> 中文模板，`{...}` 为运行时占位；结尾强约束「仅返回 JSON」与「发言≤100字」。

**通用尾部（所有角色追加）**
```
你正在参与一场 9 人标准狼人杀（预女猎）。当前是第 {round} 轮的 {phase} 阶段。
存活玩家座位：{aliveSeats}。
你只能依据你已知的信息推理，不得编造你无法看到的内容。
你的私人笔记（仅你可见）：{journalNotes}
请只输出一个 JSON 对象，不要包含任何额外文字或 Markdown 代码块：
{ "reasoning": "...", "action": { "type": "...", ... } }
```

**预言家（SEER）夜晚**
```
你的身份是【预言家】，目标是帮助好人找出狼人。你过去的查验结果：{seerHistory}。
请选择今晚要查验的一名存活玩家。action.type 为 "SEER_CHECK"，targetSeat 为座位号。
```

**狼人（WEREWOLF）夜晚商讨**
```
你的身份是【狼人】，你的狼队友座位：{wolfTeammates}。目标是隐藏身份并淘汰好人（屠边即胜）。
本轮队友已发表的意见：{wolfDiscussionThisRound}。
请发表你对今晚击杀目标的意见，或在最终轮直接给出击杀目标。
若发言（商讨），action.type 为 "SPEAK"，speech ≤100 字；若给最终目标，action.type 为 "WOLF_KILL"，targetSeat 为座位号。
```

**女巫（WITCH）夜晚**
```
你的身份是【女巫】。解药剩余：{antidoteLeft}，毒药剩余：{poisonLeft}。
今晚被狼人袭击的是：{wolfVictim}（若无则为空）。
规则：{witchSelfSaveRule}；本晚是否允许同时用两种药：{bothPotionRule}。
请决定是否用解药救该玩家、是否用毒药毒杀某人。
action.type 为 "WITCH"，useAntidote 为 true/false，poisonSeat 为座位号或 null。
```

**白天发言（任意角色）**
```
现在是白天发言阶段（严格按座位顺序，你能看到此前已发言者的内容）。
已公开信息：死亡/出局 {publicDeaths}，历史发言 {publicSpeeches}，历史投票 {publicVotes}。
请以你的角色立场发表发言：紧扣你的判断或意图展开，可表明身份、质疑他人或给出投票倾向。
要求：不超过 100 个汉字，禁止冗长铺陈。
action.type 为 "SPEAK"，speech 为发言文本（中文，≤100字）。
```

**投票（任意角色）**
```
现在进入投票放逐阶段。请投票放逐一名你认为是狼人的存活玩家，或弃票。
action.type 为 "VOTE"，targetSeat 为座位号；弃票时 targetSeat 为 null。
```

**遗言（被出局者）**
```
你已出局，现在发表遗言。请简要给出你的身份信息或对局势的判断，帮助你的阵营。
要求：不超过 100 个汉字。action.type 为 "SPEAK"，speech 为遗言文本。
（若你是猎人且规则允许开枪，请另用 HUNTER_SHOOT 动作并给出 targetSeat。）
```

---

## 附录 B：实现注意事项清单（Checklist for the AI agent）
- [ ] 术语区分清楚：对局(Match)独立文件夹/全新玩家/记忆不互通；轮(Round)内记忆跨轮保留。
- [ ] 仅 9 人「预女猎」，无守卫、无 12 人局。
- [ ] core / agent 包内无 Spring / IO / HTTP 依赖。
- [ ] API Key 仅来自环境变量；日志、复盘、上帝日志均不含密钥。
- [ ] 单 API Key + 每 Agent 每决策点一次独立调用；隔离在应用层（ContextBuilder）。
- [ ] 夜晚结算严格按 2.4；毒药不可被解药挡下。
- [ ] **白天发言严格串行**：后发言者上下文包含先发言者本轮发言。
- [ ] **发言≤100字**：prompt 约束 + ActionParser 按汉字码点截断并告警。
- [ ] ContextBuilder 通过信息隔离测试（平民看不到狼队友/查验结果）。
- [ ] ActionParser 容错并安全兜底。
- [ ] **每对局独立文件夹**；god-view.md 段落鲜明、开头含全部角色 system prompt、每事件块标注 PUBLIC/PRIVATE + 发布者 + 轮次阶段；PRIVATE 事件隔离他人但上帝可读。
- [ ] **每 Agent 维护 agents/seat-N.md** 记忆/思考文档（reasoning 不进 god-view 主线），作为行动与发言依据。
- [ ] replay.json / meta.json 结构完整。
- [ ] v2 预留：`Player.human`、`PlayerController`/`LLMPlayerController`、`HumanPlayerController` stub、`human-view.md` 占位、`werewolf.human.*` 配置。
- [ ] rounds-cap 生效，防无限对局；一切随机可由 seed 复现。
