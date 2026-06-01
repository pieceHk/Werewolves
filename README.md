# Werewolves

Werewolves 是一个后端专用的 Java 多 Agent 狼人杀项目。当前版本实现 9 人标准局（预言家、女巫、猎人、3 狼、3 民）的核心对局流程，所有游戏交互与日志均使用中文，不包含任何前端实现。

项目目标是沉淀一个可扩展、可测试、可替换 LLM 的服务端引擎：规则引擎负责狼人杀判定，Agent 层负责中文 prompt、动作解析与记忆隔离，编排层负责推进夜晚、公布、发言、投票、胜负检测和日志落盘。

## 当前能力

- Maven 单模块 Java 17 项目。
- 9 人预女猎角色分配，支持固定 seed 复现。
- 阶段状态机：准备、夜晚、白天公布、白天发言、投票、游戏结束。
- 夜晚结算：狼人刀人、预言家查验、女巫解药/毒药、首夜自救与双药限制。
- 白天投票：多数票、平票重投、二次平票无人出局。
- 胜负判定：好人胜、狼人胜，支持屠边/屠城规则配置。
- Agent 信息隔离：公开事件、私有事件、座位 audience、每局私有记忆。
- LLM 接入：离线 `MockLLMClient` 与 DashScope/Qwen OpenAI 兼容接口。
- 对局输出：上帝视角日志、每个 Agent 私有日志、回放 JSON、元数据 JSON。

## 目录结构

```text
src/main/java/com/piecehk/werewolf
  agent/        Agent 上下文、prompt、动作解析、LLM 客户端
  api/cli/      命令行入口
  core/         领域模型、事件、状态机、规则引擎
  game/         对局编排、玩家控制器、MatchManager
  infra/output/ 对局日志和回放文件写出
src/test/java/  单元测试与集成测试
```

## 本地运行

需要 Java 17 和 Maven。

```bash
cd /Users/ahiuy/myproject/Werewolves

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"

mvn test
mvn clean package
java -jar target/multiagent-werewolf.jar --seed 12345 --rounds-cap 8 --out ./matches
```

默认使用离线 mock LLM，适合开发和测试。运行后会在 `matches/` 下生成一场对局目录：

```text
god-view.md       上帝视角完整日志
human-view.md     人类视角预留文件
agents/seat-N.md  每个座位的私有记忆与动作记录
replay.json       结构化事件回放
meta.json         seed、座位身份、胜利阵营等元信息
```

对局运行时会持续刷新这些文件。另开一个终端可以实时查看最新一局上帝视角：

```bash
cd /Users/ahiuy/myproject/Werewolves
tail -f "$(ls -td matches/match-* | head -1)/god-view.md"
```

## 使用 Qwen

真实 Qwen 调用通过 DashScope OpenAI 兼容接口完成。API Key 不写入仓库，请在本机环境变量中设置：

```bash
cd /Users/ahiuy/myproject/Werewolves

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"

java -jar target/multiagent-werewolf.jar --llm qwen --seed 12345 --rounds-cap 8 --out ./matches
```

## 开发记录

阶段性开发汇报记录在 [DEVELOPMENT.md](./DEVELOPMENT.md)，原始开发文档同步在 [SPEC.md](./SPEC.md)。
