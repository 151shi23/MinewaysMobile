pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 用 PREFER_PROJECT 而不是 FAIL_ON_PROJECT_REPOS：
    // 后者会在使用 ~/.gradle/init.gradle 之类初始化脚本追加仓库（例如国内镜像加速）时
    // 直接让构建失败（"repository ... was added by initialization script"）。
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MinewaysMobile"
include(":app")
include(":chunker-core")