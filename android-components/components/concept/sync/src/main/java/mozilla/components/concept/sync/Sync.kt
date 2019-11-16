/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.concept.sync

/**
 * Results of running a sync via [SyncableStore.sync].
 */
sealed class SyncStatus {
    /**
     * Sync succeeded successfully.
     */
    object Ok : SyncStatus()

    /**
     * Sync completed with an error.
     */
    data class Error(val exception: Exception) : SyncStatus()
}

/**
 * A Firefox Sync friendly auth object which can be obtained from [OAuthAccount].
 *
 * Why is there a Firefox Sync-shaped authentication object at the concept level, you ask?
 * Mainly because this is what the [SyncableStore] consumes in order to actually perform
 * synchronization, which is in turn implemented by `places`-backed storage layer.
 * If this class lived in `services-firefox-accounts`, we'd end up with an ugly dependency situation
 * between services and storage components.
 *
 * Turns out that building a generic description of an authentication/synchronization layer is not
 * quite the way to go when you only have a single, legacy implementation.
 *
 * However, this may actually improve once we retire the tokenserver from the architecture.
 * We could also consider a heavier use of generics, as well.
 */
data class SyncAuthInfo(
    val kid: String,
    val fxaAccessToken: String,
    val fxaAccessTokenExpiresAt: Long,
    val syncKey: String,
    val tokenServerUrl: String
)

/**
 * An extension of [SyncableStore] that can be locked/unlocked using an encryption key.
 */
interface LockableStore : SyncableStore {
    /**
     * Executes a [block] while keeping the store in an unlocked state. Store is locked once [block] is finished.
     *
     * @param encryptionKey Plaintext encryption key used by the underlying storage implementation (e.g. sqlcipher)
     * to key the store.
     * @param block A lambda to execute while the store is unlocked.
     */
    suspend fun <T> unlocked(encryptionKey: String, block: suspend (store: LockableStore) -> T): T
}

/**
 * Describes a "sync" entry point for a storage layer.
 */
interface SyncableStore {
    /**
     * Performs a sync.
     *
     * @param authInfo Auth information necessary for syncing this store.
     * @return [SyncStatus] A status object describing how sync went.
     */
    suspend fun sync(authInfo: SyncAuthInfo): SyncStatus

    /**
     * This should be removed. See: https://github.com/mozilla/application-services/issues/1877
     *
     * @return raw internal handle that could be used for referencing underlying [PlacesApi]. Use it with SyncManager.
     */
    fun getHandle(): Long
}

/**
 * A set of results of running a sync operation for multiple instances of [SyncableStore].
 */
typealias SyncResult = Map<String, StoreSyncStatus>
data class StoreSyncStatus(val status: SyncStatus)
