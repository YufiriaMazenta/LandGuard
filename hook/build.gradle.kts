repositories {
    //Vault
    maven("https://jitpack.io")
    //PlaceHolderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly(project(":core"))
    //语言条目类型（与控制台多语言提示共用）
    compileOnly("com.crypticlib:bukkit-i18n:${rootProject.findProperty("crypticlibVer")}")
    //Vault
    compileOnly("com.github.MilkBowl:VaultAPI:${rootProject.findProperty("vaultApiVer")}") {
        exclude("org.bukkit", "bukkit")
    }
    compileOnly("me.clip:placeholderapi:${rootProject.findProperty("placeholderApiVer")}")
    compileOnly("io.papermc.paper:paper-api:${rootProject.findProperty("paperApiVer")}")
}
