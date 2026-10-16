package com.jarvez.assistant.apps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.jarvez.assistant.util.Logger

sealed class ContactResolutionResult {
    data class Found(val name: String, val phoneNumber: String) : ContactResolutionResult()
    data class Multiple(val contacts: List<Pair<String, String>>) : ContactResolutionResult()
    object NotFound : ContactResolutionResult()
    object PermissionRequired : ContactResolutionResult()
}

class ContactResolver(private val context: Context) {

    fun resolveContact(contactNameQuery: String): ContactResolutionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w("ContactResolver", "READ_CONTACTS permission not granted")
            return ContactResolutionResult.PermissionRequired
        }

        val query = contactNameQuery.trim()
        if (query.isEmpty()) return ContactResolutionResult.NotFound

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val matches = mutableListOf<Pair<String, String>>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    val cleanNumber = cleanPhoneNumber(number)
                    if (cleanNumber.isNotEmpty()) {
                        matches.add(Pair(name, cleanNumber))
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("ContactResolver", "Error reading contacts: ${e.message}")
        } finally {
            cursor?.close()
        }

        val unique = matches.distinctBy { it.second }
        return when {
            unique.isEmpty() -> ContactResolutionResult.NotFound
            unique.size == 1 -> ContactResolutionResult.Found(unique[0].first, unique[0].second)
            else -> {
                // If there's an exact match on name, prioritize it
                val exact = unique.firstOrNull { it.first.equals(query, ignoreCase = true) }
                if (exact != null) {
                    ContactResolutionResult.Found(exact.first, exact.second)
                } else {
                    ContactResolutionResult.Multiple(unique)
                }
            }
        }
    }

    private fun cleanPhoneNumber(raw: String): String {
        return raw.replace(Regex("[^0-9+]"), "")
    }
}
