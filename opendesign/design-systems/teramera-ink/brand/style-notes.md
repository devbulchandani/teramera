# teramera-ink · style notes

## What changed from v1

v1 was warm cream with teal/violet accents and small radii. v2 keeps the
warmth but commits to a **dark ground**: ink navy carries the brand, cream is
the paper, peach is the light. The result should feel like a printed sticker
sheet: high contrast, soft shapes, one friendly character.

## Surfaces

- **Ink** — headers, nav, hero cards, full-screen onboarding, the add-expense
  sheet header. Ink is never a small chip; it is a surface.
- **Paper** — the page and every content card. Cards are white-cream (`#FFFDF8`)
  on the page cream (`--paper-100`) with `--shadow-card`.
- **Peach** — the single action colour: primary buttons, active nav, the amount
  in a hero, the selected split method.
- **Mint** — identity only: the "mera" half of the mascot, positive balances,
  your own avatar edge.
- **Butter** — highlight: selected calendar day, a badge, "you're owed".

## Shape

| element | radius |
|---|---|
| card | 28px |
| sheet / modal | 36px top |
| input | 20px |
| button | pill |
| chip | pill |
| avatar | circle |

Headers use an organic blob (`--blob-a` / `--blob-b`), not a straight edge.
Blobs bleed off the top-left or top-right; never both.

## Type

- Display: **Bricolage Grotesque 700/800**, tight tracking, sentence case.
- UI: **Instrument Sans 400/500/600**.
- Screen titles 30px, hero money 44px, body 15px, caption 12–13px.
- Numbers tabular. Never letter-space money.

## Mascot placement

- Every screen header: 36–40px Baanto, left of the title or right of the actions.
- Empty / loading / review / confirmation: 96–120px.
- Never on top of a photo or a busy illustration.
- Never speaks. Copy sits beside it.

## Navigation

Floating ink pill, 16px from the screen edges, 12px from the bottom inset.
Four items; active item gets a peach dot and peach icon. A cream FAB with an
ink plus sits above the bar, overlapping its top edge by 12px.

## Motion

- Screen transitions: 280ms, fade + 8px rise.
- Selected chip / tab: 150ms.
- Mascot idle: 2.4° sway, 4s loop. Pleased: one 380ms squash.
- Sheet: spring, 420ms, never bounces past 8px.

## Don't

- No teal/violet from v1.
- No gradients between accents.
- No outlined-everything; borders are hairlines inside cards only.
- No square corners.
- No mascot-free screens.
