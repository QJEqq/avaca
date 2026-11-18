package com.mydrop.vpn.ui

import com.mydrop.vpn.core.model.AppDesign
import com.mydrop.vpn.ui.theme.PaletteScheme

/** The palette each design paints the app in. */
fun AppDesign.palette(): PaletteScheme = when (this) {
    AppDesign.Avaca -> PaletteScheme.Avaca
    AppDesign.Git -> PaletteScheme.Git
}
