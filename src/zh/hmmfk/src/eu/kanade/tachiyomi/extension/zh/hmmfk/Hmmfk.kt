package eu.kanade.tachiyomi.extension.zh.hmmfk

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
abstract class Hmmfk : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException("The site has no latest-updates feed")

    // region popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("list/index-$page.html")
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li")
            .mapNotNull { it.toListManga() }
            .distinctBy { it.url }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun Element.toListManga(): SManga? {
        val link = selectFirst(".manga-list-2-cover a[href], a[href^=/manhua/]") ?: return null
        val href = link.absUrl("href")
        if (!href.matches(MANGA_URL_REGEX)) return null
        val title = selectFirst(".manga-list-2-title")?.text()?.trim()
            ?.ifBlank { link.attr("title") }
            ?: link.attr("title")
        if (title.isBlank()) return null
        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = selectFirst("img")?.let { coverOf(it) }
        }
    }

    // endregion

    // region search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        if (page > 1) return MangasPage(emptyList(), false)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("search")
            .addQueryParameter("keyword", query)
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.book-list li")
            .mapNotNull { it.toSearchManga() }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    private fun Element.toSearchManga(): SManga? {
        val link = selectFirst("a[href]") ?: return null
        val href = link.absUrl("href")
        if (!href.matches(MANGA_URL_REGEX)) return null
        val title = selectFirst(".book-list-info-title")?.text()?.trim()
            ?: link.attr("title")
        if (title.isBlank()) return null
        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = selectFirst("img")?.let { coverOf(it) }
            description = selectFirst(".book-list-info-desc")?.text()?.trim()
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
            document.selectFirst(".detail-main-info-title")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { title = it }

            val authorLine = document.select(".detail-main-info-author")
                .map { it.text().trim() }
                .firstOrNull { it.startsWith("作者") }
            author = authorLine?.substringAfter("作者")?.trim()?.trimStart('：', ':')?.trim()
                ?.substringBefore("状态")?.trim()

            description = document.selectFirst(".detail-desc")?.text()?.trim() ?: description
            genre = document.select(".detail-main-info-class a")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
            document.selectFirst("img[data-original]")?.let { thumbnail_url = coverOf(it) }
            status = when {
                document.select(".detail-main-info-author").any { it.text().contains("已完结") } -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
        }

        val updatedChapters = document.select("a.chapteritem[href]")
            .mapNotNull { element ->
                val href = element.absUrl("href")
                if (!href.matches(CHAPTER_URL_REGEX)) return@mapNotNull null
                val name = element.attr("title").ifBlank { element.text().trim() }
                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    this.name = name.ifBlank { "Chapter" }
                }
            }
            .distinctBy { it.url }
            .reversed()

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // endregion

    // region pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = baseUrl + chapter.url
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select(".view-main-1 img, .view-main img")
            .mapNotNull { imageUrlOf(it) }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, url, imageUrl = imageUrl) }
    }

    // The image host rejects requests without a chapter-page Referer
    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers.newBuilder().set("Referer", page.url).build())
        .build()

    private fun imageUrlOf(image: Element): String? = listOf("data-original", "data-src", "src")
        .map { image.absUrl(it) }
        .firstOrNull { it.startsWith("http") && !it.startsWith("data:") }

    private fun coverOf(image: Element): String? = listOf("data-original", "data-src", "src")
        .map { image.absUrl(it) }
        .firstOrNull { it.startsWith("http") && !it.endsWith("loading.gif") && !it.endsWith("zanwufengmian.jpg") }

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
        private val MANGA_URL_REGEX = Regex("""https?://[^/]+/manhua/\d+""")
        private val CHAPTER_URL_REGEX = Regex("""https?://[^/]+/chapter/\d+""")
    }
}
