import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baozi Manhua CN"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://cn.bzmanga.com"
    }

    deeplink {
        host("cn.bzmanga.com")
    }
}
