import org.gradle.api.Project
import org.gradle.api.provider.Provider

val Project.ci: Provider<Boolean> get() = providers
    .environmentVariable("CI")
    .map { it.toBoolean() }
    .orElse(false)
