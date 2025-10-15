# 短视频使用统计应用技术方案

## 1. 目标与范围
- 将产品策划书拆解为技术团队可执行的开发任务，明确各阶段交付物。
- 聚焦 Android 端 MVP（时间追踪、文本驱动的视频类型识别、数据可视化、目标提醒）并为可选云端、家庭监护、成就等拓展能力预留接口。
- 视频类型识别方案优先基于**标题/标签/评论等文本信号结合大语言模型**做分类；封面图像仅在用户授权并评估成本后作为补充信号。

## 2. 迭代规划与关键任务

### 2.1 阶段拆解
| 阶段 | 时间 | 目标 | 核心交付 |
| --- | --- | --- | --- |
| P0 技术预研 | 2 周 | 验证 UsageStats 精度、文本抓取可行性、LLM 分类准确率 | POC 应用、分类提示词模板、数据隐私评估报告 |
| MVP 开发 | 6-8 周 | 实现日/周/月统计、目标提醒、基础分类可视化 | 安卓 App v1.0、SQLite 数据模型、LLM 推理服务对接、仪表盘 UI |
| P1 优化 | 6 周 | 引入封面图像补充分类、专注模式、成就体系雏形 | 图像分析微服务、通知管控模块、勋章数据结构 |
| P2 拓展 | 待定 | 家庭监护、云同步、研究者聚合报表 | 后端同步 API、家庭账户模型、脱敏聚合管线 |

### 2.2 功能模块 & 任务拆解
1. **时间追踪子系统**
   - T1: UsageStats + Accessibility 结合的数据采集服务，记录 App 进入/退出事件。
   - T2: 前台保活策略（前台服务、忽略电池优化引导）。
   - T3: session 归并与异常修复（重叠、跨日、App 崩溃场景）。

2. **内容信号采集**
   - C1: 无障碍服务读取短视频 App 当前页面标题、标签、评论摘要；必要时结合通知监听器获取文案。
   - C2: 本地脱敏处理（移除用户昵称、评论 ID 等 PII）。
   - C3: 内容快照入库，与使用 session 建立关联。

3. **文本驱动分类引擎**
   - L1: 设计多平台统一的 prompt 模板（含上下文、分类标签定义、输出格式约束）。
   - L2: 选择 LLM 推理形态：
     - 首选：端侧轻量大模型（如 GGUF 量化模型）+ 本地推理 SDK。
     - 备选：云端 LLM API（需 Token 化匿名、带宽加密、用户授权日志）。
   - L3: 分类任务调度（批量/实时），失败重试与缓存策略。
   - L4: 分类结果置信度评估与人工校正接口（供后续模型迭代）。

4. **数据处理与存储**
   - D1: 设计归档作业（每日/每周聚合），生成 Dashboard 所需聚合表。
   - D2: SQLite schema 与数据访问层封装（Room ORM 或自定义 DAO）。
   - D3: 数据导出接口（CSV/PDF 钩子，为 P1 做准备）。

5. **目标 & 提醒系统**
   - G1: 目标配置 UI & 逻辑（全局、分类级）。
   - G2: 进度评估引擎（实时监听日内累计时长）。
   - G3: 通知提醒调度（遵循安卓通知优先级与免打扰规则）。

6. **可视化与报告**
   - V1: 仪表盘（今日总时长、目标进度环图、类型饼图）。
   - V2: 趋势页（可切换日/周/月折线图、App 使用明细列表）。
   - V3: 周报/月报生成器（图文摘要 + 建议语句模板）。

7. **隐私与权限合规**
   - P1: 权限引导与用户许可记录（UsageStats、Accessibility、通知、可选多媒体）。
   - P2: 本地数据加密（SQLCipher 或 Android Keystore 加密关键字段）。
   - P3: 隐私政策文档、授权弹窗、LLM 调用日志（审计）。

8. **工程与运维支撑**
   - E1: 模块化项目结构（核心服务、UI、数据、推理）。
   - E2: 自动化测试框架（单元、UI、端到端数据一致性校验）。
   - E3: Crash & 性能监控（Firebase Crashlytics / Sentry / 自建）。

## 3. 系统架构设计

