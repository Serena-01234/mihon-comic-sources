package eu.kanade.tachiyomi.extension.zh.fuman

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Fuman : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException("The site has no latest-updates feed")

    // region popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val seen = mutableSetOf<String>()
        val mangas = document.select("a[href^=/manhua/]")
            .mapNotNull { link ->
                val href = link.absUrl("href")
                if (!href.matches(MANGA_URL_REGEX) || !seen.add(href)) return@mapNotNull null
                val image = link.selectFirst("img") ?: return@mapNotNull null
                val title = image.attr("alt").substringAfter("《", "").substringBefore("》", "")
                    .ifBlank { link.attr("title").substringAfter("《", "").substringBefore("》", "") }
                    .ifBlank { return@mapNotNull null }
                SManga.create().apply {
                    setUrlWithoutDomain(href)
                    this.title = title
                    thumbnail_url = coverOf(image)
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // endregion

    // region search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("search/index")
            .addQueryParameter("keywords", URLEncoder.encode(query, "UTF-8"))
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".comic-sort.col3.search-result li, .search-result li")
            .mapNotNull { element -> element.toSManga() }
            .distinctBy { it.url }

        val hasNext = document.selectFirst("a[href*=/search/index][href*=&page=]") != null
        return MangasPage(mangas, hasNext)
    }

    private fun Element.toSManga(): SManga? {
        val link = selectFirst("a[href]") ?: return null
        val href = link.absUrl("href")
        if (!href.matches(MANGA_URL_REGEX)) return null
        val title = selectFirst("p.title")?.text()?.trim()
            ?: selectFirst("p")?.text()?.trim()
            ?: link.attr("title").substringBefore(",").trim()
        if (title.isBlank()) return null
        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = selectFirst("img")?.let { coverOf(it) }
        }
    }

    // endregion

    // region details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()

        val updatedManga = manga.apply {
            document.selectFirst(".comic-info .comic-detail h1, .comic-info h1")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { title = it }
            document.selectFirst(".comic-info img, .comic-cover img")?.let { thumbnail_url = coverOf(it) }
            description = document.selectFirst(".comic-info .comic-detail")?.text()?.trim()
                ?: description
        }

        val updatedChapters = document.select(".weui-cells a[href]")
            .mapNotNull { element ->
                val href = element.absUrl("href")
                if (!href.matches(CHAPTER_URL_REGEX)) return@mapNotNull null
                val name = element.selectFirst(".weui-cell__bd")?.text()?.trim()
                    ?.ifBlank { element.text().trim() }
                    ?: element.text().trim()
                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    this.name = name.ifBlank { "Chapter" }
                }
            }
            // the "开始阅读" shortcut duplicates the first chapter link
            .distinctBy { it.url }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // endregion

    // region pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = baseUrl + chapter.url
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select(".center.padding5 img, .comic-content img, #chapter-content img")
            .mapNotNull { it.imageUrl() }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, url, imageUrl = imageUrl) }
    }

    private fun Element.imageUrl(): String? = backgroundOf()
        ?: listOf("data-original", "data-src", "src")
            .map { absUrl(it) }
            .firstOrNull { it.startsWith("http") && !it.startsWith("data:") }

    /** Covers are lazy-loaded through a CSS background on both the home page and search results. */
    private fun coverOf(image: Element): String? {
        val background = BACKGROUND_REGEX.find(image.attr("style"))?.groupValues?.get(1)
        if (!background.isNullOrBlank() && background.startsWith("http")) return background
        return listOf("data-original", "data-src", "src")
            .map { image.absUrl(it) }
            .firstOrNull { it.startsWith("http") && !it.startsWith("data:") }
    }

    private fun Element.backgroundOf(): String? = BACKGROUND_REGEX.find(attr("style"))?.groupValues?.get(1)?.takeIf { it.startsWith("http") }

    // endregion

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (!url.toString().matches(MANGA_URL_REGEX)) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    companion object {
        private val MANGA_URL_REGEX = Regex("""https?://[^/]+/manhua/\d+\.html""")
        private val CHAPTER_URL_REGEX = Regex("""https?://[^/]+/manhua/capter/\d+""")
        private val BACKGROUND_REGEX = Regex("""url\(['"]?([^'")]+)""")
    }
}
