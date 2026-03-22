plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "com.langosta.langosta"
version = "1.0.0"
application {
    mainClass.set("com.langosta.langosta.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(project(":composeApp"))
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.testJunit)
}
