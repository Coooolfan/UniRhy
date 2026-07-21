#!/usr/bin/env bash
# UniRhy 本地开发进程编排：用一个 tmux 会话托管 server / web / website，
# 避免 bootRun 与 vite dev 阻塞终端。日志同时落到 scripts/.dev/<svc>.log。
set -euo pipefail

SESSION="${UNIRHY_DEV_SESSION:-unirhy-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$SCRIPT_DIR/.dev"

ALL_SERVICES=(server web website)

svc_dir() {
    case "$1" in
        server) echo "$ROOT_DIR/server" ;;
        web) echo "$ROOT_DIR/web" ;;
        website) echo "$ROOT_DIR/website" ;;
        *) return 1 ;;
    esac
}

svc_cmd() {
    case "$1" in
        # 关闭彩色输出，避免日志文件混入 ANSI 转义序列
        server) echo "SPRING_OUTPUT_ANSI_ENABLED=never NO_COLOR=1 ./gradlew bootRun" ;;
        web) echo "NO_COLOR=1 yarn dev" ;;
        website) echo "NO_COLOR=1 yarn dev" ;;
        *) return 1 ;;
    esac
}

svc_port() {
    case "$1" in
        server) echo 8654 ;;
        web) echo 8655 ;;
        website) echo 5173 ;;
        *) echo "" ;;
    esac
}

# 服务命令包一层 tee：面板显示与日志落盘同源
wrapped_cmd() {
    echo "$(svc_cmd "$1") 2>&1 | tee '$LOG_DIR/$1.log'"
}

die() {
    echo "错误：$*" >&2
    exit 1
}

require_tmux() {
    command -v tmux >/dev/null 2>&1 || die "未找到 tmux，请先安装（brew install tmux）"
}

is_valid_service() {
    local svc="$1"
    for s in "${ALL_SERVICES[@]}"; do
        [ "$svc" = "$s" ] && return 0
    done
    return 1
}

# 解析服务参数：为空则返回全部，否则逐个校验
resolve_services() {
    if [ "$#" -eq 0 ]; then
        printf '%s\n' "${ALL_SERVICES[@]}"
        return
    fi
    for svc in "$@"; do
        is_valid_service "$svc" || die "未知服务 '$svc'（可选：${ALL_SERVICES[*]}）"
        echo "$svc"
    done
}

session_exists() {
    tmux has-session -t "$SESSION" 2>/dev/null
}

window_exists() {
    tmux list-windows -t "$SESSION" -F '#{window_name}' 2>/dev/null | grep -qx "$1"
}

