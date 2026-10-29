# teramera-ink — implementation plan

Internal working document. `docs/` is the GitHub Pages folder, so this file is
publicly reachable if Pages is enabled. No secrets here.

Source of truth:

- Tokens: `opendesign/design-systems/teramera-ink/tokens/colors_and_type.css`
- Rules: `opendesign/design-systems/teramera-ink/SKILL.md` + `brand/style-notes.md`
- Prototypes: `opendesign/mockups/ink-system/theme.html`, `screens.html`,
  `add-expense.html`

Run the preview at the repo root:

```bash
python3 -m http.server 8289   # then open http://localhost:8289/opendesign/
```

---

## Copy-paste prompt for the next session

> You are working in `teramera` (Android, Kotlin, Compose, Hilt, Room).
> Implement the **teramera-ink** theme following
> `docs/ink-palette-implementation.md`. Read that file first, then read
> `opendesign/design-systems/teramera-ink/tokens/colors_and_type.css` and the
> prototypes in `opendesign/mockups/ink-system/`.
>
> Work issue by issue, in order. One commit per issue, commit message
> `ui(ink): <issue>`. Do not change ledger math, sync, auth or the worker.
> After every issue run `./gradlew :app:compileDebugKotlin` and after
> issues 1, 7 and 9 run `./gradlew :app:testDebugUnitTest`.
> Match the prototypes visually; when in doubt the tokens win.
> Do not bump the version or publish until issue 9.

---

## Ground rules

1. **Do not mix systems.** `teramera-product` is retired for UI work. Keep the
   folder for history; do not reference its tokens in new code.
2. **One dark ground.** Ink (`#121220`) carries headers, nav, hero cards and
   full-screen moments. Cream is paper.
3. **Peach is the only primary action colour.** Mint is identity/secondary.
   Butter is highlight only. Never blend accents into gradients.
4. **The mascot is on every screen.** 36–44dp in headers, 96–120dp in empty,
   loading, review and confirmation states.
5. **Round:** cards 28dp, inputs 20dp, sheets 36dp top, buttons/chips pill.
6. **Do not touch** `data/`, `core/network`, `core/auth`, `worker/`, or split
   math. This is a UI change.
7. Keep R8 on. New code must survive minification (no reflection without keep
   rules).

---

## Token map (light)

The CSS is authoritative; hex values below are the exact sRGB conversions to
use in `Color.kt` (Compose has no OKLCH constructor).

| Old constant | New constant | Hex | Role |
|---|---|---|---|
| `Bg1` | `Paper100` | `#FAF7F2` | page background |
| `Bg2` | `Paper200` | `#F4F0E7` | sunken/track |
| `Bg3` | `Paper300` | `#EBE6DB` | pressed/hairline |
| `Ink1` | `Ink900` | `#121220` | primary text, ink surfaces |
| `Ink2` | `Ink500` | `#505063` | secondary text |
| `Ink3` | `Ink300` | `#6D6D7A` | tertiary text (12px+ only) |
| `AccentTeal` | `Peach` | `#F1AB88` | **primary** action |
| `AccentTealSoft` | `PeachSoft` | `#FFE2CA` | selected/soft |
| `AccentViolet` | `Mint` | `#78C1B7` | secondary/identity |
| `AccentVioletSoft` | `MintSoft` | `#CFF0EB` | secondary soft |
| `PosGreen` | `Pos` | `#1B7D6C` | owed to you |
| `PosSoft` | `PosSoft` | `#D7F2EB` | positive soft |
| `NegRed` | `Neg` | `#A52B1E` | you owe |
| `NegSoft` | `NegSoft` | `#FFE1D9` | negative soft |
| `BorderSubtle` | `BorderSubtle` | `#EBE6DB` | hairline |
| `BorderStrong` | `BorderStrong` | `#D7D0C4` | outline |
| `MascotCream` | `MascotCream` | `#FAF7F2` | mascot face/coin |
| `OnAccentLight` | `OnAccent` | `#121220` | text on peach/mint |
| — | `Butter` | `#F2D48E` | highlight |
| — | `ButterSoft` | `#FAEECD` | highlight soft |
| — | `PeachDeep` | `#DC855D` | peach text on cream |
| — | `MintDeep` | `#3F8980` | mint text on cream |

Dark theme:

