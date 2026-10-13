package com.freevibe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The largest corner radius Aura draws. tools/design_token_check.py reads this line. */
const val MAX_CORNER_RADIUS_DP = 12

/**
 * Aura's corner radii. Shapes stay rectangular, 0 to [MAX_CORNER_RADIUS_DP] dp, and nothing sits on a
 * pill, oval or circle. tools/design_token_check.py rejects a corner shape written anywhere else.
 */
object AuraShapes {
    val None = RoundedCornerShape(0.dp)
    val Hairline = RoundedCornerShape(2.dp)
    val Small = RoundedCornerShape(4.dp)
    val Compact = RoundedCornerShape(6.dp)
    val Medium = RoundedCornerShape(8.dp)
    val Large = RoundedCornerShape(12.dp)

    /** A card or panel whose top edge is rounded and whose bottom meets something else. */
    val CardTop = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

    /** A sheet rising from the bottom of the screen. */
    val SheetTop = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}
