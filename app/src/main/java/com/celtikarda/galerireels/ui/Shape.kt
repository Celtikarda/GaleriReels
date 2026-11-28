package com.celtikarda.galerireels.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** DESIGN.md `rounded` ölçüleri */
object MakaraShape {
    const val FRAME_DP = 2
    const val SM_DP = 4
    const val DEFAULT_DP = 6
    const val TILE_DP = 8
    const val CONTROL_DP = 18
    const val BAR_DP = 24

    /** Film karesi, film deliği */
    val Frame = RoundedCornerShape(FRAME_DP.dp)
    val Small = RoundedCornerShape(SM_DP.dp)
    /** Kartlar, film şeridi, ay düğmesi */
    val Default = RoundedCornerShape(DEFAULT_DP.dp)
    /** Mozaik kutuları */
    val Tile = RoundedCornerShape(TILE_DP.dp)
    /** Düğme kutuları, etkin sekme */
    val Control = RoundedCornerShape(CONTROL_DP.dp)
    /** Yüzen alt menü */
    val Bar = RoundedCornerShape(BAR_DP.dp)
}
