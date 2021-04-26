/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tv.reference.watchnext

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.android.tv.reference.shared.playback.VideoPlaybackState
import timber.log.Timber

/**
 * Notifies the video state and relevant metadata for adding/removing to Watch Next channel. Used
 * for adding un-finished/next content & removing finished content from Watch Next.
 */
class WatchNextPlaybackStateListener(private val context: Context) : Observer<VideoPlaybackState> {
    override fun onChanged(state: VideoPlaybackState) {
        if (!(state is VideoPlaybackState.Pause || state is VideoPlaybackState.End)) {
            return
        }

        Timber.v("State is $state: Notify to Watch Next.")

        val video = when (state) {
            is VideoPlaybackState.Pause -> state.video
            is VideoPlaybackState.End -> state.video
            else -> throw IllegalStateException("Watch Next only operates in Pause or End states.")
        }
        val (playerState, position) = when (state) {
            is VideoPlaybackState.Pause ->
                WatchNextHelper.PLAY_STATE_PAUSED to state.position
            else -> WatchNextHelper.PLAY_STATE_ENDED to video.duration().toMillis()
        }

        // Set relevant data about playback state and video.
        val watchData = Data.Builder().apply {
            putString(WatchNextHelper.VIDEO_ID, video.id)
            putLong(WatchNextHelper.CURRENT_POSITION, position)
            putLong(WatchNextHelper.DURATION, video.duration().toMillis())
            putString(WatchNextHelper.PLAYER_STATE, playerState)
        }

        // Run on a background thread to process playback states and do relevant operations for
        // Watch Next.
        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequest.Builder(WatchNextWorker::class.java)
                .setInputData(watchData.build())
                .build()
        )
    }
}
