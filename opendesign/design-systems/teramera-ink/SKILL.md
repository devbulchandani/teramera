---
name: teramera-ink
description: Second-generation product system for teramera. Ink canvas, cream paper, peach light, mascot-led. Use for all new teramera UI work.
---

# teramera-ink

Load `tokens/colors_and_type.css` for every value. Read `brand/style-notes.md`
before composing screens.

This system replaces `teramera-product`. Do not mix the two in one screen.

## Non-negotiables

- **One dark ground.** Ink (`--ink-900`) is the brand colour; it carries headers,
  the bottom nav, hero cards and full-screen moments. Cream is the paper.
- **Peach is the only primary action colour.** Mint is identity (the "mera" side),
  never a second button colour. Butter is for highlights (selected day, badge).
- **The mascot is present on every screen.** A 36–44px Baanto sits in every
  header; a 96–120px Baanto appears in empty, loading, review and confirmation
  states. It is never absent for more than one navigation transition.
- **Round everything.** Cards 28px, inputs 20px, buttons and chips are pills.
  No square corners, no 4px radii.
- **Organic blobs, not gradients.** Use `--blob-a` / `--blob-b` masks for ink
  headers. Never a blue-purple gradient.
- **Money is tabular**, ₹ prefixed, no decimals when `.00`.
- **Sentence case everywhere.** Buttons are verbs.
- Touch targets ≥ 44px; body ≥ 15px; captions ≥ 12px.

## Composition

- Screen = cream page, ink header blob, content cards, floating ink nav.
- Two surface families only: **ink** (inverse) and **paper** (cream/white).
- Lists are separated by hairlines inside a card, never by a border on every row.
- Icons are outline, 1.75px stroke, 24px.

## Mascot

Baanto is a split coin: peach half ("tere"), mint half ("mera"), cream face,
navy outline. Four states: idle, pleased, thinking, sorry. See
`brand/style-notes.md` for placement rules.