### 3.1 本地架构概览
```
+-------------------------------------------------------------+
|                         Android App                         |
|                                                             |
|  UI 层（Compose/VueNative）                                 |
|    - 仪表盘 / 趋势 / 目标设置 / 成就                         |
|                                                             |
|  业务层                                                     |
|    - 使用统计管理器                                         |
|    - 内容快照管理器                                         |
|    - 分类任务调度器                                         |
|    - 目标与提醒引擎                                         |
|    - 报表生成器                                             |
|                                                             |
|  数据层                                                     |
|    - Repository（Room DAO / SQLite）                         |
|    - 加密存储（密钥来自 Keystore）                         |
|                                                             |
|  系统集成                                                   |
|    - UsageStatsManager                                      |
|    - AccessibilityService                                   |
|    - NotificationListener                                   |
|    - ForegroundService                                      |
|                                                             |
|  推理层（策略模式）                                         |
|    - LocalLLMProvider（端侧模型）                           |
|    - RemoteLLMProvider（加密 API 调用，可选）               |
+-------------------------------------------------------------+
```

### 3.2 可选云端架构
```
Android App  <--TLS-->  同步 API (Node.js)
                               |
                               +--> MongoDB（用户配置、聚合报表）
                               +--> 对象存储（脱敏周报快照）
                               +--> 权限与令牌服务（JWT + Refresh Token）
```
- 云端仅存储脱敏后的聚合结果（按日的类型分钟数、目标设置、成就状态），确保原始文本/封面图不出本地。
- 同步流程：本地定期打包 -> 加密 -> 上传 -> 服务端校验 -> 返回最新配置信息。

## 4. 数据模型设计（本地 SQLite）
| 表 | 关键字段 | 描述 |
| --- | --- | --- |
| `app_sessions` | `id`, `package_name`, `start_ts`, `end_ts`, `duration_sec`, `source` | 单次应用使用 session，来源 UsageStats/Accessibility。 |
| `content_snapshots` | `id`, `session_id`, `title`, `tags`, `comments_excerpt`, `captured_ts`, `hash` | 文本信号快照，与 session 关联；哈希用于去重。 |
| `classification_jobs` | `id`, `snapshot_id`, `status`, `llm_provider`, `prompt_version`, `scheduled_ts`, `completed_ts` | LLM 分类任务队列。 |
| `classification_results` | `id`, `job_id`, `label`, `confidence`, `raw_response`, `evidence` | 分类输出，`raw_response` 加密存储。 |
| `usage_summary_daily` | `date`, `package_name`, `total_duration_sec`, `category_breakdown_json` | 每日聚合结果。 |
| `category_watchtime_daily` | `date`, `category`, `duration_sec`, `source_confidence` | 类型级别统计，用于图表。 |
| `goals` | `id`, `scope`(`global`/`category`), `target_minutes`, `days_of_week_mask`, `created_ts`, `active` | 目标配置。 |
| `goal_progress` | `goal_id`, `date`, `consumed_minutes`, `status` | 当日目标达成状态。 |
| `achievements` | `id`, `code`, `title`, `description`, `unlock_condition_json`, `unlocked_ts` | 成就定义与解锁记录。 |
| `settings` | `key`, `value`, `updated_ts` | 主题、隐私偏好、LLM 选择等配置。 |

- 所有敏感文本字段（`title/tags/comments_excerpt/raw_response`）使用 AES-256 加密存储，密钥保存在 Android Keystore（硬件级）。

## 5. 接口设计

### 5.1 App 内模块接口
- `UsageEventCollector`
  - `fun start()` / `fun stop()`
  - `Flow<AppSession>` 实时输出已结束的 session。

- `ContentSnapshotCollector`
  - `suspend fun capture(session: AppSession): ContentSnapshot`
  - `fun registerListener(AppPackageConfig)`：根据不同平台（抖音、快手、B站）配置解析策略。

- `ClassificationScheduler`
  - `fun enqueue(snapshot: ContentSnapshot)`
  - `Flow<ClassificationResult>`：输出完成的分类结果。
  - `fun setProvider(LLMProviderType)`：切换端侧/云端。

