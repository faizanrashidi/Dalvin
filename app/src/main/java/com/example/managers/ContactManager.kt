package com.example.managers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactEntry(
    val id: String,
    val name: String,
    val phone: String,
    val starred: Boolean = false
)

class ContactManager(private val context: Context) {

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun searchContacts(query: String): List<ContactEntry> {
        if (!hasReadContactsPermission()) return emptyList()
        val results = mutableListOf<ContactEntry>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val selection = if (query.isNotBlank()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null
        val args = if (query.isNotBlank()) arrayOf("%$query%", "%$query%") else null

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
            while (cursor.moveToNext() && results.size < 30) {
                results.add(
                    ContactEntry(
                        id = cursor.getString(idIdx).orEmpty(),
                        name = cursor.getString(nameIdx).orEmpty(),
                        phone = cursor.getString(numIdx).orEmpty(),
                        starred = cursor.getInt(starIdx) == 1
                    )
                )
            }
        }
        return results.distinctBy { it.name + it.phone }
    }

    fun getFavoriteContacts(): List<ContactEntry> {
        if (!hasReadContactsPermission()) return emptyList()
        return searchContacts("").filter { it.starred }
    }

    fun openDialer(number: String): String {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opened dialer for $number."
        } catch (e: Exception) {
            "Unable to open dialer: ${e.localizedMessage}"
        }
    }

    fun placeDirectCall(number: String): String {
        if (!hasCallPermission()) {
            return openDialer(number) + " (Grant CALL_PHONE for direct calling)"
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Calling $number..."
        } catch (e: Exception) {
            "Call failed: ${e.localizedMessage}"
        }
    }

    fun openContactsSearchUi(query: String): String {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened Contacts search for '$query'."
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                "Opened Contacts app."
            }.getOrElse { "Contacts app unavailable." }
        }
    }

    fun openInsertContact(name: String, phone: String = ""): String {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened new contact editor for $name."
        }.getOrElse { "Unable to open contact editor." }
    }

    fun openSystemCallHistory(): String {
        // Google Play restricts READ_CALL_LOG for non-default-dialer apps; open system Call Log legally.
        val intent = Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened system Call Log (direct READ_CALL_LOG is restricted by Google Play policy unless set as Default Phone app)."
        }.getOrElse {
            openDialer("")
        }
    }
}
