package com.gb6105.musictimely.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gb6105.musictimely.domain.repository.NotificationPermissionPort

class AndroidNotificationPermission(private val context: Context) : NotificationPermissionPort {
    override fun needsRuntimeGrant(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
