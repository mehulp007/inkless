package com.mehul.inkless.splits

import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.ui.format.Money
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The nudge sent to a friend who owes the user money, shared to WhatsApp or anywhere else. With
 * the user's UPI ID it ends in a `upi://pay` link: tapping it opens their UPI app with the amount
 * filled in. (Asking for money through UPI itself needs a bank's backing, so a link it is.)
 */
object Reminder {

    private val day = DateTimeFormatter.ofPattern("d MMM")

    fun text(friendName: String, shares: List<ShareLine>, ious: List<IouEntity>, totalPaise: Long, myVpa: String?, myName: String?, zone: ZoneId): String {
        val first = friendName.substringBefore(' ')
        val lines = shares.map { "• ${it.title ?: "Payment"} (${Instant.ofEpochMilli(it.timestamp).atZone(zone).format(day)}): ${rupees(it.sharePaise)}" } +
            ious.filter { it.theyOwe }.map { "• ${it.note ?: "Borrowed"}: ${rupees(it.amountPaise)}" }
        val pay = myVpa?.let { "\n\nPay me on UPI: ${link(it, myName, totalPaise)}" }.orEmpty()
        return "Hi $first! Just a reminder, you owe me ${rupees(totalPaise)}:\n${lines.joinToString("\n")}$pay"
    }

    fun link(vpa: String, name: String?, amountPaise: Long): String =
        "upi://pay?pa=${enc(vpa)}" + (name?.let { "&pn=${enc(it)}" }.orEmpty()) +
            "&am=${amountPaise / 100}.${(amountPaise % 100).toString().padStart(2, '0')}&cu=INR"

    private fun rupees(paise: Long) = Money.format(paise)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
