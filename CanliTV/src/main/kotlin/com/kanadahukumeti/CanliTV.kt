package com.kanadahukumeti

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class CanliTV : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/Doctorwho52/h/refs/heads/main/k.m3u"
    override var name = "CanliTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private val apiUrl = "https://core-api.kablowebtv.com/api/channels"
    private var apiKanallar = mutableListOf<ChannelResult>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageLists = mutableListOf<HomePageList>()

        
        try {
            val m3uKanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val m3uGroups = m3uKanallar.items.groupBy { it.attributes["group-title"] }

            m3uGroups.forEach { group ->
                val title = group.key ?: "Diğer"
                val channels = group.value.map { kanal ->
                    val streamurl = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl = kanal.attributes["tvg-logo"].toString()
                    val chGroup = kanal.attributes["group-title"].toString()
                    val nation = kanal.attributes["tvg-country"].toString()

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation, "m3u").toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }
                homePageLists.add(HomePageList(title, channels, isHorizontalImages = true))
            }
        } catch (e: Exception) {
            Log.e("CanliTV", "M3U parse hatası: ${e.message}")
        }

        
        
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
                "Referer" to "https://tvheryerde.com",
                "Origin" to "https://tvheryerde.com",
                "Cache-Control" to "max-age=0",
                "Connection" to "keep-alive",
                "Accept-Encoding" to "gzip",
                "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbnYiOiJMSVZFIiwiaXBiIjoiMCIsImNnZCI6IjA5M2Q3MjBhLTUwMmMtNDFlZC1hODBmLTJiODE2OTg0ZmI5NSIsImNzaCI6IlRSS1NUIiwiZGN0IjoiRTFDNjQiLCJkaSI6Ijg5MTlmNjYwLTBhZGUtNGYwMS1hMTVlLTc2MDZjNjI4ZTc5MyIsInNnZCI6IjM5MTY0ZjIwLTZlZjUtNDRlZS04ZjAyLWEzODRjOTg1ZTY5MyIsInNwZ2QiOiI5ZjJlYWE1NC01NDM2LTQ0ZTgtYTkyNy00MzQ2NjlkMTU1MWEiLCJpY2giOiIwIiwiaWRtIjoiMCIsImlhIjoiOjpmZmZmOjEwLjAuMC41IiwiYXB2IjoiMS4wLjAiLCJhYm4iOiIxMDAwIiwibmJmIjoxNzQzNDY1MzY5LCJleHAiOjE3NDM0NjU0MjksImlhdCI6MTc0MzQ2NTM2OX0.YWdVfOL5hEZTrd4f4qkmPCPmUUlaiG7I2REW5H0p6Gw"
            )

            val response = app.get(apiUrl, headers = headers)
            val decompressedBody = decompressGzip(response.body)
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val result: CanliTvResult = objectMapper.readValue(decompressedBody)

            if (result.dataResult.allChannels != null) {
                apiKanallar.addAll(result.dataResult.allChannels)
                
                
                val apiGroups = apiKanallar.groupBy { it.categories?.get(0)?.name ?: "Diğer" }
                    .filter { it.key !in listOf("Bilgilendirme", "Yaşam & Eğlence", "Yabancı Haber", "Yabancı", "Müzik") }

                apiGroups.forEach { group ->
                    val title = "${group.key} 2"
                    val channels = group.value.map { kanal ->
                        val streamurl = kanal.streamData?.hlsStreamUrl.toString()
                        val channelname = kanal.name.toString()
                        val posterurl = kanal.primaryLogo.toString()
                        val chGroup = kanal.categories?.get(0)?.name.toString()
                        val nation = "tr"

                        newLiveSearchResponse(
                            channelname,
                            LoadData(streamurl, channelname, posterurl, chGroup, nation, "api").toJson(),
                            type = TvType.Live
                        ) {
                            this.posterUrl = posterurl
                            this.lang = nation
                        }
                    }
                    homePageLists.add(HomePageList(title, channels, isHorizontalImages = true))
                }
            }
        } catch (e: Exception) {
            Log.e("CanliTV", "API parse hatası: ${e.message}")
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()

        
        try {
            val m3uKanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val m3uResults = m3uKanallar.items.filter { 
                it.title.toString().lowercase().contains(query.lowercase()) 
            }.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val chGroup = kanal.attributes["group-title"].toString()
                val nation = kanal.attributes["tvg-country"].toString()

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, chGroup, nation, "m3u").toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            searchResults.addAll(m3uResults)
        } catch (e: Exception) {
            Log.e("CanliTV", "M3U arama hatası: ${e.message}")
        }

        
        val apiResults = apiKanallar.filter { 
            it.name.toString().lowercase().contains(query.lowercase()) 
        }.map { kanal ->
            val streamurl = kanal.streamData?.hlsStreamUrl.toString()
            val channelname = kanal.name.toString()
            val posterurl = kanal.primaryLogo.toString()
            val chGroup = kanal.categories?.get(0)?.name.toString()
            val nation = "tr"

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, "api").toJson(),
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }
        }
        searchResults.addAll(apiResults)

        return searchResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = fetchDataFromUrlOrJson(url)
        val nation: String = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        val recommendations = mutableListOf<LiveSearchResponse>()

        
        if (loadData.source == "m3u") {
            try {
                val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
                for (kanal in kanallar.items) {
                    if (kanal.attributes["group-title"].toString() == loadData.group) {
                        val rcStreamUrl = kanal.url.toString()
                        val rcChannelName = kanal.title.toString()
                        if (rcChannelName == loadData.title) continue

                        val rcPosterUrl = kanal.attributes["tvg-logo"].toString()
                        val rcChGroup = kanal.attributes["group-title"].toString()
                        val rcNation = kanal.attributes["tvg-country"].toString()

                        recommendations.add(newLiveSearchResponse(
                            rcChannelName,
                            LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcNation, "m3u").toJson(),
                            type = TvType.Live
                        ) {
                            this.posterUrl = rcPosterUrl
                            this.lang = rcNation
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e("CanliTV", "M3U recommendations hatası: ${e.message}")
            }
        }

       
        if (loadData.source == "api") {
            for (kanal in apiKanallar) {
                if (kanal.categories?.get(0)?.name.toString() == loadData.group) {
                    val rcStreamUrl = kanal.streamData?.hlsStreamUrl.toString()
                    val rcChannelName = kanal.name.toString()
                    if (rcChannelName == loadData.title) continue

                    val rcPosterUrl = kanal.primaryLogo.toString()
                    val rcChGroup = kanal.categories?.get(0)?.name.toString()
                    val rcNation = "tr"

                    recommendations.add(newLiveSearchResponse(
                        rcChannelName,
                        LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcNation, "api").toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = rcPosterUrl
                        this.lang = rcNation
                    })
                }
            }
        }

        return newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
            this.posterUrl = loadData.poster
            this.plot = nation
            this.tags = listOf(loadData.group, loadData.nation)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("CanliTV", "loadData » $loadData")

        when (loadData.source) {
            "m3u" -> {
                try {
                    val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
                    val kanal = kanallar.items.firstOrNull { it.url == loadData.url }
                    
                    if (kanal != null) {
                        callback.invoke(newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = loadData.url,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = kanal.headers["referrer"] ?: ""
                            this.quality = Qualities.Unknown.value
                            this.headers = kanal.headers
                        })
                    }
                } catch (e: Exception) {
                    Log.e("CanliTV", "M3U loadLinks hatası: ${e.message}")
                }
            }
            "api" -> {
                val kanal = apiKanallar.firstOrNull { 
                    it.streamData?.hlsStreamUrl.toString() == loadData.url 
                }
                
                if (kanal != null) {
                   
                    if (kanal.streamData?.hlsStreamUrl != null) {
                        callback.invoke(newExtractorLink(
                            source = "${kanal.name} - HLS",
                            name = "${kanal.name} - HLS",
                            url = kanal.streamData.hlsStreamUrl,
                            ExtractorLinkType.M3U8
                        ) {
                            this.quality = Qualities.Unknown.value
                        })
                    }
                    
                    
                    
                    if (kanal.streamData?.dashStreamUrl != null) {
                        callback.invoke(newExtractorLink(
                            source = "${kanal.name} - DASH",
                            name = "${kanal.name} - DASH",
                            url = kanal.streamData.dashStreamUrl,
                            ExtractorLinkType.DASH
                        ) {
                            this.quality = Qualities.Unknown.value
                        })
                    }
                }
            }
        }

        return true
    }

    data class LoadData(
        val url: String, 
        val title: String, 
        val poster: String, 
        val group: String, 
        val nation: String,
        val source: String 
    )

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            
            try {
                val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
                val kanal = kanallar.items.firstOrNull { it.url == data }
                
                if (kanal != null) {
                    return LoadData(
                        kanal.url.toString(),
                        kanal.title.toString(),
                        kanal.attributes["tvg-logo"].toString(),
                        kanal.attributes["group-title"].toString(),
                        kanal.attributes["tvg-country"].toString(),
                        "m3u"
                    )
                }
            } catch (e: Exception) {
                Log.e("CanliTV", "M3U fetch hatası: ${e.message}")
            }

            
            val kanal = apiKanallar.firstOrNull { it.streamData?.hlsStreamUrl.toString() == data }
            if (kanal != null) {
                return LoadData(
                    kanal.streamData?.hlsStreamUrl.toString(),
                    kanal.name.toString(),
                    kanal.primaryLogo.toString(),
                    kanal.categories?.get(0)?.name.toString(),
                    "tr",
                    "api"
                )
            }

            
            return LoadData(data, "Bilinmeyen Kanal", "", "Diğer", "tr", "unknown")
        }
    }

    private fun decompressGzip(body: ResponseBody): String {
        GZIPInputStream(body.byteStream()).use { gzipStream ->
            InputStreamReader(gzipStream).use { reader ->
                BufferedReader(reader).use { bufferedReader ->
                    return bufferedReader.readText()
                }
            }
        }
    }
}


