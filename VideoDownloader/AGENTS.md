# 準則（必須遵守）

## 語言與 QA 日誌

- 所有回答一律以中文顯示。
- 每個問題都必須在 `docs/qa/` 目錄產生一份日誌檔案，命名格式為 `QUESTION_BRIEF_YYYYMMDD-hhmmss.log`：
  - `QUESTION_BRIEF` 為該問題的簡略描述（檔名安全字元）。
  - `YYYYMMDD-hhmmss` 為該問題發生時的當地年月日時分秒。
  - 檔案內容應記錄問題簡述、時間與對應回答。

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.