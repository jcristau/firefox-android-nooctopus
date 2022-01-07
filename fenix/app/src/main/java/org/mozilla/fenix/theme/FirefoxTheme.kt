/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import mozilla.components.ui.colors.PhotonColors

/**
 * The theme for Mozilla Firefox for Android (Fenix).
 */
@Composable
fun FirefoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColorPalette else lightColorPalette

    ProvideFirefoxColors(colors) {
        MaterialTheme(
            content = content
        )
    }
}

object FirefoxTheme {
    val colors: FirefoxColors
        @Composable
        get() = localFirefoxColors.current
}

private val darkColorPalette = FirefoxColors(
    layer1 = PhotonColors.DarkGrey80,
    layer2 = PhotonColors.DarkGrey50,
    layer3 = PhotonColors.DarkGrey60,
    layerAccent = PhotonColors.Violet40,
    layerNonOpaque = PhotonColors.Violet50A32,
    scrim = PhotonColors.DarkGrey05A45,
    scrimAccentStart = PhotonColors.Ink80A96,
    scrimAccentEnd = PhotonColors.DarkGrey90A96,
    gradientStart = PhotonColors.Violet70,
    gradientEnd = PhotonColors.Violet40,
    actionPrimary = PhotonColors.Violet60,
    actionSecondary = PhotonColors.DarkGrey10,
    formDefault = PhotonColors.LightGrey05,
    formSelected = PhotonColors.Violet40,
    formSurface = PhotonColors.DarkGrey05,
    formDisabled = PhotonColors.DarkGrey05,
    formOn = PhotonColors.Violet40,
    formOff = PhotonColors.LightGrey05,
    indicatorActive = PhotonColors.LightGrey90,
    indicatorInactive = PhotonColors.DarkGrey05,
    textPrimary = PhotonColors.LightGrey05,
    textSecondary = PhotonColors.LightGrey40,
    textDisabled = PhotonColors.LightGrey05A40,
    textWarning = PhotonColors.Red40,
    textAccent = PhotonColors.Violet40,
    textInverted = PhotonColors.White,
    iconPrimary = PhotonColors.LightGrey05,
    iconSecondary = PhotonColors.LightGrey40,
    iconDisabled = PhotonColors.LightGrey70,
    iconInverted = PhotonColors.White,
    iconNotice = PhotonColors.Blue30,
    iconButton = PhotonColors.LightGrey05,
    iconWarning = PhotonColors.Red40,
    iconAccentViolet = PhotonColors.Violet20,
    iconAccentBlue = PhotonColors.Blue20,
    iconAccentPink = PhotonColors.Pink20,
    iconAccentGreen = PhotonColors.Green20,
    iconAccentYellow = PhotonColors.Yellow20,
    iconGradientStart = PhotonColors.Violet20,
    iconGradientEnd = PhotonColors.Blue20,
    borderFormDefault = PhotonColors.LightGrey05,
    borderSelected = PhotonColors.Violet40,
    borderDisabled = PhotonColors.LightGrey70,
    borderWarning = PhotonColors.Red40,
    borderDivider = PhotonColors.DarkGrey05
)

private val lightColorPalette = FirefoxColors(
    layer1 = PhotonColors.LightGrey20,
    layer2 = PhotonColors.White,
    layer3 = PhotonColors.LightGrey10,
    layerAccent = PhotonColors.Ink20,
    layerNonOpaque = PhotonColors.Violet70A12,
    scrim = PhotonColors.DarkGrey05A45,
    scrimAccentStart = PhotonColors.DarkGrey90A96,
    scrimAccentEnd = PhotonColors.DarkGrey30A96,
    gradientStart = PhotonColors.Violet70,
    gradientEnd = PhotonColors.Violet40,
    actionPrimary = PhotonColors.Ink20,
    actionSecondary = PhotonColors.LightGrey40,
    formDefault = PhotonColors.DarkGrey90,
    formSelected = PhotonColors.Ink20,
    formSurface = PhotonColors.LightGrey50,
    formDisabled = PhotonColors.LightGrey50,
    formOn = PhotonColors.Ink20,
    formOff = PhotonColors.LightGrey05,
    indicatorActive = PhotonColors.LightGrey50,
    indicatorInactive = PhotonColors.LightGrey30,
    textPrimary = PhotonColors.DarkGrey90,
    textSecondary = PhotonColors.DarkGrey05,
    textDisabled = PhotonColors.DarkGrey90A40,
    textWarning = PhotonColors.Red80,
    textAccent = PhotonColors.Violet70,
    textInverted = PhotonColors.White,
    iconPrimary = PhotonColors.DarkGrey90,
    iconSecondary = PhotonColors.DarkGrey05,
    iconDisabled = PhotonColors.LightGrey70,
    iconInverted = PhotonColors.White,
    iconNotice = PhotonColors.Blue30,
    iconButton = PhotonColors.Ink20,
    iconWarning = PhotonColors.Red80,
    iconAccentViolet = PhotonColors.Violet60,
    iconAccentBlue = PhotonColors.Blue60,
    iconAccentPink = PhotonColors.Pink60,
    iconAccentGreen = PhotonColors.Green60,
    iconAccentYellow = PhotonColors.Yellow60,
    iconGradientStart = PhotonColors.Violet50,
    iconGradientEnd = PhotonColors.Blue60,
    borderFormDefault = PhotonColors.DarkGrey90,
    borderSelected = PhotonColors.Ink20,
    borderDisabled = PhotonColors.LightGrey70,
    borderWarning = PhotonColors.Red80,
    borderDivider = PhotonColors.LightGrey40,
)

