package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HelpAndSupportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var supportMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var inquirySent by remember { mutableStateOf(false) }

    val helplines = listOf(
        Triple("National Emergency", "112", "All-in-one police, fire, and medical assistance across India"),
        Triple("National Women Helpline", "1091", "24/7 dedicated helpline for women in distress"),
        Triple("National Cyber Crime Helpline", "1930", "Immediate reporting of cyber fraud, impersonation & harassment"),
        Triple("Citizen Safety Helpline", "100", "State police emergency response")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("help_support_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CharcoalCard)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Help, Safety & Support",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Safety In Dating Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2838)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00B0FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Why Contact Sharing Is Blocked",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To eliminate romance scams, blackmail, and unsolicited calls, Bondly does not allow sharing personal phone numbers, emails, or social media handles in chat. Use our built-in high-definition voice and video calling features to communicate safely.",
                    color = TextLightGrey,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Official Helplines in India
        Text(
            text = "Official India Emergency Helplines",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Tap any number to dial immediately from your phone.",
            color = TextMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            helplines.forEach { (name, number, desc) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$number")
                            }
                            context.startActivity(intent)
                        },
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = desc, color = TextMuted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = number,
                                color = BondlyPink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dating Safety Tips
        Text(
            text = "Essential Safety Tips",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "🛡️ Never send money or UPI payments to anyone you met online, under any circumstances.",
                    "📹 Insist on a video call on Bondly before meeting in person to verify identity.",
                    "☕ For first meetings, always choose crowded public places (malls, popular cafes) during daylight.",
                    "📍 Inform a trusted friend or family member about your plans, location, and return time.",
                    "🚫 Block and report anyone who pressures you for contact numbers or financial favors."
                ).forEach { tip ->
                    Text(text = tip, color = TextLightGrey, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Contact Support Inquiry
        Text(
            text = "Contact Bondly Support",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (inquirySent) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
            ) {
                Text(
                    text = "✓ Support message sent! Our India support desk will review and update you via notifications.",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = supportMessage,
                onValueChange = { supportMessage = it },
                placeholder = { Text("How can our team assist you? Describe your issue...", color = TextMuted, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard,
                    focusedBorderColor = BondlyPink,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryButton(
                text = "Send Message to Support",
                isLoading = isSending,
                enabled = supportMessage.trim().length >= 5 && !isSending,
                onClick = {
                    isSending = true
                    scope.launch {
                        delay(1000L)
                        isSending = false
                        inquirySent = true
                        supportMessage = ""
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}