port_listening() {
    local port="$1"
    [ -n "$port" ] || return 1
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

ensure_session() {
    if session_exists; then
        return
    fi
    # 先建一个占位 window，待服务 window 建好后再移除
    tmux new-session -d -s "$SESSION" -n __bootstrap__ -c "$ROOT_DIR"
}

cleanup_bootstrap() {
    if window_exists __bootstrap__; then
        tmux kill-window -t "$SESSION:__bootstrap__" 2>/dev/null || true
    fi
}

start_service() {
    local svc="$1" dir cmd log port
    dir="$(svc_dir "$svc")"
    cmd="$(svc_cmd "$svc")"
    log="$LOG_DIR/$svc.log"

    if window_exists "$svc"; then
        if tmux list-panes -t "$SESSION:$svc" -F '#{pane_dead}' 2>/dev/null | grep -qx 1; then
            # 进程已退出（窗口因 remain-on-exit 残留），原位复活
            : >"$log"
            tmux respawn-window -k -t "$SESSION:$svc" -c "$dir" "$(wrapped_cmd "$svc")"
            echo "• $svc 进程已退出，已在原窗口重启（端口 $(svc_port "$svc")，日志 scripts/.dev/$svc.log）"
        else
            echo "• $svc 已在运行，跳过"
        fi
        return
    fi

    port="$(svc_port "$svc")"
    if port_listening "$port"; then
        echo "• 警告：端口 $port 已被其他进程占用，$svc 可能启动失败" >&2
    fi

    mkdir -p "$LOG_DIR"
    : >"$log"

    # 服务命令直接作为窗口进程运行：进程退出即 pane 死亡，status 能准确反映状态。
    # 输出经 tee 落盘：stdout 非 TTY 时 vite/spring 自动退化为纯文本行式输出，
    # 日志不会混入屏幕重绘/光标移动转义序列
    tmux new-window -t "$SESSION" -n "$svc" -c "$dir" "$(wrapped_cmd "$svc")"
    # 进程退出后保留 pane，便于 status 显示「已退出」并支持原位复活
    tmux set-window-option -t "$SESSION:$svc" remain-on-exit on
    echo "• ${svc} 启动中（端口 ${port}，日志 scripts/.dev/$svc.log）"
}

cmd_start() {
    require_tmux
    local services
    services=$(resolve_services "$@")
    ensure_session
    while IFS= read -r svc; do
        start_service "$svc"
    done <<<"$services"
    cleanup_bootstrap
    echo
    echo "已就绪。状态：scripts/dev.sh status ｜ 日志：scripts/dev.sh logs <svc>"
}

cmd_stop() {
    require_tmux
    if ! session_exists; then
        echo "会话 $SESSION 未运行"
        return
    fi
    if [ "$#" -eq 0 ]; then
        tmux kill-session -t "$SESSION"
        echo "已停止全部服务（会话 $SESSION 已销毁）"
        return
    fi
    local services
    services=$(resolve_services "$@")
    while IFS= read -r svc; do
        if window_exists "$svc"; then
            tmux kill-window -t "$SESSION:$svc"
            echo "• $svc 已停止"
        else
            echo "• $svc 未在运行"
        fi
    done <<<"$services"
    # 若只剩占位或空会话则一并销毁
    if ! tmux list-windows -t "$SESSION" -F '#{window_name}' 2>/dev/null | grep -qvx __bootstrap__; then
        tmux kill-session -t "$SESSION" 2>/dev/null || true
    fi
}

cmd_restart() {
    cmd_stop "$@"
    sleep 1
    cmd_start "$@"
}

cmd_status() {
    require_tmux
    if ! session_exists; then
        echo "会话 ${SESSION}：未运行"
        return
    fi
    echo "会话 ${SESSION}：运行中"
    printf '%-10s %-8s %-10s %s\n' "服务" "端口" "端口监听" "窗口状态"
    for svc in "${ALL_SERVICES[@]}"; do
        local port listen win
        port="$(svc_port "$svc")"
        port_listening "$port" && listen="LISTEN" || listen="-"
        if window_exists "$svc"; then
            if tmux list-panes -t "$SESSION:$svc" -F '#{pane_dead}' 2>/dev/null | grep -qx 1; then
                win="已退出"
            else
                win="运行中"
            fi
        else
            win="未启动"
        fi
        printf '%-10s %-8s %-10s %s\n' "$svc" "$port" "$listen" "$win"
    done
}

cmd_logs() {
    local svc="${1:-}"
    [ -n "$svc" ] || die "用法：scripts/dev.sh logs <server|web|website>"
    is_valid_service "$svc" || die "未知服务 '$svc'（可选：${ALL_SERVICES[*]}）"
    local log="$LOG_DIR/$svc.log"
    [ -f "$log" ] || die "暂无 ${svc} 日志（先执行 scripts/dev.sh start $svc）"
    tail -n 200 "$log"
}

usage() {
    cat <<'EOF'
UniRhy 本地开发进程编排（基于 tmux）

用法：scripts/dev.sh <命令> [服务...]

命令：
  start   [svc...]   后台启动服务（默认 server web website），终端不阻塞
  stop    [svc...]   停止指定服务；不带参数销毁整个会话
  restart [svc...]   重启指定服务
  status             查看会话、端口监听与各窗口状态
  logs    <svc>      查看某服务最近日志（快照，立即返回）

说明：不提供 attach / logs -f 等阻塞入口；如需实时查看请自行执行 tmux attach -t $SESSION

服务：server（:8654）web（:8655）website（:5173）

示例：
  scripts/dev.sh start              # 三个全起
  scripts/dev.sh start server web   # 只起前后端
  scripts/dev.sh logs server        # 查看后端最近日志
  scripts/dev.sh restart web        # 重启前端
  scripts/dev.sh stop               # 全部停止

环境变量：
  UNIRHY_DEV_SESSION  自定义 tmux 会话名（默认 unirhy-dev）
EOF
}

main() {
    local cmd="${1:-}"
    [ "$#" -gt 0 ] && shift || true
    case "$cmd" in
        start) cmd_start "$@" ;;
        stop) cmd_stop "$@" ;;
        restart) cmd_restart "$@" ;;
        status) cmd_status "$@" ;;
        logs) cmd_logs "$@" ;;
        "" | -h | --help | help) usage ;;
        *) die "未知命令 '$cmd'（执行 scripts/dev.sh --help 查看用法）" ;;
    esac
}

main "$@"
