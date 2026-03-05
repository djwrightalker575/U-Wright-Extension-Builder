# Extension Foundry

Local-first, offline-capable, no-code browser extension generator that exports deterministic Manifest V3 ZIP bundles.

## Install & Run

```bash
npm install
npm run dev
```

Build:

```bash
npm run build
```

Tests:

```bash
npm test
npm run test:e2e
```

## How to Use

1. Home → Create Project.
2. Project → set metadata and targets, optionally apply one of 3 templates.
3. Builder:
   - Add blocks and connect flow in React Flow canvas.
   - Use Selector Picker with `/test_pages/simple.html` (or same-origin URL), enable picker, click elements, save selectors.
   - Run in test harness (Run or Step Mode), inspect timestamped logs.
4. Export → one-click deterministic ZIP export.

## Install Exported ZIP as Unpacked Extension

1. Unzip exported archive.
2. Open `chrome://extensions`.
3. Enable **Developer mode**.
4. Click **Load unpacked** and choose unzipped `extension/` folder.
5. Open target page and click toolbar/popup run button.

## Deterministic ZIP strategy

- Stable file ordering (`path` sort).
- Fixed ZIP timestamps (`2024-01-01T00:00:00Z`).
- Stable JSON stringify with sorted keys.
- Stable workflow/code generation from persisted state.

## MV3 Troubleshooting

- **No action on page**: verify target host pattern is included in `host_permissions`.
- **Cross-origin selector picker blocked**: use in-app same-origin/local test pages, then exported two-phase picker mode via extension injection.
- **Permission warnings**: avoid `<all_urls>` unless explicitly necessary.
- **Notifications not shown**: ensure `NOTIFY` block included so `notifications` permission is generated.

## Repo Tree (key paths)

- `src/app/App.tsx` UI pages (Home/Project/Builder/Export)
- `src/data/db.ts` Dexie schema + migration
- `src/components/*` builder, picker, harness
- `src/compiler/*` compiler + permission minimizer + deterministic tools
- `src/exporter/zip.ts` deterministic JSZip exporter
- `src/templates/templates.ts` sample templates
- `src/compiler/compiler.test.ts` unit tests
- `e2e/templates.spec.ts` Playwright e2e
- `test_pages/simple.html` local harness page
