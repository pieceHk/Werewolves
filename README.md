# Werewolves · 多 Agent 狼人杀引擎 🐺🤖

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![LLM: Qwen](https://img.shields.io/badge/LLM-Qwen%2FDashScope-green.svg)](https://dashscope.aliyun.com/)
[![Tests](https://img.shields.io/badge/tests-18%20passing-brightgreen.svg)](#测试与构建)

一个**后端专用**的 Java 多 Agent 狼人杀游戏引擎。每名玩家由一个独立的 LLM Agent 驱动，自动完成一场完整的 **9 人「预女猎」标准局**对局：夜晚行动、警长竞选、白天发言、投票放逐、胜负判定，全程使用中文交互与日志。

项目不包含任何前端实现，对局过程通过**上帝视角日志 + 每 Agent 私有记忆 + JSON 回放 + 全局复盘**呈现。核心设计强调三件事：**规则完备**（技能边界情形显式定义）、**结果可复现**（一切随机性由固定 seed 控制）、**LLM 可替换**（离线 Mock 与真实 Qwen 一键切换）。

> [!NOTE]
> 本仓库当前聚焦单一板子（9 人预女猎），不做 12 人局、不引入守卫。这是有意为之的范围控制——先把一种局做到规则严谨、信息隔离干净，再谈扩展。设计规格详见 [SPEC.md](./SPEC.md)，里程碑式开发记录详见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

## 📋 目录

- [功能特点](#-功能特点)
- [快速开始](#-快速开始)
- [命令行参数](#-命令行参数)
- [游戏机制](#-游戏机制)
- [运行配置](#-运行配置)
- [输出产物](#-输出产物)
- [项目结构](#-项目结构)
- [架构概览](#-架构概览)
- [测试与构建](#-测试与构建)
- [常见问题](#-常见问题)
- [开发记录](#-开发记录)
- [许可证](#-许可证)

## ✨ 功能特点

### 🎮 对局功能
- **9 人预女猎标准局**：3 狼人、3 平民、1 预言家、1 女巫、1 猎人，固定 seed 可复现角色分配。
- **完整阶段状态机**：准备 → 夜晚 → 警长竞选 → 白天公布 → 发言 → 投票 → 胜负检测，循环推进。
- **夜晚结算**：狼人可空刀、预言家可弃验、女巫解药/毒药；女巫硬规则写死（不能自救、不能同晚双药、毒药无视解药）。
- **警长竞选**：第 1 天支持上警、警上发言退水、警下投票、PK 重投、无人当选/警徽流失、警徽移交或撕毁。
- **白天投票**：多数票、警长 1.5 票权、首次平票重投、二次平票无人出局。
- **胜负判定**：好人全歼狼人胜利；支持屠边（`KILL_SIDE`）与屠城规则配置。

### 🤖 Agent 系统
- **严格信息隔离**：基于事件可见性（`PUBLIC` / `PRIVATE` + 座位 audience）构建每个 Agent 的上下文，狼队商讨仅狼人可见。
- **每局私有记忆**：`AgentJournal` 在对局内跨轮累积（第 2 轮记得第 1 轮），对局之间彻底清空。
- **中文 Prompt 工程**：所有角色固定前置公共规则与角色简化规则，强制严格 JSON 输出、发言不超过 100 个汉字。
- **健壮的动作解析**：支持 Markdown 围栏剥离、首个 JSON 提取、非法目标降级、发言按码点截断并返回告警。
- **夜晚动作净化**：进入规则引擎前先净化非法动作（女巫自救、无刀口救人、同晚双药、无效目标等），避免真实 LLM 输出导致整局中断。

### 🔌 LLM 接入
- **离线 Mock**：`MockLLMClient` 确定性输出，测试不联网，可本地离线跑通整局。
- **真实 Qwen**：`QwenLLMClient` 走 DashScope OpenAI 兼容 `/chat/completions`，支持连接/请求超时、重试、并发限流；**API Key 仅从环境变量读取，绝不写入仓库**。

### 📊 复盘与输出
- **上帝视角日志**：完整对局过程，含各角色 System Prompt 与夜晚私有动作记录。
- **每 Agent 私有日志**：每个座位独立的记忆与动作记录文件。
- **结构化回放**：`replay.json` 事件流、`meta.json` 元信息、每轮玩家表现分。
- **全局复盘**：对局结束后由 LLM 生成 `review.md` / `review.json`（含转折点、玩家点评、MVP、阵营总评）；Qwen 返回不可解析时走规则侧兜底复盘。
- **实时刷新**：每次事件写入后即刷新所有输出文件，对局运行中可 `tail -f` 实时观战。

## 🚀 快速开始

### 环境要求

- **Java 17**
- **Maven 3.8+**

### 1. 克隆项目

```bash
git clone https://github.com/pieceHk/multiagent-werewolf.git
cd multiagent-werewolf
```

### 2. 配置工具链（macOS / Homebrew 示例）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
```

### 3. 构建

```bash
mvn test          # 运行全部单元/集成测试（18 个）
mvn clean package # 生成可运行 shaded jar：target/multiagent-werewolf.jar
```

### 4. 运行一局（离线 Mock，推荐首次体验）

```bash
java -jar target/multiagent-werewolf.jar --seed 12345 --rounds-cap 8 --out ./matches
```

默认使用离线 Mock LLM，无需任何 API Key，适合开发和测试。运行后会在 `matches/` 下生成一场对局目录。

### 5. 使用真实 Qwen 对局

真实 Qwen 调用通过 DashScope OpenAI 兼容接口完成。**API Key 不写入仓库**，请在本机环境变量中设置：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"

java -jar target/multiagent-werewolf.jar --llm qwen --seed 12345 --rounds-cap 8 --out ./matches
```

### 6. 实时观战

对局运行时会持续刷新输出文件。另开一个终端可实时查看最新一局的上帝视角：

```bash
tail -f "$(ls -td matches/match-* | head -1)/god-view.md"
```

## 🧭 命令行参数

入口类 `com.piecehk.werewolf.api.cli.MatchRunner`，参数形如 `--key value`。

| 参数 | 说明 | 默认值 | 示例 |
|------|------|--------|------|
| `--seed` | 随机种子，决定角色分配与平票裁决，可复现对局 | `12345` | `--seed 999` |
| `--rounds-cap` | 单局最大轮数上限，防止极端情况下不收敛 | `20` | `--rounds-cap 8` |
| `--out` | 对局输出根目录 | `./matches` | `--out ./runs` |
| `--llm` | LLM 后端：`mock`（离线）或 `qwen`（真实） | `mock` | `--llm qwen` |
| `--qwen-base-url` | Qwen DashScope 兼容端点 | DashScope 兼容端点 | `--qwen-base-url https://...` |

## 🎲 游戏机制

### 角色技能

| 角色 | 英文标识 | 阵营 | 技能描述 |
|------|----------|------|----------|
| 狼人 | `WEREWOLF` | 狼人 | 每晚共同选择击杀一名玩家（可空刀）；狼人之间互相可见身份 |
| 预言家 | `SEER` | 好人 | 每晚查验一名玩家是好人还是狼人（可弃验） |
| 女巫 | `WITCH` | 好人 | 解药救当晚被刀者、毒药杀一人，各限一次（**不能自救、不能同晚双药**） |
| 猎人 | `HUNTER` | 好人 | 死亡时可开枪带走一名玩家（被毒不可开枪，被投票可开枪，由规则配置） |
| 平民 | `VILLAGER` | 好人 | 无夜间技能，仅靠白天发言与投票 |

### 对局流程

```
准备 PREPARING
   │
   ▼
夜晚 NIGHT ───────────── ① 狼人刀人（可空刀）
   │                     ② 预言家验人（可弃验）
   │                     ③ 女巫解药/毒药
   ▼
[仅第 1 天] 警长竞选 DAY_SHERIFF_ELECTION
   │            上警 → 警上发言退水 → 警下投票 → PK 重投 → 警徽归属
   ▼
白天公布 DAY_ANNOUNCE ── 公布夜间死讯与遗言
   │
   ▼
白天发言 DAY_DISCUSS ─── 存活玩家严格按座位串行发言
   │
   ▼
投票放逐 DAY_VOTE ────── 多数票 / 警长 1.5 票 / 平票重投 / 二次平票无人出局
   │
   ▼
胜负检测 ──── 未结束则回到夜晚，循环推进
   │
   ▼
游戏结束 GAME_OVER
```

### 关键规则边界

| 情形 | 裁决 |
|------|------|
| 女巫自救 | **禁止**（硬规则常量，不可配置） |
| 女巫同晚双药 | **禁止**（硬规则常量） |
| 毒药 vs 解药 | 毒药无视解药 |
| 平票 | 首次平票重投；二次平票无人出局 |
| 警长票权 | 1.5 票，按最高非整数分唯一性判断出局或平票 |
| 胜利条件 | `KILL_SIDE`（屠边即狼胜）/ 屠城，可配置 |

### 表现评分

每轮投票后生成玩家表现分表写入 `god-view.md`，结构化分数同步写入 `meta.json.scoreboardByRound` 与 `replay.json`；对局结束生成全局复盘，由 LLM 评选 MVP 并给出阵营总评。

## ⚙️ 运行配置

规则参数、输出目录与 Qwen 端点集中在 [`src/main/resources/application.yml`](src/main/resources/application.yml)：

```yaml
werewolf:
  preset: STANDARD_9          # 当前仅支持 9 人预女猎
  seed: 0
  rounds-cap: 20
  output-dir: ./matches
  rules:
    win-condition: KILL_SIDE          # 屠边胜利
    hunter-shoot-when-poisoned: false # 被毒不可开枪
    hunter-shoot-when-voted: true     # 被投票可开枪
    allow-first-night-last-words: true
    vote-tie: REVOTE_THEN_NONE        # 平票重投，再平则无人出局
    sheriff-enabled: true
    sheriff-vote-weight: 1.5          # 警长 1.5 票权
    max-speech-chars: 100             # 发言上限 100 汉字
  human:
    enabled: false   # v2 预留真人参与接口，v1 不实现
    seat: null

qwen:
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen3-max
  temperature: 0.8
  timeout-connect-ms: 10000
  timeout-read-ms: 60000
  max-retries: 3
  max-concurrency: 6
```

> [!IMPORTANT]
> Qwen 的 `api_key` **不在配置文件中**，仅从环境变量 `DASHSCOPE_API_KEY` 读取。`.gitignore` 已忽略 `target/`、`matches/`、`.env` 等可能含密钥的文件。

## 📂 输出产物

每场对局在 `matches/` 下创建独立目录（如 `match-20260601-204603-c0c41c31/`），互不干扰：

```text
match-<时间戳>-<id>/
├── god-view.md        # 上帝视角完整日志（含各角色 System Prompt 与夜晚私有动作）
├── human-view.md      # 人类视角预留文件（v2）
├── agents/
│   ├── seat-1.md      # 座位 1 的私有记忆与动作记录
│   └── ...            # seat-2 ~ seat-9
├── replay.json        # 结构化事件回放
├── meta.json          # seed、座位身份、胜利阵营、阶段、每轮表现分等元信息
├── review.md          # 对局结束后的全局复盘（人类可读）
└── review.json        # 全局复盘（机器可读：转折点 / 玩家点评 / MVP / 阵营总评）
```

## 📁 项目结构

```text
multiagent-werewolf/
├── pom.xml                         # Maven 单模块配置
├── README.md
├── SPEC.md                         # 工程设计规格（PRD + 架构 + 里程碑）
├── DEVELOPMENT.md                  # 里程碑式开发记录
├── LICENSE                         # MIT
└── src/
    ├── main/
    │   ├── java/com/piecehk/werewolf/
    │   │   ├── agent/              # Agent 上下文、prompt、动作解析、记忆、编排、LLM 客户端
    │   │   │   ├── action/         # 动作密封接口与各类动作（发言/投票/刀人/查验/用药…）
    │   │   │   └── llm/            # LLMClient、MockLLMClient、QwenLLMClient
    │   │   ├── api/cli/            # 命令行入口 MatchRunner
    │   │   ├── core/               # 框架无关的领域核心
    │   │   │   ├── model/          # 阵营、角色、阶段、玩家、规则、对局聚合、GameFactory
    │   │   │   ├── event/          # 事件、可见性、EventBus
    │   │   │   ├── rule/           # NightResolver、VoteResolver、WinChecker、WitchRules
    │   │   │   ├── score/          # 表现评分
    │   │   │   └── statemachine/   # GameStateMachine 与非法流转异常
    │   │   ├── game/               # 对局编排、玩家控制器、MatchManager、PhaseScheduler
    │   │   │   └── player/         # LLM / Human(stub) PlayerController
    │   │   └── infra/output/       # god-view / 私有日志 / replay / 快照 / 复盘 写出
    │   └── resources/
    │       └── application.yml     # 规则参数、输出目录、Qwen 端点
    └── test/java/                  # 单元测试与集成测试
```

## 🏗 架构概览

项目分三层，**core 与 agent 层保持框架无关（纯 Java）**：

- **规则引擎（`core`）**：领域模型 + 状态机 + 夜晚/投票/胜负裁决，负责一切狼人杀判定，确定性、可复现。
- **Agent 层（`agent`）**：中文 prompt 构建、动作解析、信息隔离上下文、每局私有记忆，负责把 LLM 的自由文本变成合法动作。
- **编排层（`game` + `infra`）**：`PhaseScheduler` / `GameService` 推进相位，`MatchManager` 为每场对局创建全新 `Game`、全新控制器与全新 `AgentJournal`；`infra/output` 负责所有文件落盘与实时快照。

> 一句话记忆模型：**记忆在「对局内跨轮保留」，在「对局之间清空」。**

## 🧪 测试与构建

```bash
mvn test          # 18 个测试：状态机流转、夜晚结算、平票裁决、胜负判定、
                  #            女巫硬规则、警长票权、动作解析、信息隔离、prompt 规则前置、整局集成
mvn clean package # 生成 target/multiagent-werewolf.jar（shaded，含全部依赖）
```

测试默认离线（Mock LLM 不联网）。集成测试会用 Mock 客户端跑通整局，验证不出现「非法目标」「解析失败」等异常降级记录。

## ❓ 常见问题

**Q：运行需要 API Key 吗？**
A：默认 `--llm mock` 完全离线，不需要任何 Key。只有 `--llm qwen` 才需要在环境变量 `DASHSCOPE_API_KEY` 中提供 DashScope 的 Key。

**Q：API Key 会被写进仓库吗？**
A：不会。Key 仅从环境变量读取，配置文件与代码中均无硬编码；`.gitignore` 也忽略了 `.env` 等文件。

**Q：为什么真实 Qwen 偶尔输出不合法的动作，对局却没有崩？**
A：进入规则引擎前会先净化夜晚动作（女巫二夜自救、无刀口救人、同晚双药、无效目标等会被降级并写入私有系统备注），保证整局不中断。

**Q：相同 seed 能复现同一局吗？**
A：能。角色分配与平票裁决等随机性均由 seed 控制。但若使用真实 Qwen，LLM 自身输出存在随机性（受 `temperature` 影响），发言与决策不保证逐字一致。

**Q：支持 12 人局 / 守卫 / 多模型竞技吗？**
A：当前不支持，属于有意的范围控制。接口可插拔，扩展方向见 [SPEC.md](./SPEC.md)。

**Q：怎么复盘一局？**
A：看对局目录下的 `god-view.md`（全过程）与 `review.md`（LLM 全局复盘）；机器可读数据在 `replay.json` / `meta.json` / `review.json`。

**Q：`mvn` 找不到或 Java 版本不对？**
A：确认已安装 Java 17 与 Maven，并正确导出 `JAVA_HOME` 与 `PATH`（见[快速开始](#-快速开始)）。

## 📝 开发记录

里程碑式开发汇报记录在 [DEVELOPMENT.md](./DEVELOPMENT.md)，已完成 M0–M8：

| 里程碑 | 内容 |
|--------|------|
| M0 | 项目初始化：Maven 单模块、SPEC、`.gitignore`、`application.yml`、工具链 |
| M1 | 领域模型与状态机：核心 model、`GameFactory.standardNine`、事件总线、相位流转 |
| M2 | 规则引擎：`NightResolver`、`VoteResolver`、`WinChecker` 及单测 |
| M3 | LLM 客户端：`LLMClient` 抽象、`QwenLLMClient`、`MockLLMClient` |
| M4 | Agent 层：动作密封接口、信息隔离上下文、`PromptBuilder`、`ActionParser`、`AgentJournal`、编排器 |
| M5 | 引擎编排、CLI 与日志：`PhaseScheduler`/`GameService`、`MatchManager`、各类输出写出 |
| M6 | 文档与运行验证：补全 README，修正 Mock 动作识别，全测试通过、整局跑通 |
| M7 | 真实 Qwen 容错与实时日志：夜晚动作净化、`MatchSnapshotWriter` 实时快照 |
| M8 | 规则补丁：女巫硬规则写死、警长竞选与 1.5 票权、每轮表现分、全局复盘 |

原始设计规格同步在 [SPEC.md](./SPEC.md)。

## 📄 许可证

本项目采用 [MIT](LICENSE) 许可证开源。
