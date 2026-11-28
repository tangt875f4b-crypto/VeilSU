package me.veilsu.manager.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.veilsu.manager.R
import me.veilsu.manager.ui.util.formatByteCount
import me.veilsu.manager.ui.util.formatTransferRate

/** Shared copy for the download progress card, used by both UI skins. */
@Composable
internal fun updateProgressTitle(progress: UpdateProgress): String = stringResource(
    when (progress.stage) {
        UpdateStage.DOWNLOADING -> if (progress.isCi) {
            R.string.update_progress_downloading_ci
        } else {
            R.string.update_progress_downloading_release
        }

        UpdateStage.VERIFYING -> R.string.update_progress_verifying
        UpdateStage.INSTALLING -> R.string.update_progress_installing
        UpdateStage.COMPLETED -> R.string.update_progress_completed
        UpdateStage.AWAITING_USER -> R.string.update_progress_awaiting_user
        UpdateStage.FAILED -> R.string.update_progress_failed
    }
)

/** Secondary line: byte counters plus transfer rate while downloading, error text on failure. */
@Composable
internal fun updateProgressDetail(progress: UpdateProgress): String? {
    if (progress.stage == UpdateStage.FAILED) {
        return progress.error
    }
    if (progress.stage != UpdateStage.DOWNLOADING) {
        return null
    }
    val downloaded = formatByteCount(progress.downloadedBytes)
    val speed = formatTransferRate(progress.bytesPerSecond)
    val percent = progress.percent
    return if (percent != null) {
        stringResource(
            R.string.update_progress_detail,
            percent,
            downloaded,
            formatByteCount(progress.totalBytes),
            speed,
        )
    } else {
        stringResource(R.string.update_progress_detail_unknown, downloaded, speed)
    }
}
