val name: String by settings
rootProject.name = name

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("multiplatform") version kotlinVersion
    }
}
