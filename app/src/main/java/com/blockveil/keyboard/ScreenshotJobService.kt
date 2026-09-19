package com.blockveil.keyboard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore

// Point: "Show copied images on Clipboard" (Settings > Preferences) needs
// screenshots to be caught even when the keyboard/app isn't currently
// running - a plain ContentObserver only fires while some component of this
// app is alive in memory, which an IME service is NOT guaranteed to be in
// the background. JobScheduler's content-trigger jobs are the OS-supported
// way to get woken up for a MediaStore change regardless of process state.
class ScreenshotJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // Point: touches MediaStore/disk, so this runs off the main thread -
        // onStartJob itself must return fast.
        Thread {
            try {
                if (SettingsStore.getBoolean(this, SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES, false) &&
                    hasImagePermission(this)
                ) {
                    checkForNewScreenshot(this)
                }
            } finally {
                // Point: addTriggerContentUri jobs fire once per batch of
                // changes, then stop watching - rescheduling here (rather
                // than only when the setting is toggled on) means a screen-
                // shot taken right after this job runs is still caught.
                schedule(this)
                jobFinished(params, false)
            }
        }.start()
        return true // work continues on the background thread above
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 4302

        // Point: in-memory only - fine for de-duplicating within one
        // process's lifetime; a relaunch just re-checks the latest image
        // once more, which is harmless (ClipboardStore's own addItem dedupe
        // - see its DEDUPE_WINDOW_MS - covers the rest).
        private var lastProcessedUri: String? = null

        fun hasImagePermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= 33) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        // Point: called from SettingsSectionActivity the moment "Show copied
        // images on Clipboard" is turned on (with permission already
        // confirmed), and re-called by this job itself after every run so
        // it keeps watching.
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ScreenshotJobService::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(1500)
                .build()
            scheduler.schedule(job)
        }

        // Point: called when "Show copied images on Clipboard" is turned
        // off, or when SettingsSectionActivity notices the permission was
        // revoked from App Info - stops future wake-ups for this job.
        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }

        // Point: queries for the single most-recently-added image and
        // checks whether it LOOKS like a screenshot (standard "Screenshot"
        // naming or the common Screenshots folder) before adding it - this
        // job runs on every MediaStore image change, not just screenshots,
        // so this filter is what keeps a regular saved photo or downloaded
        // image from also being auto-added.
        private fun checkForNewScreenshot(context: Context) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED
                )
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    ) ?: ""
                    val path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    ) ?: ""
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val uriKey = uri.toString()
                    if (uriKey == lastProcessedUri) return
                    val looksLikeScreenshot = name.contains("Screenshot", ignoreCase = true) ||
                        path.contains("Screenshot", ignoreCase = true)
                    if (!looksLikeScreenshot) return
                    lastProcessedUri = uriKey
                    val base64 = ClipboardStore.encodeImageUriToBase64(context, uri) ?: return
                    ClipboardStore.addImageItem(context, base64)
                }
            } catch (e: Exception) {
                // Silent fail - a permission revoked mid-check, a locked
                // MediaStore row, etc. should never crash this job.
            }
        }
    }
}
