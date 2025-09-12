package com.Doctorwho52

import CanliTvResult
import ChannelResult
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.zip.GZIPInputStream

class Canli : MainAPI() {
    override var mainUrl = "https://core-api.kablowebtv.com/api/channels"
    override var name = "Canli"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    private var kanallar = mutableListOf<ChannelResult>()
    private var baseUrl: String? = null
    private var urlParams: String? = null
    private var baseDashUrl: String? = null
    private var dashUrlParams: String? = null

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
            "Referer" to "https://tvheryerde.com",
            "Origin" to "https://tvheryerde.com",
            "Cache-Control" to "max-age=0",
            "Connection" to "keep-alive",
            "Accept-Encoding" to "gzip",
            
            "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbnYiOiJMSVZFIiwiaXBiIjoiMCIsImNnZCI6IjA5M2Q3MjBhLTUwMmMtNDFlZC1hODBmLTJiODE2OTg0ZmI5NSIsImNzaCI6IlRSS1NUIiwiZGN0IjoiM0VGNzUiLCJkaSI6IjNkY2I2NmJiLTZhNjctNDIwYi1iN2MyLTg3ZGQ2MGFjNDNjZCIsInNnZCI6Ijk1N2U3NjliLWJiYjgtNGFiMC05NzYwLTgyM2UyMGE1OWFlMyIsInNwZ2QiOiIxNTY0ODUxZC1hY2ViLTQyZWUtYjkwZi04MGFlNTczOGEyM2EiLCJpY2giOiIwIiwiaWRtIjoiMCIsImlhIjoiOjpmZmZmOjEwLjAuMC42IiwiYXB2IjoiMS4wLjAiLCJhYm4iOiIxMDAwIiwibmJmIjoxNzQwOTY1ODI4LCJleHAiOjE3NDA5NjU4ODgsImlhdCI6MTc0MDk2NTgyOH0.8SgjsXtcwvmCYpV0W2T-rwwUiiFKpluz8crfpRhDv9A"
        )

        try {
            val response = app.get(mainUrl, headers = headers)
            val decompressedBody = decompressGzip(response.body)
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val result: CanliTvResult = objectMapper.readValue(decompressedBody)
            
           
            if (result.isSucceeded != true || result.dataResult.allChannels == null) {
                
                return newHomePageResponse(emptyList(), hasNext = false)
            }

            kanallar.clear()
            kanallar.addAll(result.dataResult.allChannels)
            

           
            setupBaseUrls()

            val newHomePageResponse = newHomePageResponse(
                kanallar.groupBy { it.categories?.get(0)?.name ?: "Genel" }
                    .filter { it.key != "Bilgilendirme" }
                    .map { group ->
                        val title = group.key
                        val show = group.value.mapNotNull { kanal ->
                            createSearchResponse(kanal)
                        }
                        HomePageList(title, show, isHorizontalImages = true)
                    },
                hasNext = false
            )
            return newHomePageResponse
        } catch (e: Exception) {
            
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun setupBaseUrls() {
        
        for (channel in kanallar) {
            
            val hlsUrl = channel.streamData?.hlsStreamUrl
            if (hlsUrl != null && hlsUrl.contains("ottcdn.kablowebtv.net") && baseUrl == null) {
                try {
                    val url = URL(hlsUrl)
                    val pathParts = url.path.split("/")
                    val streamPart = pathParts.find { it.contains("_stream") }
                    if (streamPart != null) {
                        val basePath = url.path.replace("/$streamPart/index.m3u8", "")
                        baseUrl = "${url.protocol}://${url.host}$basePath/"
                        urlParams = if (url.query != null) "?${url.query}" else ""
                        
                    }
                } catch (e: Exception) {
                    
                }
            }
            
            
            val dashUrl = channel.streamData?.dashStreamUrl
            if (dashUrl != null && dashUrl.contains("ottcdn.kablowebtv.net") && baseDashUrl == null) {
                try {
                    val url = URL(dashUrl)
                    val pathParts = url.path.split("/")
                    val streamPart = pathParts.find { it.contains("_stream") }
                    if (streamPart != null) {
                        val basePath = url.path.replace("/$streamPart/index.mpd", "")
                        baseDashUrl = "${url.protocol}://${url.host}$basePath/"
                        dashUrlParams = if (url.query != null) "?${url.query}" else ""
                        
                    }
                } catch (e: Exception) {
                    
                }
            }
            
            
            if (baseUrl != null && baseDashUrl != null) break
        }
    }

    private fun createManualUrl(channelName: String): String? {
        if (baseUrl == null || urlParams == null) return null
        
        val specialChannelUrls = mapOf(
            "Al Quran Al Kareem" to "alquran",
            "Mezzo" to "mezzohd",
            "Classical Harmony" to "classical_harmony",
            "Trt Avaz" to "trtavaz",
            "Trt Kurdi" to "trtkurdi",
            "Haber Global" to "haberglobalhd",
            "CNN International" to "cnnint",
            "Lalegül Tv" to "lalegul",
            "Al Sunnah Al Nabawiyah" to "alsunnah",
            "Myzen Tv" to "myzen",
            "Film Screen" to "FilmScreen",
            "Epic Drama" to "epicdramahd",
            "Cosmo Sports" to "fightscreen",
            "Trace Sports Stars" to "tracesport",
            "Tv5 Monde" to "tv5mondeeurope",
            "Cgtn Documentary" to "cgtndocu",
            "France 24 (French)" to "france24fr",
            "France 24 (English)" to "france24en",
            "Dw Tv (English)" to "dwtv",
            "TRT Arabia" to "trtarabi",
            "Kbs World" to "kbstv",
            "Planeta Rtr" to "rtrplaneta",
            "National Geographic" to "natgeo",
            "National Geographic Wild" to "natgeowild",
            "Love Nature" to "lovenaturehd",
            "Eurosport 1" to "eurosport1hd",
            "Eurosport 2" to "eurosport2hd",
            "HT Spor" to "htsporhd",
            "Moonbug" to "moonbugkids",
            "Ekol Sports" to "yargitv"
        )
        
        val cleanName = if (specialChannelUrls.containsKey(channelName)) {
            specialChannelUrls[channelName]!!
        } else {
            channelName.lowercase()
                .replace(" ", "")
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace(",", "_")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c")
                .replace("-", "")
                .replace("türk", "turk")
                .trim()
        }
        
        return "${baseUrl}${cleanName}_stream/index.m3u8$urlParams"
    }

    private fun createManualDashUrl(channelName: String): String? {
        if (baseDashUrl == null || dashUrlParams == null) return null
        
        val specialChannelUrls = mapOf(
            "Al Quran Al Kareem" to "alquran",
            "Mezzo" to "mezzohd",
            "Classical Harmony" to "classical_harmony",
            "Trt Avaz" to "trtavaz",
            "Trt Kurdi" to "trtkurdi",
            "Haber Global" to "haberglobalhd",
            "CNN International" to "cnnint",
            "Lalegül Tv" to "lalegul",
            "Al Sunnah Al Nabawiyah" to "alsunnah",
            "Myzen Tv" to "myzen",
            "Film Screen" to "FilmScreen",
            "Epic Drama" to "epicdramahd",
            "Cosmo Sports" to "fightscreen",
            "Trace Sports Stars" to "tracesport",
            "Tv5 Monde" to "tv5mondeeurope",
            "Cgtn Documentary" to "cgtndocu",
            "France 24 (French)" to "france24fr",
            "France 24 (English)" to "france24en",
            "Dw Tv (English)" to "dwtv",
            "TRT Arabia" to "trtarabi",
            "Kbs World" to "kbstv",
            "Planeta Rtr" to "rtrplaneta",
            "National Geographic" to "natgeo",
            "National Geographic Wild" to "natgeowild",
            "Love Nature" to "lovenaturehd",
            "Eurosport 1" to "eurosport1hd",
            "Eurosport 2" to "eurosport2hd",
            "HT Spor" to "htsporhd",
            "Moonbug" to "moonbugkids",
            "Ekol Sports" to "yargitv"
        )
        
        val cleanName = if (specialChannelUrls.containsKey(channelName)) {
            specialChannelUrls[channelName]!!
        } else {
            channelName.lowercase()
                .replace(" ", "")
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace(",", "_")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c")
                .replace("-", "")
                .replace("türk", "turk")
                .trim()
        }
        
        return "${baseDashUrl}${cleanName}_stream/index.mpd$dashUrlParams"
    }

    private fun createSearchResponse(kanal: ChannelResult): SearchResponse? {
        val channelName = kanal.name ?: return null
        var streamUrl = kanal.streamData?.hlsStreamUrl
        
        
        if (streamUrl == null || !streamUrl.contains("ottcdn.kablowebtv.net")) {
            streamUrl = createManualUrl(channelName)
        }
        
        if (streamUrl == null) return null
        
        val posterUrl = kanal.primaryLogo ?: ""
        
        return newLiveSearchResponse(
            channelName,
            streamUrl,
            type = TvType.Live
        ) {
            this.posterUrl = posterUrl
            this.lang = "tr"
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return kanallar.filter { 
            it.name?.lowercase()?.contains(query.lowercase()) == true 
        }.mapNotNull { kanal ->
            createSearchResponse(kanal)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        
        
        val channel = kanallar.find { 
            it.streamData?.hlsStreamUrl == url || createManualUrl(it.name ?: "") == url 
        }
        
        return if (channel != null) {
            val loadData = LoadData(
                url,
                channel.name ?: "Bilinmeyen Kanal",
                channel.primaryLogo ?: "",
                channel.categories?.get(0)?.name ?: "Genel",
                "tr"
            )
            
            newLiveStreamLoadResponse(
                loadData.title,
                url,
                url
            ) {
                this.posterUrl = loadData.poster
                this.tags = listOf(loadData.group)
            }
        } else {
            newLiveStreamLoadResponse("Bilinmeyen Kanal", "", url) {
                this.posterUrl = ""
                this.tags = listOf("Genel")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        
        val channel = kanallar.find { 
            it.streamData?.hlsStreamUrl == data || createManualUrl(it.name ?: "") == data 
        }
        
        if (channel != null) {
            val channelName = channel.name ?: "Bilinmeyen Kanal"
            
            
            callback.invoke(
                newExtractorLink(
                    source = "$channelName - HLS",
                    name = "$channelName - HLS", 
                    url = data,
                    ExtractorLinkType.M3U8
                ) {
                    this.quality = Qualities.Unknown.value
                }
            )
            
            
            val originalDashUrl = channel.streamData?.dashStreamUrl
            if (!originalDashUrl.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = "$channelName - DASH",
                        name = "$channelName - DASH",
                        url = originalDashUrl,
                        ExtractorLinkType.DASH
                    ) {
                        this.quality = Qualities.Unknown.value
                    }
                )
                
            } else {
                
                val manualDashUrl = createManualDashUrl(channelName)
                if (manualDashUrl != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = "$channelName - DASH",
                            name = "$channelName - DASH",
                            url = manualDashUrl,
                            ExtractorLinkType.DASH
                        ) {
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    
                }
            }
            
            
            val originalHlsUrl = channel.streamData?.hlsStreamUrl
            if (originalHlsUrl != null && originalHlsUrl != data && originalHlsUrl.contains("ottcdn.kablowebtv.net")) {
                callback.invoke(
                    newExtractorLink(
                        source = "$channelName - HLS",
                        name = "$channelName - HLS",
                        url = originalHlsUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.Unknown.value
                    }
                )
                
            }
        } else {
           
            callback.invoke(
                newExtractorLink(
                    source = "Hls",
                    name = "Hls",
                    url = data,
                    ExtractorLinkType.M3U8
                ) {
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String
    )

    private fun decompressGzip(body: ResponseBody): String {
        return try {
            GZIPInputStream(body.byteStream()).use { gzipStream ->
                InputStreamReader(gzipStream).use { reader ->
                    BufferedReader(reader).use { bufferedReader ->
                        bufferedReader.readText()
                    }
                }
            }
        } catch (e: Exception) {
            
            body.string()
        }
    }
}