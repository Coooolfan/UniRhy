---
name: release-note-creator
description: 撰写 UniRhy 的 Release Notes。适用于：(1) 发版前为指定版本号生成更新日志；(2) 确定版本的变更范围（前序 tag 比较基准）；(3) 校对已有 release notes 是否符合书写规范。
---

# Release Note Creator

## 流程

1. 阅读 `skills/release-note-creator/references/range-determination.md`，了解如何确定变更范围
2. 与上一步确定的前序 tag 进行比较，确定变更范围
3. 阅读 `skills/release-note-creator/references/release-note-guideline.md`，了解如何撰写更新日志
4. 如版本号、前序 tag 或更新日志范围存在歧义，再与用户确认
5. 在无歧义时直接撰写更新日志
6. 将更新日志写入 `docs/release_notes/<version>.md`

## 与 CD 工作流的关系

- CD 工作流（`.github/workflows/cd.yml`）通过 workflow_dispatch 手动触发，`create-draft-release.sh` 会在目标 commit 上检查 `docs/release_notes/<version>.md`，**文件缺失会导致发布失败**
- 因此 release notes 必须在触发 CD **之前**提交并推送到 main
- 版本号规则（由 `prepare-release.sh` 校验）：
  - 形如 `0.1.0` 或 `0.1.0-rc.1`，预发布标签仅允许 `alpha`、`beta`、`rc`，分隔符为点（`.`）
  - 不使用 `v` 前缀
  - 预发布版本不能发布为 latest

## tips

- 使用 markdown 格式
- 不允许使用 `gh` 命令，仅基于本地 git 信息工作
- `skills/release-note-creator/scripts/` 下的脚本都支持传递 `--help` 参数查看使用方法
