package com.example.teramera.ui.theme

import androidx.compose.ui.graphics.Color

// teramera-ink — ink canvas, cream paper, peach light.
// Hex values are the exact sRGB conversions from
// opendesign/design-systems/teramera-ink/tokens/colors_and_type.css

// Paper
val Paper000 = Color(0xFFFFFDF8)
val Paper100 = Color(0xFFFAF7F2)
val Paper200 = Color(0xFFF4F0E7)
val Paper300 = Color(0xFFEBE6DB)

// Ink
val Ink900 = Color(0xFF121220)
val Ink800 = Color(0xFF1B1B2B)
val Ink700 = Color(0xFF27263A)
val Ink500 = Color(0xFF505063)
val Ink300 = Color(0xFF6D6D7A)

// Accents
val Peach = Color(0xFFF1AB88)
val PeachDeep = Color(0xFFDC855D)
val PeachSoft = Color(0xFFFFE2CA)
val Mint = Color(0xFF78C1B7)
val MintDeep = Color(0xFF3F8980)
val MintSoft = Color(0xFFCFF0EB)
val Butter = Color(0xFFF2D48E)
val ButterSoft = Color(0xFFFAEECD)

// Status
val Pos = Color(0xFF1B7D6C)
val PosSoft = Color(0xFFD7F2EB)
val Neg = Color(0xFFA52B1E)
val NegSoft = Color(0xFFFFE1D9)

// Borders
val BorderSubtle = Paper300
val BorderStrong = Color(0xFFD7D0C4)

// Dark theme
val DInk2 = Color(0xFFBCB6AF)
val DInk3 = Color(0xFF8A867C)
val DBorderSubtle = Color(0xFF2C2C3C)
val DBorderStrong = Color(0xFF404052)

// Dark containers and status (accessible on ink)
val DPeachContainer = Color(0xFF4A3529)
val DMintContainer = Color(0xFF2B4A47)
val DPos = Color(0xFF6FD3BF)
val DPosSoft = Color(0xFF1E3B35)
val DNeg = Color(0xFFFF9A86)
val DNegSoft = Color(0xFF462622)

// Fixed cream for the mascot's face, ring and coin in both themes (never flips).
val MascotCream = Paper100

// Ink used for text/graphics on a peach or mint fill.
val OnAccent = Ink900
