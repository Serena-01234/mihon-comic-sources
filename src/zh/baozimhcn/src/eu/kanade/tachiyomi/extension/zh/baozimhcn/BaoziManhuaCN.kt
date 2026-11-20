package eu.kanade.tachiyomi.extension.zh.baozimhcn

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
import okhttp3.Request
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BaoziManhuaCN : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException("The site has no latest-updates feed")

    // region popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/index2", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".comics-card")
            .mapNotNull { it.toSManga() }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // endregion

    // region search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        if (page > 1) return MangasPage(emptyList(), false)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("search")
            .addQueryParameter("q", query)
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".comics-card")
            .mapNotNull { it.toSManga() }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    private fun Element.toSManga(): SManga? {
        val link = selectFirst("a[href]") ?: return null
        val href = link.absUrl("href")
        if (!href.matches(MANGA_URL_REGEX)) return null
        val title = selectFirst(".comics-card__title")?.text()?.trim()
            ?: link.attr("title").substringAfter("《").substringBefore("》")
        if (title.isBlank()) return null
        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = selectFirst("amp-img, img")?.let { coverOf(it) }
            description = selectFirst(".tags")?.text()?.trim()
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
            document.selectFirst(".comics-detail__title")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { title = it }
            document.selectFirst(".comics-detail__author")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { author = it }
            document.selectFirst(".comics-detail__desc")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { description = it }
            document.selectFirst(".comics-detail__cover amp-img, .comics-detail amp-img")?.let {
                thumbnail_url = coverOf(it)
            }
            genre = document.select(".tag-list a, .tag-list span")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        }

        val updatedChapters = document.select(".comics-chapters__item")
            .mapNotNull { element ->
                val link = if (element.tagName() == "a") element else element.selectFirst("a[href]") ?: return@mapNotNull null
                val href = link.absUrl("href")
                if (href.isBlank()) return@mapNotNull null
                val name = link.selectFirst(".comics-chapters__title")?.text()?.trim()
                    ?: link.text().trim()
                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    this.name = name.ifBlank { "Chapter" }
                }
            }
            .distinctBy { it.url }
            // The list is rendered as a palindrome (newest → oldest → newest), so sort by slot instead
            .sortedBy { chapterOrder(it.url) }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // endregion

    // region pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = baseUrl + chapter.url
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select(".comic-contain amp-img, .comic-contain img")
            .mapNotNull { it.pageImageUrl() }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, url, imageUrl = imageUrl) }
    }

    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers.newBuilder().set("Referer", "$baseUrl/").build())
        .build()

    /** `page_direct?section_slot=0&chapter_slot=12` -> 12, keeps chapters in reading order. */
    private fun chapterOrder(url: String): Long {
        val section = SECTION_SLOT_REGEX.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val chapter = CHAPTER_SLOT_REGEX.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return section * 100_000L + chapter
    }

    private fun Element.pageImageUrl(): String? = listOf("data-src", "data-original", "src")
        .map { absUrl(it) }
        .firstOrNull { it.startsWith("http") && it.contains("/scomic/") }

    private fun coverOf(image: Element): String? = listOf("data-src", "src", "data-original")
        .map { image.absUrl(it) }
        .firstOrNull { it.startsWith("http") && !it.startsWith("data:") && !it.contains("loding") }

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
        private val MANGA_URL_REGEX = Regex("""https?://[^/]+/comic/[^/?#]+""")
        private val SECTION_SLOT_REGEX = Regex("""section_slot=(\d+)""")
        private val CHAPTER_SLOT_REGEX = Regex("""chapter_slot=(\d+)""")
    }
}
