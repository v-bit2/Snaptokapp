package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Paper Neobrutalism Core Palette
val PaperBgLight = Color(0xFFF7F4EB)        // Warm unbleached heavy cardstock
val PaperSurfaceLight = Color(0xFFFFFFFF)   // Crisp clean paper card
val PaperSurfaceAltLight = Color(0xFFFFFDF5)// Light buttery paper card
val PaperBorderLight = Color(0xFF141416)    // Solid ink outline (1.5-2dp)
val PaperShadowLight = Color(0xFF141416)    // Solid hard paper drop shadow

val PaperBgDark = Color(0xFF141417)         // Matte chalkboard slate
val PaperSurfaceDark = Color(0xFF1E1E24)    // Heavy dark graphite paper card
val PaperSurfaceAltDark = Color(0xFF282830) // Dark card secondary
val PaperBorderDark = Color(0xFFF7F4EB)     // Crisp chalk outline
val PaperShadowDark = Color(0xFF000000)     // Solid pitch shadow

// Punchy Neo-Sticker Accents
val NeoCoral = Color(0xFFFF4757)            // Electric vibrant coral/red
val NeoYellow = Color(0xFFFFDE59)           // Highlighter post-it yellow
val NeoMint = Color(0xFF00E599)             // Vibrant slime mint green
val NeoCyan = Color(0xFF2ED1FF)             // Electric sky cyan
val NeoPurple = Color(0xFFA588EE)           // Pastel neo lilac
val NeoOrange = Color(0xFFFF9F1C)           // Warm citrus sticker orange

// Aliases for compatibility
val CoralPrimary = NeoCoral
val CoralVariant = Color(0xFFFF6B81)
val TealAccent = NeoCyan
val TealDark = Color(0xFF00A8CC)
val StitchBlue = Color(0xFF3867D6)
val StitchBlueLight = Color(0xFF4B7BEC)
val StitchMint = NeoMint
val StitchAmber = NeoYellow
val StitchPurple = NeoPurple

// Surface colors mapped to theme
val DarkCanvas = PaperBgDark
val DarkSurface = PaperSurfaceDark
val DarkSurfaceVariant = PaperSurfaceAltDark
val DarkOutline = PaperBorderDark
val DarkOutlineGlow = PaperBorderDark.copy(alpha = 0.4f)
val DarkOnSurface = Color(0xFFF7F4EB)
val DarkOnSurfaceVariant = Color(0xFFA4A4B0)

val LightCanvas = PaperBgLight
val LightSurface = PaperSurfaceLight
val LightSurfaceVariant = Color(0xFFEEEBE0)
val LightOutline = PaperBorderLight
val LightOutlineGlow = PaperBorderLight.copy(alpha = 0.3f)
val LightOnSurface = Color(0xFF141416)
val LightOnSurfaceVariant = Color(0xFF5C5C66)

val SuccessGreen = NeoMint
val ErrorRed = Color(0xFFFF3838)
