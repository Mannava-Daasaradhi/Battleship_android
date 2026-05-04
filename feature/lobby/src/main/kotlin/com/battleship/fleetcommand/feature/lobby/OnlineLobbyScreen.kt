package com.battleship.fleetcommand.feature.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.battleship.fleetcommand.navigation.WaitingForOpponentRoute

private val NavyDeep   = Color(0xFF050E1A)
private val NavyMid    = Color(0xFF0A1930)
private val NavyAccent = Color(0xFF1A4A8A)
private val GoldAccent = Color(0xFFD4AF37)
private val TextWhite  = Color(0xFFF0F4FF)
private val TextMuted  = Color(0xFF8090B0)

@Composable
fun OnlineLobbyScreen(
    navController: NavController,
    viewModel: LobbyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LobbyUiEffect.NavigateToWaiting -> {
                    navController.navigate(
                        WaitingForOpponentRoute(
                            gameId   = effect.gameId,
                            roomCode = effect.roomCode   // now forwarded
                        )
                    )
                }
                is LobbyUiEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = NavyAccent,
                    contentColor   = TextWhite
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(NavyDeep, NavyMid)))
                .padding(paddingValues)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text          = "⚓  ONLINE BATTLE",
                    color         = GoldAccent,
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                when (uiState.mode) {
                    LobbyMode.CHOOSE  -> ChoosePanel(
                        isLoading = uiState.isLoading,
                        onHost    = { viewModel.onEvent(LobbyUiEvent.HostGame) },
                        onJoin    = { viewModel.onEvent(LobbyUiEvent.JoinGame) }
                    )
                    LobbyMode.HOSTING -> HostingPanel(
                        roomCode  = uiState.roomCode,
                        isLoading = uiState.isLoading,
                        onCancel  = { viewModel.onEvent(LobbyUiEvent.CancelLobby) }
                    )
                    LobbyMode.JOINING -> JoiningPanel(
                        codeInput    = uiState.roomCodeInput,
                        isLoading    = uiState.isLoading,
                        onCodeChange = { viewModel.onEvent(LobbyUiEvent.RoomCodeInputChanged(it)) },
                        onConfirm    = { viewModel.onEvent(LobbyUiEvent.ConfirmJoin) },
                        onCancel     = { viewModel.onEvent(LobbyUiEvent.CancelLobby) }
                    )
                }
            }
        }
    }
}

// ── Choose panel ──────────────────────────────────────────────────────────────

@Composable
private fun ChoosePanel(isLoading: Boolean, onHost: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = GoldAccent)
        } else {
            LobbyButton(text = "HOST GAME", onClick = onHost, primary = true)
            LobbyButton(text = "JOIN GAME", onClick = onJoin, primary = false)
        }
    }
}

// ── Hosting panel ─────────────────────────────────────────────────────────────

@Composable
private fun HostingPanel(roomCode: String, isLoading: Boolean, onCancel: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (isLoading || roomCode.isBlank()) {
            CircularProgressIndicator(color = GoldAccent)
            Text(text = "Creating game…", color = TextMuted, fontSize = 14.sp)
        } else {
            RoomCodeDisplay(code = roomCode)
            Text(
                text      = "Share this code with your opponent",
                color     = TextMuted,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Waiting for opponent to join…",
                color     = TextWhite,
                fontSize  = 15.sp,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onCancel,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
            ) { Text("CANCEL") }
        }
    }
}

// ── Joining panel ─────────────────────────────────────────────────────────────

@Composable
private fun JoiningPanel(
    codeInput: String,
    isLoading: Boolean,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Enter the 6-character room code", color = TextWhite, fontSize = 15.sp)

        OutlinedTextField(
            value         = codeInput,
            onValueChange = onCodeChange,
            label         = { Text("Room Code", color = TextMuted) },
            singleLine    = true,
            enabled       = !isLoading,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType   = KeyboardType.Ascii,
                imeAction      = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onConfirm() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = TextWhite,
                unfocusedTextColor   = TextWhite,
                focusedBorderColor   = GoldAccent,
                unfocusedBorderColor = NavyAccent,
                cursorColor          = GoldAccent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isLoading) {
            CircularProgressIndicator(color = GoldAccent)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick  = onCancel,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) { Text("BACK") }
                Button(
                    onClick  = onConfirm,
                    enabled  = codeInput.length == 6,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = NavyDeep)
                ) { Text("JOIN", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── Room code display (host lobby) ────────────────────────────────────────────

@Composable
private fun RoomCodeDisplay(code: String) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .background(NavyAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "ROOM CODE", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                code.forEach { char ->
                    Box(
                        modifier         = Modifier
                            .size(width = 38.dp, height = 48.dp)
                            .background(NavyDeep, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = char.toString(),
                            color      = GoldAccent,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Shared button ─────────────────────────────────────────────────────────────

@Composable
private fun LobbyButton(text: String, onClick: () -> Unit, primary: Boolean) {
    if (primary) {
        Button(
            onClick  = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = NavyDeep)
        ) { Text(text = text, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
    } else {
        OutlinedButton(
            onClick  = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
        ) { Text(text = text, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
    }
}