pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalCallAgent"

include(":core-model")
include(":core-privacy")
include(":telephony-api")
include(":telephony-pstn")
include(":telephony-sip")
include(":audio-core")
include(":asr-local")
include(":tts-local")
include(":llm-litert")
include(":agent-orchestrator")
include(":benchmark")
include(":test-fixtures")
include(":app")
