package com.kidsphoneguard.utils

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 离线客服恢复码的纯计算核心。
 *
 * 输入只包含版本、设备号和恢复页显示的可信日期；Android 状态由 [RecoveryCodeManager] 采集。
 * 算号器必须使用完全相同的规范化、消息格式和 HOTP 动态截断规则。
 */
internal object RecoveryCodeEngine {
    const val CODE_LENGTH = 8
    private const val CODE_MODULUS = 100_000_000
    private const val PAYLOAD_VERSION = "KPG_RESET_V1"
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun generateCode(
        recoveryId: String,
        recoveryDate: String,
        masterSecret: String
    ): String {
        require(masterSecret.isNotBlank()) { "masterSecret must not be blank" }
        val normalizedId = normalizeRecoveryId(recoveryId)
        val normalizedDate = normalizeRecoveryDate(recoveryDate)
        val payload = "$PAYLOAD_VERSION|$normalizedId|$normalizedDate"
        val digest = Mac.getInstance("HmacSHA256").run {
            init(
                SecretKeySpec(
                    masterSecret.toByteArray(StandardCharsets.UTF_8),
                    "HmacSHA256"
                )
            )
            doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        }

        // RFC 4226/HOTP 风格的动态截断，客服算号器使用同一规则。
        val offset = digest.last().toInt() and 0x0F
        val binaryCode =
            ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)
        return (binaryCode % CODE_MODULUS).toString().padStart(CODE_LENGTH, '0')
    }

    fun normalizeRecoveryId(value: String): String {
        val normalized = value
            .filter(Char::isLetterOrDigit)
            .uppercase(Locale.ROOT)
        require(normalized.isNotBlank()) { "recoveryId must not be blank" }
        return normalized
    }

    fun formatRecoveryId(value: String): String =
        normalizeRecoveryId(value).chunked(4).joinToString("-")

    fun normalizeRecoveryDate(value: String): String =
        LocalDate.parse(value, dateFormatter).format(dateFormatter)

    fun normalizeEnteredCode(value: String): String = value.filter(Char::isDigit)

    fun isValidCode(
        recoveryId: String,
        recoveryDate: String,
        enteredCode: String,
        masterSecret: String
    ): Boolean {
        val normalizedEntered = normalizeEnteredCode(enteredCode)
        if (normalizedEntered.length != CODE_LENGTH) {
            return false
        }
        val expected = generateCode(recoveryId, recoveryDate, masterSecret)
        var difference = 0
        expected.indices.forEach { index ->
            difference = difference or
                (expected[index].code xor normalizedEntered[index].code)
        }
        return difference == 0
    }
}
