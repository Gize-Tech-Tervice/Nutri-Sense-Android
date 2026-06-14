package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Health Palette - Emerald Green, Orange, Teal, Slate
val PrimaryEmerald = Color(0xFF10B981)
val SecondaryOrange = Color(0xFFF59E0B)
val AccentTeal = Color(0xFF14B8A6)

// Light Theme Ecosystem
val SlateBackgroundLight = Color(0xFFF8FAFC)
val SlateSurfaceLight = Color(0xFFFFFFFF)
val SlateSurfaceVariantLight = Color(0xFFE2E8F0)
val SlateOutlineLight = Color(0xFFCBD5E1)
val SlateTextMainLight = Color(0xFF0F172A)

// Dark Theme Ecosystem
val SlateBackgroundDark = Color(0xFF0F172A)
val SlateSurfaceDark = Color(0xFF1E293B)
val SlateSurfaceVariantDark = Color(0xFF334155)
val SlateOutlineDark = Color(0xFF475569)
val SlateTextMainDark = Color(0xFFF8FAFC)

// Keep legacy names mapped to new theme to avoid breaking other imports if they exist
val MinimalistPrimary = PrimaryEmerald
val MinimalistSecondaryContainer = SlateSurfaceVariantLight
val MinimalistSecondary = SecondaryOrange
val MinimalistBorder = SlateOutlineLight
val MinimalistBackground = SlateBackgroundLight
val MinimalistSurfaceVariant = SlateSurfaceVariantLight
val MinimalistOnBackground = SlateTextMainLight

val MinimalistPrimaryDark = Color(0xFF34D399) // Lighter emerald for dark theme readability
val MinimalistSecondaryContainerDark = SlateSurfaceVariantDark
val MinimalistSecondaryDark = Color(0xFFFBBF24)
val MinimalistBackgroundDark = SlateBackgroundDark
val MinimalistSurfaceVariantDark = SlateSurfaceVariantDark
val MinimalistOnBackgroundDark = SlateTextMainDark


