// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/GameCell.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/GameCell.kt
package com.battleship.fleetcommand.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.isShot
import com.battleship.fleetcommand.core.ui.theme.FogOfWar
import com.battleship.fleetcommand.core.ui.theme.GridLine
import com.battleship.fleetcommand.core.ui.theme.HitRed
import com.battleship.fleetcommand.core.ui.theme.InvalidRed
import com.battleship.fleetcommand.core.ui.theme.MissWhite
import com.battleship.fleetcommand.core.ui.theme.NavyPrimary
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.core.ui.theme.SunkOrange
import com.battleship.fleetcommand.core.ui.theme.ValidGreen

@Composable
fun GameCell(
    cell: CellViewState,
    cellSizeDp: Dp,
    showShip: Boolean,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baseColor = when (cell.state) {
        CellDisplayState.WATER  -> if (showShip) NavySurface else FogOfWar
        CellDisplayState.SHIP   -> if (showShip) NavyPrimary else FogOfWar
        CellDisplayState.HIT    -> HitRed
        CellDisplayState.MISS   -> MissWhite.copy(alpha = 0.35f)
        CellDisplayState.SUNK   -> SunkOrange
    }

    val highlightColor = when {
        !cell.isHighlighted  -> Color.Transparent
        cell.highlightValid  -> ValidGreen.copy(alpha = 0.5f)
        else                 -> InvalidRed.copy(alpha = 0.5f)
    }
    val animatedHighlight by animateColorAsState(
        targetValue = highlightColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cellHighlight",
    )

    // Subtle scale pulse on already-shot cells when tapped
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cellScale",
    )

    Box(
        modifier = modifier
            .size(cellSizeDp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(baseColor)
            .drawBehind {
                // Canvas border — no extra allocation from Modifier.border
                drawRect(color = GridLine, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            }
            .background(animatedHighlight)
            .then(
                if (onTap != null && !cell.state.isShot)
                    Modifier.clickable(onClick = onTap)
                else
                    Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (cell.state) {
            CellDisplayState.HIT   -> HitMarker()
            CellDisplayState.MISS  -> MissMarker()
            else                   -> Unit
        }
    }
}

@Composable
private fun HitMarker() {
    Box(
        modifier = Modifier
            .size(androidx.compose.ui.unit.DpSize(androidx.compose.ui.unit.Dp.Unspecified, androidx.compose.ui.unit.Dp.Unspecified))
            .drawBehind {
                val r = size.minDimension * 0.3f
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r)
            }
    )
}

@Composable
private fun MissMarker() {
    Box(
        modifier = Modifier.drawBehind {
            val r = size.minDimension * 0.25f
            drawCircle(
                color = MissWhite.copy(alpha = 0.7f),
                radius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    )
}