/**
 * A custom Color Palette for Mozilla Firefox for Android (Fenix).
 */
@Suppress("LargeClass", "LongParameterList")
@Stable
class FirefoxColors(
    layer1: Color,
    layer2: Color,
    layer3: Color,
    layerAccent: Color,
    layerNonOpaque: Color,
    scrim: Color,
    scrimAccentStart: Color,
    scrimAccentEnd: Color,
    gradientStart: Color,
    gradientEnd: Color,
    actionPrimary: Color,
    actionSecondary: Color,
    formDefault: Color,
    formSelected: Color,
    formSurface: Color,
    formDisabled: Color,
    formOn: Color,
    formOff: Color,
    indicatorActive: Color,
    indicatorInactive: Color,
    textPrimary: Color,
    textSecondary: Color,
    textDisabled: Color,
    textWarning: Color,
    textAccent: Color,
    textInverted: Color,
    iconPrimary: Color,
    iconSecondary: Color,
    iconDisabled: Color,
    iconInverted: Color,
    iconNotice: Color,
    iconButton: Color,
    iconWarning: Color,
    iconAccentViolet: Color,
    iconAccentBlue: Color,
    iconAccentPink: Color,
    iconAccentGreen: Color,
    iconAccentYellow: Color,
    iconGradientStart: Color,
    iconGradientEnd: Color,
    borderFormDefault: Color,
    borderSelected: Color,
    borderDisabled: Color,
    borderWarning: Color,
    borderDivider: Color
) {
    // Layers

    // Default Screen, Search, Frontlayer background
    var layer1 by mutableStateOf(layer1)
        private set
    // Card background, Menu background
    var layer2 by mutableStateOf(layer2)
        private set
    // App Bar Top, App Bar Bottom, Frontlayer header
    var layer3 by mutableStateOf(layer3)
        private set
    // App Bar Top (edit), Header (edit)
    var layerAccent by mutableStateOf(layerAccent)
        private set
    // Selected tab
    var layerNonOpaque by mutableStateOf(layerNonOpaque)
        private set
    var scrim by mutableStateOf(scrim)
        private set
    var scrimAccentStart by mutableStateOf(scrimAccentStart)
        private set
    var scrimAccentEnd by mutableStateOf(scrimAccentEnd)
        private set
    // Tooltip
    var gradientStart by mutableStateOf(gradientStart)
        private set
    // Tooltip
    var gradientEnd by mutableStateOf(gradientEnd)
        private set

    // Actions

    // Primary button, Snackbar, Floating action button, Chip selected
    var actionPrimary by mutableStateOf(actionPrimary)
        private set
    // Secondary button, Chip
    var actionSecondary by mutableStateOf(actionSecondary)
        private set
    // Checkbox default, Radio button default
    var formDefault by mutableStateOf(formDefault)
        private set
    // Checkbox selected, Radio button selected
    var formSelected by mutableStateOf(formSelected)
        private set
    // Switch background OFF, Switch background ON
    var formSurface by mutableStateOf(formSurface)
        private set
    // Checkbox disabled, Radio disabled
    var formDisabled by mutableStateOf(formDisabled)
        private set
    // Switch thumb ON
    var formOn by mutableStateOf(formOn)
        private set
    // Switch thumb OFF
    var formOff by mutableStateOf(formOff)
        private set
    // Scroll indicator active
    var indicatorActive by mutableStateOf(indicatorActive)
        private set
    // Scroll indicator inactive
    var indicatorInactive by mutableStateOf(indicatorInactive)
        private set

    // Text

    // Primary text
    var textPrimary by mutableStateOf(textPrimary)
        private set
    // Secondary text
    var textSecondary by mutableStateOf(textSecondary)
        private set
    // Disabled text
    var textDisabled by mutableStateOf(textDisabled)
        private set
    // Warning text
    var textWarning by mutableStateOf(textWarning)
        private set
    // Small heading, Text link
    var textAccent by mutableStateOf(textAccent)
        private set
    // Text Inverted/On Color
    var textInverted by mutableStateOf(textInverted)
        private set

    // Icon

    // Primary icon
    var iconPrimary by mutableStateOf(iconPrimary)
        private set
    // Secondary icon
    var iconSecondary by mutableStateOf(iconSecondary)
        private set
    // Disabled icon
    var iconDisabled by mutableStateOf(iconDisabled)
        private set
    // Icon inverted (on color)
    var iconInverted by mutableStateOf(iconInverted)
        private set
    // New
    var iconNotice by mutableStateOf(iconNotice)
        private set
    // Icon button
    var iconButton by mutableStateOf(iconButton)
        private set
    var iconWarning by mutableStateOf(iconWarning)
        private set
    var iconAccentViolet by mutableStateOf(iconAccentViolet)
        private set
    var iconAccentBlue by mutableStateOf(iconAccentBlue)
        private set
    var iconAccentPink by mutableStateOf(iconAccentPink)
        private set
    var iconAccentGreen by mutableStateOf(iconAccentGreen)
        private set
    var iconAccentYellow by mutableStateOf(iconAccentYellow)
        private set
    // Reader, ETP Shield
    var iconGradientStart by mutableStateOf(iconGradientStart)
        private set
    // Reader, ETP Shield
    var iconGradientEnd by mutableStateOf(iconGradientEnd)
        private set

    // Border

    // Form parts
    var borderFormDefault by mutableStateOf(borderFormDefault)
        private set
    // Selected tab
    var borderSelected by mutableStateOf(borderSelected)
        private set
    // Form parts
    var borderDisabled by mutableStateOf(borderDisabled)
        private set
    // Form parts
    var borderWarning by mutableStateOf(borderWarning)
        private set
    var borderDivider by mutableStateOf(borderDivider)
        private set

    fun update(other: FirefoxColors) {
        layer1 = other.layer1
        layer2 = other.layer2
        layer3 = other.layer3
        layerAccent = other.layerAccent
        layerNonOpaque = other.layerNonOpaque
        scrim = other.scrim
        scrimAccentStart = other.scrimAccentStart
        scrimAccentEnd = other.scrimAccentEnd
        gradientStart = other.gradientStart
        gradientEnd = other.gradientEnd
        actionPrimary = other.actionPrimary
        actionSecondary = other.actionSecondary
        formDefault = other.formDefault
        formSelected = other.formSelected
        formSurface = other.formSurface
        formDisabled = other.formDisabled
        formOn = other.formOn
        formOff = other.formOff
        indicatorActive = other.indicatorActive
        indicatorInactive = other.indicatorInactive
        textPrimary = other.textPrimary
        textSecondary = other.textSecondary
        textDisabled = other.textDisabled
        textWarning = other.textWarning
        textAccent = other.textAccent
        textInverted = other.textInverted
        iconPrimary = other.iconPrimary
        iconSecondary = other.iconSecondary
        iconDisabled = other.iconDisabled
        iconInverted = other.iconInverted
        iconNotice = other.iconNotice
        iconButton = other.iconButton
        iconWarning = other.iconWarning
        iconAccentViolet = other.iconAccentViolet
        iconAccentBlue = other.iconAccentBlue
        iconAccentPink = other.iconAccentPink
        iconAccentGreen = other.iconAccentGreen
        iconAccentYellow = other.iconAccentYellow
        iconGradientStart = other.iconGradientStart
        iconGradientEnd = other.iconGradientEnd
        borderFormDefault = other.borderFormDefault
        borderSelected = other.borderSelected
        borderDisabled = other.borderDisabled
        borderWarning = other.borderWarning
        borderDivider = other.borderDivider
    }

    fun copy(): FirefoxColors = FirefoxColors(
        layer1 = layer1,
        layer2 = layer2,
        layer3 = layer3,
        layerAccent = layerAccent,
        layerNonOpaque = layerNonOpaque,
        scrim = scrim,
        scrimAccentStart = scrimAccentStart,
        scrimAccentEnd = scrimAccentEnd,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
        actionPrimary = actionPrimary,
        actionSecondary = actionSecondary,
        formDefault = formDefault,
        formSelected = formSelected,
        formSurface = formSurface,
        formDisabled = formDisabled,
        formOn = formOn,
        formOff = formOff,
        indicatorActive = indicatorActive,
        indicatorInactive = indicatorInactive,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textDisabled = textDisabled,
        textWarning = textWarning,
        textAccent = textAccent,
        textInverted = textInverted,
        iconPrimary = iconPrimary,
        iconSecondary = iconSecondary,
        iconDisabled = iconDisabled,
        iconInverted = iconInverted,
        iconNotice = iconNotice,
        iconButton = iconButton,
        iconWarning = iconWarning,
        iconAccentViolet = iconAccentViolet,
        iconAccentBlue = iconAccentBlue,
        iconAccentPink = iconAccentPink,
        iconAccentGreen = iconAccentGreen,
        iconAccentYellow = iconAccentYellow,
        iconGradientStart = iconGradientStart,
        iconGradientEnd = iconGradientEnd,
        borderFormDefault = borderFormDefault,
        borderSelected = borderSelected,
        borderDisabled = borderDisabled,
        borderWarning = borderWarning,
        borderDivider = borderDivider
    )
}

@Composable
fun ProvideFirefoxColors(
    colors: FirefoxColors,
    content: @Composable () -> Unit
) {
    val colorPalette = remember {
        // Explicitly creating a new object here so we don't mutate the initial [colors]
        // provided, and overwrite the values set in it.
        colors.copy()
    }
    colorPalette.update(colors)
    CompositionLocalProvider(localFirefoxColors provides colorPalette, content = content)
}

private val localFirefoxColors = staticCompositionLocalOf<FirefoxColors> {
    error("No FirefoxColors provided")
}
