/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.permission

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import mozilla.components.concept.engine.permission.Permission
import mozilla.components.concept.engine.permission.PermissionRequest
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource.SOURCE_OTHER
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource.SOURCE_SCREEN
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
import java.util.UUID

/**
 * Gecko-based implementation of [PermissionRequest].
 *
 * @property permissions the list of requested permissions.
 * @property callback the callback to grant/reject the requested permissions.
 * @property id a unique identifier for the request.
 */
sealed class GeckoPermissionRequest constructor(
    override val permissions: List<Permission>,
    private val callback: PermissionDelegate.Callback? = null,
    override val id: String = UUID.randomUUID().toString()
) : PermissionRequest {

    /**
     * Represents a gecko-based content permission request.
     *
     * @property uri the URI of the content requesting the permissions.
     * @property type the type of the requested content permission (will be
     * mapped to corresponding [Permission]).
     * @property callback the callback to grant/reject the requested permissions.
     */
    data class Content(
        override val uri: String,
        private val type: Int,
        private val callback: PermissionDelegate.Callback
    ) : GeckoPermissionRequest(
            listOf(permissionsMap.getOrElse(type) { Permission.Generic("$type", "Gecko permission type = $type") }),
            callback
    ) {
        companion object {
            val permissionsMap = mapOf(
                PERMISSION_DESKTOP_NOTIFICATION to Permission.ContentNotification(),
                PERMISSION_GEOLOCATION to Permission.ContentGeoLocation(),
                PERMISSION_AUTOPLAY_AUDIBLE to Permission.ContentAutoPlayAudible(),
                PERMISSION_AUTOPLAY_INAUDIBLE to Permission.ContentAutoPlayInaudible(),
                PERMISSION_PERSISTENT_STORAGE to Permission.ContentPersistentStorage()
            )
        }
    }

    /**
     * Represents a gecko-based application permission request.
     *
     * @property uri the URI of the content requesting the permissions.
     * @property nativePermissions the list of requested app permissions (will be
     * mapped to corresponding [Permission]s).
     * @property callback the callback to grant/reject the requested permissions.
     */
    data class App(
        private val nativePermissions: List<String>,
        private val callback: PermissionDelegate.Callback
    ) : GeckoPermissionRequest(
            nativePermissions.map { permissionsMap.getOrElse(it) { Permission.Generic(it) } },
            callback
    ) {
        override val uri: String? = null

        companion object {
            val permissionsMap = mapOf(
                ACCESS_COARSE_LOCATION to Permission.AppLocationCoarse(ACCESS_COARSE_LOCATION),
                ACCESS_FINE_LOCATION to Permission.AppLocationFine(ACCESS_FINE_LOCATION),
                CAMERA to Permission.AppCamera(CAMERA),
                RECORD_AUDIO to Permission.AppAudio(RECORD_AUDIO)
            )
        }
    }

    /**
     * Represents a gecko-based media permission request.
     *
     * @property uri the URI of the content requesting the permissions.
     * @property videoSources the list of requested video sources (will be
     * mapped to the corresponding [Permission]).
     * @property audioSources the list of requested audio sources (will be
     * mapped to corresponding [Permission]).
     * @property callback the callback to grant/reject the requested permissions.
     */
    data class Media(
        override val uri: String,
        private val videoSources: List<MediaSource>,
        private val audioSources: List<MediaSource>,
        private val callback: PermissionDelegate.MediaCallback
    ) : GeckoPermissionRequest(
            videoSources.map { mapPermission(it) } + audioSources.map { mapPermission(it) }
    ) {
        override fun grant(permissions: List<Permission>) {
            val videos = permissions.mapNotNull { permission -> videoSources.find { it.id == permission.id } }
            val audios = permissions.mapNotNull { permission -> audioSources.find { it.id == permission.id } }
            callback.grant(videos.firstOrNull(), audios.firstOrNull())
        }

        override fun containsVideoAndAudioSources(): Boolean {
            return videoSources.isNotEmpty() && audioSources.isNotEmpty()
        }

        override fun reject() {
            callback.reject()
        }

        companion object {
            fun mapPermission(mediaSource: MediaSource): Permission =
                if (mediaSource.type == MediaSource.TYPE_AUDIO) {
                    mapAudioPermission(mediaSource)
                } else {
                    mapVideoPermission(mediaSource)
                }

            @Suppress("SwitchIntDef")
            private fun mapAudioPermission(mediaSource: MediaSource) = when (mediaSource.source) {
                SOURCE_AUDIOCAPTURE -> Permission.ContentAudioCapture(mediaSource.id, mediaSource.name)
                SOURCE_MICROPHONE -> Permission.ContentAudioMicrophone(mediaSource.id, mediaSource.name)
                SOURCE_OTHER -> Permission.ContentAudioOther(mediaSource.id, mediaSource.name)
                else -> Permission.Generic(mediaSource.id, mediaSource.name)
            }

            @Suppress("ComplexMethod", "SwitchIntDef")
            private fun mapVideoPermission(mediaSource: MediaSource) = when (mediaSource.source) {
                SOURCE_CAMERA -> Permission.ContentVideoCamera(mediaSource.id, mediaSource.name)
                SOURCE_SCREEN -> Permission.ContentVideoScreen(mediaSource.id, mediaSource.name)
                SOURCE_OTHER -> Permission.ContentVideoOther(mediaSource.id, mediaSource.name)
                else -> Permission.Generic(mediaSource.id, mediaSource.name)
            }
        }
    }

    override fun grant(permissions: List<Permission>) {
        callback?.grant()
    }

    override fun reject() {
        callback?.reject()
    }
}
