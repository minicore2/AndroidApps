package com.videodownloader.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistParserTest {

    @Test
    fun hlsMasterExpandsEveryResolution() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            https://cdn.example.com/1080p/index.m3u8?token=abc
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100000,URI="iframe.m3u8"
        """.trimIndent()

        val variants = PlaylistParser.parseHls(
            "https://cdn.example.com/master.m3u8?x=1",
            master
        )

        assertEquals(3, variants.size)
        assertEquals("https://cdn.example.com/360p/index.m3u8", variants[0].url)
        assertEquals("360p", variants[0].quality)
        assertEquals("https://cdn.example.com/720p/index.m3u8", variants[1].url)
        assertEquals("720p", variants[1].quality)
        assertEquals("https://cdn.example.com/1080p/index.m3u8?token=abc", variants[2].url)
        assertEquals("1080p", variants[2].quality)
    }

    @Test
    fun hlsMediaPlaylistHasNoVariants() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertTrue(PlaylistParser.parseHls("https://cdn.example.com/index.m3u8", media).isEmpty())
    }

    @Test
    fun dashRepresentationsWithStandaloneBaseUrlExpand() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" width="1280" height="720" bandwidth="2000000">
                    <BaseURL>video_720.mp4</BaseURL>
                  </Representation>
                  <Representation id="v2" width="1920" height="1080" bandwidth="4000000">
                    <BaseURL>video_1080.mp4</BaseURL>
                  </Representation>
                  <Representation id="a1" mimeType="audio/mp4" bandwidth="128000">
                    <BaseURL>audio.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val variants = PlaylistParser.parseDash(
            "https://cdn.example.com/manifest.mpd",
            manifest
        )

        assertEquals(2, variants.size)
        assertEquals("https://cdn.example.com/video_720.mp4", variants[0].url)
        assertEquals("720p", variants[0].quality)
        assertEquals("https://cdn.example.com/video_1080.mp4", variants[1].url)
        assertEquals("1080p", variants[1].quality)
    }

    @Test
    fun dashSegmentTemplateRepresentationIsNotTreatedAsAFile() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" width="1920" height="1080" bandwidth="4000000">
                    <SegmentTemplate media="seg-${'$'}Number${'$'}.m4s" initialization="init.mp4"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        assertTrue(
            PlaylistParser.parseDash("https://cdn.example.com/manifest.mpd", manifest).isEmpty()
        )
    }

    @Test
    fun qualityFromUrlFindsCommonFilenamePatterns() {
        assertEquals("1080p", PlaylistParser.qualityFromUrl("https://x/video_1080p.mp4"))
        assertEquals("1080p", PlaylistParser.qualityFromUrl("https://x/1920x1080.mp4"))
        assertEquals("720p", PlaylistParser.qualityFromUrl("https://x/1280x720_60.mp4"))
        assertNull(PlaylistParser.qualityFromUrl("https://x/video.mp4"))
    }
}
