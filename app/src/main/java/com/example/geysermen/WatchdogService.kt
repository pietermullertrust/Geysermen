package com.example.geysermen

    import android.Manifest
    import android.app.Notification
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.app.Service
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.os.Build
    import android.os.IBinder
    import android.util.Log
    import androidx.core.app.NotificationCompat
    import androidx.core.app.NotificationManagerCompat
    import androidx.core.content.ContextCompat
    import java.io.BufferedReader
    import java.io.InputStreamReader
    import java.net.InetSocketAddress
    import java.net.Socket

    class WatchdogService : Service() {

        companion object {
            private const val TAG = "WatchdogService"
            private const val CHANNEL_ID = "watchdog_service"
            private const val ALERT_CHANNEL_ID = "watchdog_alerts"
            private const val FOREGROUND_ID = 100
        }

        private val alertPort = 9002

        @Volatile
        private var alertListenerRunning = false

        private var masterIP: String = "154.66.153.194"

        override fun onCreate() {
            super.onCreate()
            createServiceChannel()
            createAlertChannel()
            startForeground(FOREGROUND_ID, buildForegroundNotification("Watchdog listener running"))
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            masterIP = intent?.getStringExtra("MASTER_IP") ?: "154.66.153.194"
            Log.d(TAG, "Service started with MASTER_IP=$masterIP")

            if (!alertListenerRunning) {
                startAlertListener()
            }

            return START_STICKY
        }

        override fun onDestroy() {
            alertListenerRunning = false
            super.onDestroy()
        }

        override fun onBind(intent: Intent?): IBinder? = null

        private fun startAlertListener() {
            if (alertListenerRunning) return
            alertListenerRunning = true

            Thread {
                while (alertListenerRunning) {
                    var socket: Socket? = null
                    var reader: BufferedReader? = null

                    try {
                        Log.d(TAG, "Connecting to $masterIP:$alertPort")

                        socket = Socket()
                        socket.keepAlive = true
                        socket.soTimeout = 30000   // was 12000
                        socket.connect(InetSocketAddress(masterIP, alertPort), 5000)

                        Log.d(TAG, "Connected to alert server")


                        reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                        while (alertListenerRunning) {
                            val line = reader.readLine()

                            if (line == null) {
                                Log.w(TAG, "Alert socket closed by server")
                                break
                            }

                            Log.d(TAG, "Received line: $line")

                            if (line.startsWith("ALERT_SERVER_CONNECTED")) {
                                continue
                            }

                            if (line.startsWith("KEEPALIVE|")) {
                                continue
                            }

                            handleWatchdogMessage(line)
                            Log.d(TAG, "Received raw: $line")
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.w(TAG, "Alert listener timed out waiting for data - reconnecting")
                    } catch (e: Exception) {
                        Log.e(TAG, "Alert listener error: ${e.message}", e)
                    } finally {
                        try { reader?.close() } catch (_: Exception) {}
                        try { socket?.close() } catch (_: Exception) {}
                    }

                    if (alertListenerRunning) {
                        Log.d(TAG, "Reconnecting alert socket in 2 seconds...")
                        try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                    }
                }
            }.start()
        }

        private fun handleWatchdogMessage(line: String) {
            val p = line.split("|")
            if (p.size < 5) return

            Log.d(TAG, "Watchdog line: $line")

            val type = p[0]
            val source = p[1]

            // Existing WATCHDOG alert format:
            // ALERT|WATCHDOG|device|ip|event|count
            if (source == "WATCHDOG" && p.size >= 6) {
                val device = p[2]
                val ip = p[3]
                val event = p[4]
                val count = p[5]

                val title = if (type == "ALERT") "Watchdog Alert" else "Watchdog Recovery"
                val body = "$device ($ip) - $event"

                showNotification(title, body)

                val intent = Intent("WATCHDOG_ALERT_EVENT").apply {
                    putExtra("line", line)
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("device", device)
                    putExtra("ip", ip)
                    putExtra("event", event)
                    putExtra("count", count)
                }
                sendBroadcast(intent)
                return
            }

            // New POWER grid alert format:
            // ALERT|POWER|GRID|OFF|PV=...,GRID=...,BAT=...,LOAD=...
            if (source == "POWER" && p.size >= 5) {
                val category = p[2]   // GRID
                val state = p[3]      // OFF or ON
                val details = p[4]

                val title = when {
                    type == "ALERT" && category == "GRID" && state == "OFF" -> "Grid Power Off"
                    type == "OK" && category == "GRID" && state == "ON" -> "Grid Power Restored"
                    else -> "Power Alert"
                }

                val body = "$category $state - $details"

                showNotification(title, body)

                val intent = Intent("WATCHDOG_ALERT_EVENT").apply {
                    putExtra("line", line)
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("device", category)
                    putExtra("ip", "")
                    putExtra("event", state)
                    putExtra("count", details)
                }
                sendBroadcast(intent)
                return
            }
        }

        private fun createServiceChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Watchdog Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background watchdog listener"
                }

                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
        }

        private fun createAlertChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Watchdog Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts from ESP32 watchdog"
                }

                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
        }

        private fun buildForegroundNotification(text: String): Notification {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Geyser watchdog")
                .setContentText(text)
                .setOngoing(true)
                .build()
        }

        private fun showNotification(title: String, message: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Notification permission not granted")
                    return
                }
            }

            val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(false)

            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
