plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("com.google.code.gson:gson:2.14.0")
    api("com.google.guava:guava:33.7.1-jre")
    api("it.unimi.dsi:fastutil:8.5.19")
    // 注意：Caffeine 必须留在 2.x。
    // 3.x 是 Java 11 基线，内部用 java.lang.System.Logger（Java 9+，Android 10 及以下的
    // 运行时里没有 java.lang.System.getLogger），在手机上会在转换过程中抛
    // NoSuchMethodError: No static method getLogger… 紧接着 NoClassDefFoundError: Caffeine。
    // 本项目只用 Resolver.cached() 里的 Caffeine.newBuilder().build(loader)（无界缓存），
    // 2.9.3 是 Java 8 基线、API 完全兼容，也是 Android 上通行的选择。
    api("com.github.ben-manes.caffeine:caffeine:2.9.3")
    api("net.jpountz.lz4:lz4:1.3.0")
    // 注意：leveldb 1.1.0（hivemc 分支）是 Java 9+ 编译的，内部对 MappedByteBuffer 调用了 Java 9 才有的
    // 协变重写（duplicate()/force() 等返回 MappedByteBuffer 的版本）。Android 的 libcore 把这些裁掉了
    // （API 34 才有），于是 API 33 及以下转换时会抛
    //   NoSuchMethodError: No virtual method duplicate()Ljava/nio/MappedByteBuffer;
    // 这里改用同一份 1.1.0 的“向下兼容补丁版”（把这类调用改写为 ByteBuffer 同名方法 + checkcast，
    // 补丁脚本 tools/patch_leveldb.ps1，补丁后残留检查 0/0），新旧 Android 都能跑，行为完全一致。
    // 注意：该补丁版 jar 里 **已删除** org/iq80/leveldb/util/ZLib.class，改由本模块源码
    // src/main/java/org/iq80/leveldb/util/ZLib.java 提供（只用 API 1 就有的 byte[] 形式）。
    // 原因：jar 内原实现调用 Inflater.setInput(ByteBuffer)/inflate(ByteBuffer)，Android 的 libcore
    // 直到 API 34 才有，API 33 及以下读取基岩版存档（LevelDB 的 zlib 块）会抛 NoSuchMethodError。
    // 若将来重新生成此 jar，务必再次删除 ZLib.class，否则会与源码里的同名类冲突/复现崩溃。
    api(files("libs/leveldb-1.1.0-android-compat.jar"))
    api("com.hivemc.leveldb:leveldb-api:1.1.0")
    api("info.picocli:picocli:4.7.6")
    compileOnly("org.jetbrains:annotations:24.0.0")
}