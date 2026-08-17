package com.saara.ai

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SaaraNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Yeh incoming notifications ko read karega
        val packageName = sbn?.packageName
        val extras = sbn?.notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getString("android.text")
        
        Log.d("SaaraNotification", "App: $packageName | Title: $title | Text: $text")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
