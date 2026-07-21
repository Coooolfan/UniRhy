# scripts

本地开发辅助脚本。

## dev.sh — 开发进程编排

用一个 tmux 会话（默认 `unirhy-dev`）托管 `server` / `web` / `website`，让 `./gradlew bootRun` 与 `yarn dev` 在后台运行而不阻塞终端。各服务输出实时落到 `scripts/.dev/<svc>.log`（已被 git 忽略）。

### 给 Agent 的使用约定

- 需要启动本地开发环境时，**一律使用此脚本**，不要直接在前台执行 `./gradlew bootRun` / `yarn dev`（会阻塞终端直到超时）。
- 所有子命令均**非阻塞**、立即返回。脚本刻意不提供 `attach` / `logs -f` 这类占住终端的入口，也不要自行执行 `tmux attach`、`tail -f` 等阻塞命令。
- `start` 只是异步拉起进程，返回时服务尚未就绪。启动后应执行 `scripts/dev.sh status` 轮询确认「端口监听」为 `LISTEN` 再认为启动成功；后端首次编译启动较慢，需多次间隔检查。
- 排查服务问题优先用 `scripts/dev.sh logs <svc>` 获取最近日志快照；日志为纯文本（无 ANSI 转义），可直接 read / grep。
- `status` 显示「已退出」表示进程崩溃，先 `logs <svc>` 查看崩溃原因；如需重试，再次 `start <svc>` 会在原窗口原位重启。
- 修改代码后需要服务生效时用 `restart <svc>`；任务结束后如不再需要服务，用 `stop` 清理。

### 命令

```bash
scripts/dev.sh start              # 后台启动 server + web + website
scripts/dev.sh start server web   # 只启动前后端
scripts/dev.sh status             # 会话 / 端口监听 / 各窗口状态
scripts/dev.sh logs server        # 打印后端最近日志（快照，立即返回）
scripts/dev.sh restart web        # 重启前端
scripts/dev.sh stop               # 停止全部
```

### 服务定义

| 服务    | 端口 | 启动命令            | 工作目录   |
| ------- | ---- | ------------------- | ---------- |
| server  | 8654 | `./gradlew bootRun` | `server/`  |
| web     | 8655 | `yarn dev`          | `web/`     |
| website | 5173 | `yarn dev`          | `website/` |

### 实现说明

- 依赖 `tmux`（`brew install tmux`）。
- 服务命令直接作为 tmux 窗口进程运行（非交互 shell），进程退出即窗口死亡；窗口以 `remain-on-exit` 保留，`status` 据此区分「运行中 / 已退出 / 未启动」。
- 服务输出经 `tee` 落盘（非 TTY），日志为纯文本，不含屏幕重绘/彩色转义序列。
- 启动命令强制 `NO_COLOR=1`（server 额外 `SPRING_OUTPUT_ANSI_ENABLED=never`）。
- 自定义会话名：`UNIRHY_DEV_SESSION=foo scripts/dev.sh start`。
