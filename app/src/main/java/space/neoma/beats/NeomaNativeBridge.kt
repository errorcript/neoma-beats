package space.neoma.beats

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NeomaNativeBridge(
    private val context: Context,
    private val offlineManager: NeomaOfflineManager,
    private val onExecuteJs: (String) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Main)

    @JavascriptInterface
    fun isNative(): Boolean = true

    @JavascriptInterface
    fun startBackgroundPlayback(title: String, artist: String, thumbnail: String) {
        val intent = Intent(context, NeomaForegroundService::class.java).apply {
            action = NeomaForegroundService.ACTION_START
            putExtra(NeomaForegroundService.EXTRA_TITLE, title)
            putExtra(NeomaForegroundService.EXTRA_ARTIST, artist)
            putExtra(NeomaForegroundService.EXTRA_IS_PLAYING, true)
        }
        context.startService(intent)
    }

    @JavascriptInterface
    fun updatePlaybackState(isPlaying: Boolean, title: String, artist: String) {
        val intent = Intent(context, NeomaForegroundService::class.java).apply {
            action = NeomaForegroundService.ACTION_UPDATE
            putExtra(NeomaForegroundService.EXTRA_TITLE, title)
            putExtra(NeomaForegroundService.EXTRA_ARTIST, artist)
            putExtra(NeomaForegroundService.EXTRA_IS_PLAYING, isPlaying)
        }
        context.startService(intent)
    }

    @JavascriptInterface
    fun stopBackgroundPlayback() {
        val intent = Intent(context, NeomaForegroundService::class.java).apply {
            action = NeomaForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    @JavascriptInterface
    fun downloadTrack(trackId: String, title: String, artist: String, thumbnail: String, audioUrl: String) {
        scope.launch {
            val success = offlineManager.downloadTrack(trackId, title, artist, thumbnail, audioUrl) { progress ->
                onExecuteJs("window.onNativeDownloadProgress && window.onNativeDownloadProgress('$trackId', $progress);")
            }
            if (success) {
                onExecuteJs("window.onNativeDownloadComplete && window.onNativeDownloadComplete('$trackId');")
            } else {
                onExecuteJs("window.onNativeDownloadFailed && window.onNativeDownloadFailed('$trackId');")
            }
        }
    }

    @JavascriptInterface
    fun getOfflineTracks(): String {
        return offlineManager.getOfflineTracksJson()
    }

    @JavascriptInterface
    fun deleteOfflineTrack(trackId: String): Boolean {
        return offlineManager.deleteTrack(trackId)
    }

    @JavascriptInterface
    fun hasOfflineTrack(trackId: String): Boolean {
        return offlineManager.hasTrack(trackId)
    }

    @JavascriptInterface
    fun getOfflineTrackPath(trackId: String): String {
        return offlineManager.getTrackFilePath(trackId) ?: ""
    }
}