data class CanliTvResult(
    @JsonProperty("IsSucceeded") val isSucceeded: Boolean?,
    @JsonProperty("Data") val dataResult: DataResult
)

data class DataResult(
    @JsonProperty("AllChannels") val allChannels: List<ChannelResult>?
)

data class ChannelResult(
    @JsonProperty("UId") val uid: String?,
    @JsonProperty("Name") val name: String?,
    @JsonProperty("PrimaryLogoImageUrl") val primaryLogo: String?,
    @JsonProperty("SecondaryLogoImageUrl") val secondaryLogo: String?,
    @JsonProperty("QualityTypeLogoUrl") val qualityLogo: String?,
    @JsonProperty("StreamData") val streamData: StreamData?,
    @JsonProperty("Categories") val categories: List<Category>?,
)

data class StreamData(
    @JsonProperty("HlsStreamUrl") val hlsStreamUrl: String?,
    @JsonProperty("DashStreamUrl") val dashStreamUrl: String?,
)

data class Category(
    @JsonProperty("UId") val uid: String?,
    @JsonProperty("Name") val name: String?,
)


data class Playlist(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
)

class IptvPlaylistParser {

    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()

                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item = playlistItems[currentIndex]
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                    val referrer = line.getTagValue("http-referrer")

                    val headers = mutableMapOf<String, String>()

                    if (userAgent != null) {
                        headers["user-agent"] = userAgent
                    }

                    if (referrer != null) {
                        headers["referrer"] = referrer
                    }

                    playlistItems[currentIndex] = item.copy(
                        userAgent = userAgent,
                        headers = headers
                    )
                } else {
                    if (!line.startsWith("#")) {
                        val item = playlistItems[currentIndex]
                        val url = line.getUrl()
                        val userAgent = line.getUrlParameter("user-agent")
                        val referrer = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {
                            item.headers + mapOf("referrer" to referrer)
                        } else item.headers

                        playlistItems[currentIndex] = item.copy(
                            url = url,
                            headers = item.headers + urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                        currentIndex++
                    }
                }
            }

            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()

        return attributesString
            .split(Regex("\\s"))
            .mapNotNull {
                val pair = it.split("=")
                if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
            }
            .toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)

        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}