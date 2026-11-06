package com.mehul.inkless.capture.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.mehul.inkless.capture.IncomingSms

object SmsPermissions {
    val required = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun granted(context: Context): Boolean =
        required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/** Reads past messages from the phone's SMS inbox through Android's SMS content provider (needs READ_SMS). */
object SmsInbox {

    /** Messages received at or after [sinceMillis], oldest first. */
    fun read(context: Context, sinceMillis: Long): List<IncomingSms> {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return emptyList()

        return cursor.use {
            val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val dateSent = it.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            buildList {
                while (it.moveToNext()) {
                    add(
                        IncomingSms(
                            sender = it.getString(address).orEmpty(),
                            body = it.getString(body).orEmpty(),
                            sentAt = it.getLong(dateSent),
                            receivedAt = it.getLong(date),
                        ),
                    )
                }
            }
        }
    }
}
