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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.battleship.fleetcommand.core.domain.Orientation
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
import com.battleship.fleetcommand.core.ui.theme.ShipColors
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
    // ── Base colour: use per-ship hull colour when a shipId is available ──
    val shipHull = if (cell.shipId != null) ShipColors.hullColor(cell.shipId) else NavyPrimary
    val shipAccent = if (cell.shipId != null) ShipColors.accentColor(cell.shipId) else Color.Transparent

    val baseColor = when (cell.state) {
        CellDisplayState.WATER  -> if (showShip) NavySurface else FogOfWar
        CellDisplayState.SHIP   -> if (showShip) shipHull else FogOfWar
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

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cellScale",
    )

    // ── Accessibility ──
    val rowLabel = 'A' + cell.coord.rowOf()
    val colLabel = cell.coord.colOf() + 1
    val stateDesc = when (cell.state) {
        CellDisplayState.WATER -> "Empty"
        CellDisplayState.SHIP  -> "Your ship"
        CellDisplayState.HIT   -> "Hit"
        CellDisplayState.MISS  -> "Miss"
        CellDisplayState.SUNK  -> "Sunk ship"
    }
    val contentDesc = "Row $rowLabel Column $colLabel: $stateDesc"
    val isInteractive = onTap != null && !cell.state.isShot

    // ── Shaped border radius for bow/stern ──
    val isBow = cell.shipCellIndex == 0 && cell.shipSize > 0
    val isStern = cell.shipCellIndex == cell.shipSize - 1 && cell.shipSize > 0
    val isHorizontal = cell.shipOrientation is Orientation.Horizontal

    val cornerShape = when {
        !showShip || cell.state == CellDisplayState.WATER -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
        isBow && isHorizontal -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = cellSizeDp * 0.35f, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = cellSizeDp * 0.35f
        )
        isStern && isHorizontal -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 0.dp, topEnd = cellSizeDp * 0.35f, bottomEnd = cellSizeDp * 0.35f, bottomStart = 0.dp
        )
        isBow && !isHorizontal -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = cellSizeDp * 0.35f, topEnd = cellSizeDp * 0.35f, bottomEnd = 0.dp, bottomStart = 0.dp
        )
        isStern && !isHorizontal -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 0.dp, topEnd = 0.dp, bottomEnd = cellSizeDp * 0.35f, bottomStart = cellSizeDp * 0.35f
        )
        // Submarine: all cells fully rounded
        cell.shipId == com.battleship.fleetcommand.core.domain.ship.ShipId.SUBMARINE && showShip ->
            androidx.compose.foundation.shape.RoundedCornerShape(cellSizeDp * 0.4f)
        else -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    }

    // Whether this cell should show the accent stripe (ship cell, not sunk)
    val showAccentStripe = showShip &&
            cell.state == CellDisplayState.SHIP &&
            cell.shipId != null

    Box(
        modifier = modifier
            .size(cellSizeDp)
            .graphicsLayer { scaleX = scale; scaleY = scale; clip = true; shape = cornerShape }
            .background(baseColor, cornerShape)
            .drawBehind {
                // Grid line border
                drawRect(color = GridLine, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))

                // Accent stripe at the top of ship cells
                if (showAccentStripe) {
                    drawRect(
                        color = shipAccent.copy(alpha = 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width, 3f),
                    )
                }
            }
            .background(animatedHighlight)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
                if (isInteractive) {
                    role = Role.Button
                    stateDescription = "Tap to fire"
                }
            }
            .then(
                if (isInteractive) Modifier.clickable(onClick = onTap!!) else Modifier
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
            .size(
                androidx.compose.ui.unit.DpSize(
                    androidx.compose.ui.unit.Dp.Unspecified,
                    androidx.compose.ui.unit.Dp.Unspecified,
                )
            )
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