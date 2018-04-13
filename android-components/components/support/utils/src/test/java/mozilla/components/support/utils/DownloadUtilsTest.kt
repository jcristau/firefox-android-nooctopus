/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.support.utils

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import junit.framework.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class DownloadUtilsTest {

    private fun assertContentDisposition(expected: String, contentDisposition: String) {
        assertEquals(expected, DownloadUtils.guessFileName(contentDisposition, null, null))
    }

    @Test
    fun testGuessFileName_contentDisposition() {
        // Default file name
        assertContentDisposition("downloadfile.bin", "")
        assertContentDisposition("downloadfile.bin", "attachment")
        assertContentDisposition("downloadfile.bin", "attachment;")
        assertContentDisposition("downloadfile.bin", "attachment; filename")
        assertContentDisposition(".bin", "attachment; filename=")
        assertContentDisposition(".bin", "attachment; filename=\"\"")

        // Provided filename field
        assertContentDisposition("filename.jpg", "attachment; filename=\"filename.jpg\"")
        assertContentDisposition("file\"name.jpg", "attachment; filename=\"file\\\"name.jpg\"")
        assertContentDisposition("file\\name.jpg", "attachment; filename=\"file\\\\name.jpg\"")
        assertContentDisposition("file\\\"name.jpg", "attachment; filename=\"file\\\\\\\"name.jpg\"")
        assertContentDisposition("filename.jpg", "attachment; filename=filename.jpg")
        assertContentDisposition("filename.jpg", "attachment; filename=filename.jpg; foo")
        assertContentDisposition("filename.jpg", "attachment; filename=\"filename.jpg\"; foo")

        // UTF-8 encoded filename* field
        assertContentDisposition("\uD83E\uDD8A + x.jpg",
                "attachment; filename=\"_.jpg\"; filename*=utf-8'en'%F0%9F%A6%8A%20+%20x.jpg")
        assertContentDisposition("filename 的副本.jpg",
                "attachment;filename=\"_.jpg\";" + "filename*=UTF-8''filename%20%E7%9A%84%E5%89%AF%E6%9C%AC.jpg")
        assertContentDisposition("filename.jpg",
                "attachment; filename=_.jpg; filename*=utf-8'en'filename.jpg")

        // ISO-8859-1 encoded filename* field
        assertContentDisposition("file' 'name.jpg",
                "attachment; filename=\"_.jpg\"; filename*=iso-8859-1'en'file%27%20%27name.jpg")
    }

    private fun assertUrl(expected: String, url: String) {
        assertEquals(expected, DownloadUtils.guessFileName(null, url, null))
    }

    @Test
    fun testGuessFileName_url() {
        assertUrl("downloadfile.bin", "http://example.com/")
        assertUrl("downloadfile.bin", "http://example.com/filename/")
        assertUrl("filename.jpg", "http://example.com/filename.jpg")
        assertUrl("filename.jpg", "http://example.com/foo/bar/filename.jpg")
    }
}
