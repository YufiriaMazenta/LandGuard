repositories {
    //Vault
    maven("https://jitpack.io")
    //PlaceHolderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly(project(":core"))
    //Vault
    compileOnly("com.github.MilkBowl:VaultAPI:${rootProject.findProperty("vaultApiVer")}") {
        exclude("org.bukkit", "bukkit")
    }
    compileOnly("me.clip:placeholderapi:${rootProject.findProperty("placeholderApiVer")}")
    compileOnly("io.papermc.paper:paper-api:${rootProject.findProperty("paperApiVer")}")
}
