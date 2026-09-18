package com.example.localcallagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localcallagent.ui.MainViewModel
import com.example.localcallagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationScreen(
    viewModel: MainViewModel,
    onStartCall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val businessName by viewModel.businessName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val primaryQuestion by viewModel.primaryQuestion.collectAsState()
    val clarifyingInstructions by viewModel.clarifyingInstructions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "New Call Task",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                Text(
                    text = "Configure inquiry and safety parameters",
                    fontSize = 13.sp,
                    color = DarkTextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = DarkTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Business Information Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Target Business",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = DarkTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { viewModel.businessName.value = it },
                    label = { Text("Business Name") },
                    placeholder = { Text("e.g., Mike's Auto Care, Valley Pharmacy") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandPrimary) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { viewModel.phoneNumber.value = it },
                    label = { Text("Phone Number (E.164)") },
                    placeholder = { Text("+15185550199") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BrandPrimary) }
                )
            }
        }

        // Call Objective Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "What Should AI Ask?",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = DarkTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = primaryQuestion,
                    onValueChange = { viewModel.primaryQuestion.value = it },
                    label = { Text("Primary Inquiry Question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = BrandSecondary) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clarifyingInstructions,
                    onValueChange = { viewModel.clarifyingInstructions.value = it },
                    label = { Text("Clarifying Context / Follow-up") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = BrandSecondary) }
                )
            }
        }

        // Safety Invariant Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SafetyShieldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Safety Invariants Strictly Enforced",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = SafetyShieldGreen
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "✓ 0 Unauthorized Purchases: AI refuses all credit cards, billing, and deposits.\n" +
                    "✓ 0 Autonomous Appointments: AI inquires about availability only; booking requires human takeover.\n" +
                    "✓ Mandatory Disclosure: Discloses AI identity immediately upon pickup.\n" +
                    "✓ Instant Barge-in & Takeover: Tap 'Take Over' at any moment to speak directly.",
                    fontSize = 11.sp,
                    color = DarkTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Start Call Button
        Button(
            onClick = {
                viewModel.startCall(onConnected = onStartCall)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start AI Call", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
