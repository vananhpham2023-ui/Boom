# Boom

## 模块概览

项目包含 `modules/time-tracking` 纯 Java 模块，用于实现时间追踪子系统（T1~T3）中的核心会话构建、归并与修复算法。Android 端的 UsageStats/Accessibility 采集可复用该模块公开的 `UsageEvent` 数据模型。

### 快速开始

```bash
# 在离线环境中运行轻量化校验脚本
gradle :modules:time-tracking:runModuleTests --console=plain --no-daemon
```

更多细节参考 `docs/time_tracking_module.md` 与 `docs/todo.md`。
