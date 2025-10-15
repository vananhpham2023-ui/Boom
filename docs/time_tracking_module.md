# 时间追踪子系统（T1 ~ T3）实现说明

本文件描述 MVP 阶段时间追踪模块的核心数据结构、算法流程、测试覆盖与验收自检方式，对应 TODO 清单中的 T1~T3 任务。

## 代码结构

```
modules/time-tracking/
├── build.gradle.kts              // 纯 Java Library 模块配置（无外部依赖）
├── src/main/java/com/boom/timetracking/
│   ├── Session.java              // Session 定义与置信度、修复标记
│   ├── SessionBuilder.java       // UsageEvent 流→Session 的实时构建逻辑
│   ├── SessionMerger.java        // Session 跨段归并逻辑
│   ├── SessionRepair.java        // 重叠、跨日修复与异常裁剪工具
│   ├── SessionRepository.java    // MVP 阶段的内存存储与接口抽象
│   └── UsageEvent.java           // UsageEvent 数据模型及来源枚举
└── src/test/java/com/boom/timetracking/
    └── TestHarness.java          // 离线环境下的轻量断言脚本，覆盖 T1~T3 KPI
```

> **说明**：采集层（UsageStats / Accessibility）在 Android 端实现，统一按照 `UsageEvent` 数据模型向 `SessionBuilder` 推送事件；该模块聚焦于跨平台可复用的核心算法，便于在容器/CI 中编译与回归。

## 主要流程

1. **事件采集统一输出**：端侧 collector 将 UsageStats、Accessibility、心跳补偿等信号转换为标准 `UsageEvent`。
2. **SessionBuilder**：
   - 为每个包维护 `MutableState`（首/末时间、事件数、来源集合、附加属性）。
   - 接收前台事件时更新状态，若间隔超过 `inactivityThresholdMillis` 则自动关闭旧 Session 并新建。
   - 接收后台事件时生成 Session，处理“无前台的后台事件”“跨长间隔的后台事件”并按照 KPI 计入自动修复统计。
   - `flush(now)` 用于崩溃恢复或日切换时落盘未完成 Session，保证 T3 “崩溃恢复后数据丢失为 0”。
3. **SessionMerger**：用于离线批处理，将同一应用在阈值内的连续 Session 合并，合并后会保留来源并取最高置信度。
4. **SessionRepair**：保证排序后 Session 不重叠，自动裁剪跨日 Session 至当日 24:00，所有修复过的记录均标记为 `AUTO_REPAIRED` 以便追踪。
5. **SessionRepository**：MVP 阶段提供线程安全的内存实现，真实工程可替换为 Room/SQLCipher DAO，接口保持一致。

## 测试与验收映射

| 任务 | 覆盖脚本 | KPI 关联点 |
| ---- | -------- | ---------- |
| T1   | `TestHarness#shouldBuildSessionsWithHighAccuracy` | 计算识别准确率、漏报率、重复率并断言阈值；校验统计计数。 |
| T2   | `TestHarness#shouldFlushInactiveSessions` | 模拟后台保活后 flush 行为，验证自动修复标记。 |
| T3   | `TestHarness#shouldMergeSessionsWithinThreshold`、`shouldRepairOverlapsAndCrossDaySessions` | 覆盖跨段归并、重叠裁剪、跨日截断，确保修复状态正确。 |

运行方法：

```bash
gradle :modules:time-tracking:runModuleTests --console=plain --no-daemon
```

该任务基于 `JavaExec` 执行轻量断言，不依赖第三方测试框架，可在离线环境中运行。测试通过后，可结合真实采集日志生成 CSV 与日志样本，补充 TODO 中要求的产出物。

## 后续集成提示

- Android Collector 可通过 `SessionBuilder.process(event)` 实时写入，再将生成的 Session 投递至持久化层。
- 若需要记录额外上下文（如任务栈、屏幕状态），可在 `UsageEvent.metadata` 中附加键值对，`SessionBuilder` 会自动聚合。
- 集成 Room 时建议以 `SessionRepository` 作为接口入口，保持线程安全，并复用现有测试数据集做迁移验证。

## KPI 自检建议

- 使用真实设备采集样本，与系统「数字健康」导出的 CSV 对齐，计算准确率、漏报率、重复率，与 `TestHarness` 中指标保持一致。
- 保活策略可通过定时触发 `flush(now)` 日志验证是否存在未关闭 Session，并结合业务埋点统计白名单转化率。
- 异常样例库运行 `SessionRepair.repair` 与 `SessionMerger.merge`，输出前后差异，确保修复正确率达到 KPI 要求。
