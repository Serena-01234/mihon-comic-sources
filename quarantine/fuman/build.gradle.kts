import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fuman"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://www.fumanmanhua.com"
    }

    deeplink {
        host("www.fumanmanhua.com")
    }
}
