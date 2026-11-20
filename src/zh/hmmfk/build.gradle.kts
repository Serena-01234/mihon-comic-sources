import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lianliankan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://www.hmmfk.com"
    }

    deeplink {
        host("www.hmmfk.com")
    }
}
