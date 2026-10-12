package com.gb6105.musictimely

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Runs a shell command as the shell user (grants and listener access) and waits for it to finish. */
fun shell(command: String): String {
    val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
}

const val LISTENER = "com.gb6105.musictimely/com.gb6105.musictimely.service.MediaAccessListener"

fun allowListener() = shell("cmd notification allow_listener $LISTENER")

fun disallowListener() = shell("cmd notification disallow_listener $LISTENER")

fun isListenerAllowed(): Boolean = LISTENER in shell("settings get secure enabled_notification_listeners")

/** Leaves the device as the user had it: a test must not take away access the user granted. */
fun restoreListener(wasAllowed: Boolean) {
    if (wasAllowed) allowListener() else disallowListener()
}