- `GoalManager`
  - `fun upsertGoal(goal: Goal)`
  - `fun evaluateProgress(now: Instant)` -> `GoalProgressUpdate`
  - `fun notifyIfNeeded()`

- `ReportService`
  - `suspend fun getDashboardSummary(dateRange: DateRange): DashboardSummary`
  - `suspend fun generateWeeklyDigest(week: Week): Report`

### 5.2 可选云端 API（REST）
- `POST /v1/sync/upload`
  - Body: `{ device_id, payload: encrypted_blob, checksum }`
  - Auth: JWT（设备绑定）。
  - Response: `{ status, latest_configs? }`

- `GET /v1/reports/weekly?week=YYYY-WW`
  - 返回脱敏聚合报告（若用户开启云同步）。

- `POST /v1/family/link`
  - 建立家庭账号绑定，触发双向确认流程。

- 所有 API 需要速率限制与审计日志，确保合规。

## 6. 大模型分类策略

1. **文本信号汇总**：`title + tags + top_comments_excerpt + app_name + publisher_hint` 组成上下文；若缺失则使用 UsageStats 和历史偏好填补。
2. **Prompt 结构**：
   ```
   System: 你是一个内容分类助手，仅根据提供的中文文本判断短视频类型。
   User: {平台说明 + 分类定义 + 文本片段}
   Assistant: 返回 JSON：{"label": "娱乐", "confidence": 0.78, "reasons": ["含搞笑关键词"]}
   ```
3. **类别体系**（可配置）：娱乐、知识科普、生活、美食、游戏、体育、音乐、其他。
4. **模型选型**：
   - 端侧：集成 `Mistral-7B`/`Qwen2-7B` 量化版，通过 `llama.cpp`/`GGML` 接口部署，确保 1-2s 内完成分类。
   - 云端备选：调用企业版大模型（需签署数据处理协议），传输前对文本进行脱敏（hash 用户名、去除 URL）。
5. **补充封面图计划**：
   - POC 阶段验证 `MobileNet`/`EfficientNet` 轻量模型对封面主色调、场景识别的贡献度。
   - 引入条件：文本缺失率 > 15% 且用户授权采集封面截图。

## 7. Definition of Done（DoD）
- **通用标准**
  - 需求在 Jira/Tapd 等工具中建立任务并有验收标准。
  - 代码通过静态检查（ktlint/detekt）和单元测试，覆盖率 ≥ 70%。
  - 关键用户流程（仪表盘浏览、目标设置、分类展示）通过 UI 自动化回归。
  - 主要模块附带监控指标或日志，便于排错。
  - 隐私合规检查通过（权限弹窗、本地加密、数据最小化）。

- **模块特定**
  - 时间追踪：随机抽样 50 条 session，与系统“数字健康”记录比对误差 < 5%。
  - 文本采集：支持抖音、快手、B站三大平台，成功率 > 90%，无敏感数据泄露。
  - 分类引擎：在内部标注集上 F1 ≥ 0.75；异常响应（非 JSON/低置信度）可回退至“其他”分类并记录。
  - 仪表盘：渲染时间 < 300ms（首屏已缓存数据），图表与底层数据一致性通过快照测试验证。
  - 目标提醒：接近阈值（剩余 10 分钟）时通知必达率 > 95%，可手动测试覆核。
  - 隐私与权限：所有权限请求具备前置教育页，用户可在设置中撤回且系统行为恢复默认。

## 8. 风险与缓解
- **文本信号缺失**：与内容平台版本变化联动；建立解析策略配置中心，发布热更新。
- **LLM 延迟/成本**：优先端侧推理；云端调用设置配额与批量处理，缓存重复文本结果。
- **权限政策变化**：保持与主流厂商兼容测试；预置 WebView 引导至官方教程。
- **隐私投诉**：准备数据导出/删除通道，提供透明日志，便于审计。

## 9. 里程碑输出
- P0：完成预研报告 + Demo（含分类准确率、性能数据）。
- MVP Beta：内部测试版（TestFlight/内测包），包含仪表盘、目标提醒、分类统计。
- MVP GA：公开上架，完成隐私合规、性能优化、崩溃率 < 1%。
- P1：新增专注模式、封面图补充、勋章体系，更新 DoD。

