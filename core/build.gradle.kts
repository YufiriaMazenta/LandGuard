repositories {
    mavenCentral()
    //Vault
    maven("https://jitpack.io")
    //PlaceHolderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    //bStats
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    compileOnly("me.clip:placeholderapi:${rootProject.findProperty("placeholderApiVer")}")
    compileOnly("net.kyori:adventure-api:${rootProject.findProperty("adventureApiVer")}")
    compileOnly("io.papermc.paper:paper-api:${rootProject.findProperty("paperApiVer")}")
    compileOnly("com.crypticlib:bukkit:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:bukkit-ui:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:bukkit-i18n:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:bukkit-util:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:common-compat:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:common-database:${rootProject.findProperty("crypticlibVer")}")
    implementation("com.crypticlib:common-util:${rootProject.findProperty("crypticlibVer")}")
    implementation("org.bstats:bstats-bukkit:${rootProject.findProperty("bStatsVer")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.crypticlib:bukkit:${rootProject.findProperty("crypticlibVer")}")
    testImplementation("net.kyori:adventure-api:${rootProject.findProperty("adventureApiVer")}")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.44.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")
    //仅测试期提供, 生产环境由服务端提供 MySQL 驱动
    testRuntimeOnly("com.mysql:mysql-connector-j:${rootProject.findProperty("mysqlDriverVer")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
