@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-contact / changed-key confirmation dialog for SSH TOFU.
 *
 * [onDecision] receives true (trust / reset trust) or false (abort).
 * For a changed key the destructive option is clearly labelled so the user
 * understands that resetting trust accepts a different host key.
 */
@Composable
fun HostKeyTrustDialog(
    prompt: HostKeyPrompt,
    onDecision: (Boolean) -> Unit
) {
    if (prompt.keyChanged) {
        AlertDialog(
            onDismissRequest = { onDecision(false) },
            title = { Text("⚠️ کلید میزبان تغییر کرده", fontWeight = FontWeight.Bold, color = Ds.danger) },
            text = {
                Column {
                    Text(
                        "کلید SSH سرور $prompt.host:${prompt.port} با کلید ذخیره‌شده نمی‌خواند.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "این می‌تواند نشانهٔ حمله MITM باشد، یا اینکه سرور دوباره نصب/ایمیج شده است. " +
                            "تنها در صورتی که هر دو حالت را تأیید می‌کنید، اعتماد را بازنشانی کنید.",
                        fontSize = 12.sp,
                        color = Ds.textSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "کلید جدید: ${prompt.fingerprint}",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        color = Ds.danger
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onDecision(true) }) {
                    Text("بازنشانی اعتماد (مخاطره‌آمیز)", color = Ds.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDecision(false) }) { Text("قطع اتصال") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { onDecision(false) },
            title = { Text("اعتماد به سرور SSH جدید", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "اولین اتصال به $prompt.host:${prompt.port}. کلید میزبان ذخیره می‌شود و در اتصالات بعدی، " +
                            "هر تفاوتی (احتمال MITM) باعث قطع اتصال می‌شود.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Fingerprint SHA-256 کلید:\n${prompt.fingerprint}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        color = Ds.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "اگر این fingerprint را از طریق یک کانال مستقل (تیکت، پیام مستقیم) دریافت کرده‌اید، " +
                            "مطابقت آن را بررسی کنید.",
                        fontSize = 11.sp,
                        color = Ds.textTertiary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onDecision(true) }) { Text("اعتماد و اتصال") }
            },
            dismissButton = {
                TextButton(onClick = { onDecision(false) }) { Text("لغو") }
            }
        )
    }
}
