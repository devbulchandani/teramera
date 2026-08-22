---
name: teramera-product
description: Product design system for teramera, an India-first expense-splitting app. Warm clean aesthetic, deep teal + violet accents, Bricolage Grotesque display type. Use for any teramera UI work.
---

# teramera product system

Load `tokens/colors_and_type.css` for all tokens. Read `brand/style-notes.md` before composing screens.

## Non-negotiables

- Money is always tabular numerals (`font-feature-settings: "tnum"`), ₹ prefix, no decimals when .00
- Two accent hues only: teal (primary action) and violet (secondary/identity). Never blend them into gradients
- Status colors: teal-family green for "owed to you", warm red-orange for "you owe" — never pure red/green defaults
- Warm cream surfaces in light mode; warm brown-black (never gray) in dark mode
- Display type (Bricolage Grotesque) only for screen titles and hero money figures; Instrument Sans everywhere else
- Touch targets ≥ 44px; body copy ≥ 15px on mobile
