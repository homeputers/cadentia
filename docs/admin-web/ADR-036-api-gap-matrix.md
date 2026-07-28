# ADR-036 API Gap Matrix

This matrix inventories the administrative web interface workflows against the
split OpenAPI contract rooted at `apps/api/src/main/openapi/cadentia-api.yaml`.
The UI must use only documented routes from the generated client.

| ADR-036 workflow | v1 status | Endpoint coverage | Notes |
| --- | --- | --- | --- |
| Admin session bootstrap, role-aware navigation, and capability display | New endpoint | `GET /admin/session` | Returns actor, church-instance scope, roles, and capabilities; backend RBAC remains authoritative. |
| Manual song import and CSV import staging | New endpoint | `POST /admin/song-imports/manual`, `POST /admin/song-imports/csv` | Stages operator-entered song metadata, suggestion details, lyrics/chords, and resources into import batches; does not create approved catalog records directly. |
| Import candidate queue filtering and triage summaries | Existing endpoint | `GET /admin/import-candidates` | Uses ADR-011 governance queue. |
| Candidate detail, provenance, parser evidence, and review history | Existing endpoint with redaction constraints | `GET /admin/import-candidates/{candidateId}` | OpenAPI response shape avoids full copyrighted lyrics and should not expose raw connector payloads in v1 UI. |
| Duplicate comparison and duplicate evidence | Existing endpoint | `GET /admin/import-candidates/{candidateId}/duplicates` | Backend supplies ranked matches and stable IDs. |
| Moderation flag open, assign, resolve, and escalate | Existing endpoint | `POST /admin/import-candidates/{candidateId}/moderation-flags`, `POST /admin/moderation-flags/{flagId}/assign`, `POST /admin/moderation-flags/{flagId}/resolve`, `POST /admin/moderation-flags/{flagId}/escalate` | Allowed actions and audit references should be rendered from backend responses as backend implementation catches up. |
| Audit history inspection | Existing endpoint | `GET /admin/import-candidates/{candidateId}/audit-history` | Append-only audit references are backend-owned. |
| Rollback preview and execution | Existing endpoint | `POST /admin/rollback-previews`, `POST /admin/rollbacks` | High-risk workflow keeps preview separate from execution. |
| Redacted operational diagnostics | New endpoint | `GET /admin/diagnostics` | Response is scoped by `X-Church-Instance-Id` and redacts secrets, raw connector payloads, lyrics, and cross-instance data. |
| Instance configuration | New endpoint | `GET /admin/instance-configuration`, `PUT /admin/instance-configuration` | Includes stable church-instance ID, allowed actions, audit reference, and optimistic concurrency. |
| Feature flag inspection and changes | New endpoint | `GET /admin/feature-flags`, `POST /admin/feature-flags/{flagKey}:preview`, `POST /admin/feature-flags/{flagKey}:confirm` | High-risk changes require preview token plus confirmation. |
| Telegram bot status and channel settings | Existing endpoint | `GET /admin/telegram/bots/{botId}/status`, `PUT /admin/telegram/channels/{channelId}/settings` | Covered by ADR-035 bot operations. |
| Plugin/package operations console | Existing endpoint | `/admin/plugins` and related plugin lifecycle routes | Covered by ADR-030 plugin administration. |
| Recommendation scoring profile authoring | Deferred endpoint | None in v1 | ADR-021 explainability is available through proposal responses; profile editing is deferred until scoring-profile governance is specified. |
| Raw connector payload inspection | Unsupported v1 | None | Deliberately unsupported to avoid leaking credentials, copyrighted content, or provider-specific sensitive payloads. |
| Cross-instance diagnostics | Unsupported v1 | None | Deliberately unsupported; diagnostics are church-instance scoped. |