| New constant | Hex | Role |
|---|---|---|
| `DBg1` → `Ink900` | `#121220` | page |
| `DBg2` → `Ink800` | `#1B1B2B` | raised surface |
| `DBg3` → `Ink700` | `#27263A` | pressed |
| `DInk1` → `Paper100` | `#FAF7F2` | text |
| `DInk2` | `#BCB6AF` | secondary text |
| `DInk3` | `#8A867C` | tertiary text |
| `DAccentTeal` → `Peach` | `#F1AB88` | primary (same) |
| `DAccentViolet` → `Mint` | `#78C1B7` | secondary (same) |
| `DBorderSubtle` | `#2C2C3C` | hairline |
| `DBorderStrong` | `#404052` | outline |
| `DOnAccent` | `#121220` | text on peach/mint |

Critical: **`onPrimary` and `onSecondary` are ink in both themes**, because
peach and mint are light in both. The old theme used white in light mode; that
fails contrast on peach.

---

# Issues

## Issue 0 — branch and baseline

```bash
git checkout main && git pull
git checkout -b feat/ink-theme
./gradlew :app:testDebugUnitTest
```

Acceptance: tests green on the branch baseline.

---

## Issue 1 — theme foundation

**Files**
- `app/src/main/java/com/example/teramera/ui/theme/Color.kt`
- `.../ui/theme/Theme.kt`
- `.../ui/theme/Type.kt`
- new `.../ui/theme/Shape.kt`

**Steps**

1. Rewrite `Color.kt` with the new constants from the token map. Delete the old
   names so no screen can accidentally keep teal/violet. (Only `Baanto.kt`,
   `Theme.kt` and `Color.kt` reference them today; `grep` to confirm.)
2. In `Theme.kt`:
   - Light: `primary = Peach`, `onPrimary = OnAccent`, `primaryContainer =
     PeachSoft`, `onPrimaryContainer = Ink900`, `secondary = Mint`,
     `onSecondary = OnAccent`, `secondaryContainer = MintSoft`,
     `onSecondaryContainer = Ink900`, `tertiary = Pos`, `error = Neg`,
     `background = Paper100`, `onBackground = Ink900`, `surface = Paper200`
     (page cards use `Paper000` separately), `onSurface = Ink900`,
     `surfaceVariant = Paper300`, `onSurfaceVariant = Ink500`,
     `outline = BorderStrong`, `outlineVariant = BorderSubtle`.
   - Dark: `primary = Peach`, `onPrimary = OnAccent`, `secondary = Mint`,
     `background = Ink900`, `surface = Ink800`, `surfaceVariant = Ink700`,
     `onSurface = Paper100`, `onSurfaceVariant = DInk2`, etc.
   - Add `shapes = TerameraShapes` to `MaterialTheme`.
3. Create `Shape.kt`:
   ```kotlin
   val TerameraShapes = Shapes(
       extraSmall = RoundedCornerShape(10.dp),
       small = RoundedCornerShape(14.dp),
       medium = RoundedCornerShape(20.dp),
       large = RoundedCornerShape(28.dp),
       extraLarge = RoundedCornerShape(36.dp),
   )
   ```
4. Update `Type.kt` scale to the tokens: displayLarge 44/46, displayMedium 30/32,
   titleLarge 30/32 (screen titles), titleMedium 17, bodyLarge 15, bodyMedium
   13, bodySmall 12, labelLarge 15, labelMedium 13, labelSmall 12 (never 11 for
   navigation or captions). Add `fontFeatureSettings = "tnum"` to every style
   used for money (`displayLarge`, `displayMedium`, `titleMedium`, `bodySmall`).
5. Add `SideEffect` in `TerameraTheme` to set transparent system bars and
   light/dark icons:
   ```kotlin
   val view = LocalView.current
   SideEffect {
       val window = (view.context as Activity).window
       WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
       WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
   }
   ```

**Acceptance**
- App launches in light and dark with ink/cream surfaces.
- No teal or violet visible anywhere.
- `grep -r "AccentTeal\|AccentViolet" app/src` returns nothing.

---

## Issue 2 — mascot recolor and presence

**Files**
- `ui/components/Baanto.kt`
- `ui/components/EmptyState.kt`
- screen headers already wired: `HomeScreen.kt`, `GroupsScreen.kt`,
  `ActivityScreen.kt`, `SettleScreen.kt`, `GroupDetailScreen.kt`,
  `AddExpenseSheet.kt`, `LoginScreen.kt`, `AppRoot.kt`

**Steps**

1. In `Baanto.kt`, keep the drawing but recolour:
   - left half `MaterialTheme.colorScheme.primary` (peach)
   - right half `MaterialTheme.colorScheme.secondary` (mint)
   - face features: `Ink900` (not cream) — cream on peach/mint fails contrast
   - ring: `MascotCream` at 0.35 alpha
   - coin: `MascotCream` fill, `Ink900` stroke and ₹ glyph
