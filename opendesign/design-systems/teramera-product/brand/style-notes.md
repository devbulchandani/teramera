# Visual style notes

## Decided directions
- Home/balances screen: **Variant A "Ledger"** (hero card + segmented tabs + list rows). Variant B split-columns is archived as an exploration, kept as a toggle in the mockup for reference

## Color roles
- `--accent-1` teal: primary actions (FABs, primary buttons, "you are owed" emphasis, active tab)
- `--accent-2` violet: identity moments — group avatars, the tere|mera logo split, secondary highlights. Never a button fill
- `--pos` / `--neg`: balance semantics only (owed to you / you owe). Soft variants tint chips and rows
- Surfaces stay warm-neutral; color lives in type, chips, and accents — not in large fills

## Type usage
- Screen titles: Bricolage Grotesque 700, 22px
- Hero money figures (home total): Bricolage Grotesque 700, up to 40px, tabular
- Everything else Instrument Sans; money always `font-feature-settings: "tnum"`

## Spacing & layout
- 4pt scale. Screen side padding 20px (`--sp-5`)
- Cards: `--r-lg` 24px on hero cards, `--r-md` 16px on list cards, padding 16–20px
- List row height ≥ 56px; touch targets ≥ 44px

## Cards & borders
- Cards on `--bg-2` with `--shadow-card`; no left-border accent strips
- Hairlines `--border-subtle` only inside lists, never around every card

## Motion
- Press: scale 0.97 + `--t-fast`
- Sheet entry: slide-up 380ms spring (`--t-spring`) with scrim fade
- Balance numbers count up on change (~500ms)
- Tab switch: shared-element-ish crossfade, 260ms

## States
- Hover (tablet/web): surface lift only, no shadow growth spam
- Disabled: 38% opacity, no gray-out of text color
- Selected participant chip: filled accent-1-soft with accent-1 border

## Imagery & avatars
- Member avatars: two-tone split circles for distinguishability — each person gets one teal-dominant or violet-dominant variant (solid accent fill with initials, or accent + soft-accent gradient). Group avatars always mix both hues (teal | violet split)
- The consistent rule is the *split construction* (two-tone, hard 105° divide), not a single color pair
- No stock photography. No illustrations beyond empty states if ever

## Large color fills — exceptions
- The group-detail header uses a full violet radial as a deliberate identity moment (violet = "tere"/group identity side). This is the one sanctioned large-fill; don't repeat it on other screens

## Iconography
- Outline style, 1.75px stroke, 24px grid, currentColor
- No emoji as icons anywhere in UI chrome
