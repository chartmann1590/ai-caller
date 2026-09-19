package com.charles.localcallagent.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charles.localcallagent.agent.orchestrator.AgentState
import com.charles.localcallagent.core.model.DialogueTurn
import com.charles.localcallagent.core.model.Speaker
import com.charles.localcallagent.ui.MainViewModel
import com.charles.localcallagent.ui.theme.*

@Composable
fun LiveCallScreen(
    viewModel: MainViewModel,
    onCallEnded: () -> Unit
) {
    val businessName by viewModel.businessName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val durationSeconds by viewModel.callDurationSeconds.collectAsState()
    val transcript by viewModel.liveTranscript.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()

    var showDtmfPad by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom as transcript updates
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    // Auto-navigate to result summary when complete
    LaunchedEffect(agentState) {
        if (agentState == AgentState.COMPLETE || agentState == AgentState.FAILED) {
            onCallEnded()
        }
    }

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    val isHandoff = agentState == AgentState.HANDOFF
    val headerBgColor by animateColorAsState(
        if (isHandoff) WarningAmber.copy(alpha = 0.15f) else DarkSurface
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Call Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = headerBgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = businessName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                Text(
                    text = phoneNumber,
                    fontSize = 13.sp,
                    color = DarkTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Status Badge
                    Surface(
                        color = when (agentState) {
                            AgentState.HANDOFF -> WarningAmber
                            AgentState.COMPLETE -> SafetyShieldGreen
                            AgentState.FAILED -> EmergencyRed
                            else -> BrandPrimary
                        }.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = agentState.name,
                            color = when (agentState) {
                                AgentState.HANDOFF -> WarningAmber
                                AgentState.COMPLETE -> SafetyShieldGreen
                                AgentState.FAILED -> EmergencyRed
                                else -> BrandPrimary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = durationFormatted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Waveform Visualization Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(8) { index ->
                        val barHeight = (4 + (audioRms * 14 * ((index % 3) + 1))).coerceIn(4f, 18f)
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barHeight.dp)
                                .background(
                                    if (isHandoff) WarningAmber else BrandSecondary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }

        // 2. Real-Time Transcript View
        Text(
            text = "LIVE TRANSCRIPT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            items(transcript) { turn ->
                val isBot = turn.speaker == Speaker.BOT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = if (isBot) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = if (isBot) "AI Caller" else businessName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBot) BotBlue else BusinessPurple,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        color = if (isBot) BotBlue.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isBot) 12.dp else 2.dp,
                            bottomEnd = if (isBot) 2.dp else 12.dp
                        ),
                        border = if (isBot) null else null
                    ) {
                        Text(
                            text = turn.text,
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // 3. In-Call Action Controls
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Take Over / Resume Bot Button
                Button(
                    onClick = {
                        if (isHandoff) {
                            viewModel.resumeBot()
                        } else {
                            viewModel.takeOver()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHandoff) SafetyShieldGreen else WarningAmber
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (isHandoff) Icons.Default.SmartToy else Icons.Default.RecordVoiceOver,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHandoff) "RESUME AI CALLER" else "TAKE OVER CALL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // DTMF Keypad Button
                    OutlinedButton(
                        onClick = { showDtmfPad = !showDtmfPad },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Dialpad, contentDescription = "Keypad")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DTMF", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // End Call Button
                    Button(
                        onClick = {
                            viewModel.endCall()
                            onCallEnded()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END CALL", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Optional DTMF Pad Drawer
                if (showDtmfPad) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val keys = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    )
                    Column {
                        for (row in keys) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (digit in row) {
                                    IconButton(
                                        onClick = { viewModel.sendDtmf(digit) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(DarkSurfaceVariant, CircleShape)
                                    ) {
                                        Text("$digit", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}