2. Keep `BaantoState` and the `animate` sway.
3. Verify every screen header renders `Baanto(size = 36–40.dp)`; the loader in
   `AppRoot` uses `Thinking`, 72dp, animated.
4. EmptyState uses 104dp; review/confirmation use 64–96dp.
5. Add `Modifier.semantics { contentDescription = "Baanto" }` to the Canvas so
   TalkBack is not silent.

**Acceptance**
- Mascot visible on Login, app start, Home, Groups, Activity, Settle, Group
  detail and all three Add-expense steps.
- No screen is mascot-free.

---

## Issue 3 — app chrome and navigation

**Files**
- `ui/AppRoot.kt`
- `MainActivity.kt`

**Steps**

1. Replace the Material `NavigationBar` with a floating ink pill:
   - outer `Box` with 16dp horizontal, 18dp bottom padding
   - background `Ink900`, radius `CircleShape`/pills, `shadowElevation = 18.dp`
   - 4 items, 44dp min height, 12sp labels, cream inactive / peach active
   - a cream FAB (56dp) overlapping the top edge, plus icon in ink
2. Keep hierarchy-aware selection: `Groups` selected on `groups/{groupId}`.
3. Hide the pill on group detail (it has its own action bar).
4. Ensure `Scaffold` content padding derives from the bar; remove hardcoded
   `96.dp`/`88.dp` bottom paddings from screens once the bar is in place.
5. MainActivity: `enableEdgeToEdge()` already present; keep it, and verify the
   status bar icons flip with the theme (Issue 1).

**Acceptance**
- Nav never overlaps content; selected tab always correct; FAB reachable from
  Home and Groups; no double bars on group detail.

---

## Issue 4 — Home

**Files** `ui/home/HomeScreen.kt`

**Steps**

1. Header: ink blob surface behind the greeting (use a `Box` with
   `RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp)` or a custom
   `Shape` for the organic edge), cream text, 40dp Baanto, avatar.
2. Hero card: ink surface, peach ₹, cream amount, two chips (mint for owed,
   peach/red for owe), "Updated 2m ago" using `TimeFormat.relative`.
3. Segmented tabs: `Paper300` track, `Paper000` selected pill, ink label.
4. Rows move into a single `Paper000` card per section with hairlines, 28dp
   radius, avatars rotating Peach/Mint/Butter.
5. Empty state: `EmptyState` with 104dp Baanto and "Add expense".
6. Reduce the hero's old inverted-ink treatment; ink is now the brand, not an
   exception.

**Acceptance** matches `ink-system/screens.html`, Home.

---

## Issue 5 — Groups and group detail

**Files** `ui/groups/GroupsScreen.kt`, `ui/groups/GroupDetailScreen.kt`

**Steps**

1. Groups header: ink blob, 30dp title, Baanto, round icon button for new group.
2. Group rows become `Paper000` cards with 56dp rounded-square avatars
   (Peach/Mint/Butter rotation) and a date subtitle from `TimeFormat`.
3. Group detail: ink blob header with back/share/Baanto; peach balance card;
   expense rows with `TimeFormat.dayTime`.
4. Replace the current action bar (Material buttons) with the pill pair:
   ghost "Settle up" + peach "Add expense", 54dp, bottom 28dp.
5. Keep the global nav hidden here.

**Acceptance** matches `ink-system/screens.html`, Groups + Group detail.

---

## Issue 6 — Activity and Settle

**Files** `ui/activity/ActivityScreen.kt`, `ui/settle/SettleScreen.kt`

**Steps**

1. Both headers: ink blob, Baanto 40dp.
2. Activity: day sections become `Paper000` cards; keep `TimeFormat` day buckets
   and 15sp event copy; badge glyphs use `--fg` ink on peach/mint soft.
3. Settle: balance rows in cards; settle sheet uses the same peach CTA and
   method pills; confirmation gets a 84dp mint tick, timestamp
   (`TimeFormat.dayTime`) and a peach "Back to settle".
4. Empty states use `EmptyState`.

**Acceptance** matches `ink-system/screens.html`, Activity + Settle.

---

## Issue 7 — Add expense rebuild

**Files** `ui/addexpense/AddExpenseSheet.kt`, `...ViewModel.kt`

**Steps**

1. Keep the ViewModel/ledger logic untouched. This is layout only.
2. Make the `ModalBottomSheet` full-height (`sheetMaxWidth`, `skipPartiallyExpanded
   = true`) and give it a 36dp top radius.
3. Header: ink block, back/close, centered title, 36dp Baanto, three progress
   pills.
4. Step 1 (amount):
   - group pill
   - 56sp peach ₹ amount on ink
   - keypad as 3×4 `Paper000` cards, 58dp, 20dp radius, 8dp gap
   - sticky peach "Continue" (disabled until amount > 0)
5. Step 2 (details):
   - title as a `Paper200` input, 20dp radius, no outline
   - "Who paid?" horizontal avatar row, selected ring 3dp peach
   - split as a 2×2 grid of `Paper300` tiles with a small glyph; selected =
     `PeachSoft` + 2dp peach border
   - "Included" list previewing each share (use existing
     `serverShares`/`previewShares`)
6. Step 3 (review):
   - 64dp pleased Baanto
   - total, "Paid by", "Splitting … · equal"
   - per-person rows with exact shares
   - "When" row with `TimeFormat.dayTime(System.currentTimeMillis())`
   - peach "Split ₹X" CTA
7. Keep validation errors inline and human (`Couldn't split ₹X across 0 people`
   etc. already exists).
8. Do not ship the prototype's fake data; wire the real state.

**Acceptance**
- One decision per step; no long scrolling sheet.
- Saving works exactly as before (all four split types, multi-payer).
- Matches `ink-system/add-expense.html`.

---

## Issue 8 — components, icons, accessibility

**Files** shared UI files + `ui/components/TerameraIcons.kt`

**Steps**

1. Replace text glyphs (`←`, `⇪`, `✕`, `⏻`, `+`, `✓`, `⌫`) with vector icons
   (24dp, 1.75dp stroke). Add them to `TerameraIcons.kt`.
2. Add `contentDescription`/`Role.Button` to every icon-only control; use
   `selectable(selected, role = Role.Tab)` for tabs and split tiles.
3. Enforce 44dp minimum on chips, nav items and segmented tabs
   (`Modifier.heightIn(min = 44.dp)`).
4. Replace remaining `MaterialTheme.typography.*.copy(fontSize = ...)` calls
   with the proper style from Issue 1.
5. Ensure every text color comes from the scheme; remove `Color.White` and
   hardcoded hex from screens.
6. Verify dark mode on every screen; ink surfaces must lift to `Ink800` when
   they share the page colour.

**Acceptance**
- TalkBack can name every control.
- No hardcoded colors (`grep -r "Color(0x" app/src/main/java/com/example/teramera/ui`
  only hits `theme/`).
- All touch targets ≥ 44dp.

---

## Issue 9 — QA and release

**Steps**

1. `./gradlew :app:testDebugUnitTest`
2. `./gradlew :app:lintDebug`
3. `cd worker && python3 smoke.py`
4. `./gradlew :app:assembleRelease` (R8 on) and inspect the APK:
   `apksigner verify --print-certs`, `aapt2 dump badging`.
5. Install the signed release on a device over the current build; exercise:
   login, add expense (all four split types, multi-payer), edit, delete,
   settle, activity, deep-link invite, update prompt, dark mode.
6. Bump `versionCode`/`versionName` in `app/build.gradle.kts` and
   `APP_VERSION_CODE`/`APP_VERSION_NAME` in `worker/wrangler.jsonc` (e.g. 19 /
   0.5.0).
7. Commit, push, `wrangler deploy` with the account token (rotate it after).
8. Optional: tag `v0.5.0` and push to trigger `.github/workflows/release.yml`.

**Acceptance**
- Tests, lint and smoke green.
- Signed APK installs over the previous version and works on device.
- `/app/version` returns the new code.

---

## Order and dependencies

```
0 ─▶ 1 ─▶ 3 ─▶ 4 ─▶ 5 ─▶ 6 ─▶ 7 ─▶ 8 ─▶ 9
        └─▶ 2 (can run in parallel with 3)
```

Issue 1 is a hard dependency for everything. Issues 4–6 can be parallelised by
screen once 1 is in. Issue 7 depends on 1 and the shared components from 8
(do the component pass for buttons/inputs first if you split them).

## Risks

- **Contrast:** peach and mint are light. Never use cream/white text on them;
  use `OnAccent = #121220`. Run a contrast check on every new pair.
- **R8:** the release build is minified. If a screen only breaks in release,
  check Moshi/Retrofit keep rules before touching them.
- **Dark mode:** `Ink900` equals the dark page. Any ink surface must step up to
  `Ink800` in dark, or it disappears.
- **Scope:** do not refactor the ledger during this pass. Keep commits small so
  a visual regression cannot hide inside a math change.
