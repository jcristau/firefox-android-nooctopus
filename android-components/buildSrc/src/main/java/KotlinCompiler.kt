/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/**
 * A collection of functions and properties to interact with the Kotlin compiler.
 */
object KotlinCompiler {

    // Ideally, this would be a function to enable warningsAsErrors. However, it's hard to make the
    // KotlinCompile task dependencies available to buildSrc so we settle for defining this list here instead.
    // Maybe this is easier in Gradle 5+.
    @JvmStatic
    val projectsWithWarningsAsErrorsDisabled = setOf(
        "browser-domains",
        "browser-engine-gecko",
        "browser-engine-servo",
        "browser-search",
        "browser-storage-sync",
        "feature-accounts",
        "feature-awesomebar",
        "feature-contextmenu",
        "feature-customtabs",
        "feature-downloads",
        "feature-intent",
        "feature-prompts",
        "feature-search",
        "feature-session",
        "feature-sitepermissions",
        "feature-tabs",
        "lib-fetch-okhttp",
        "samples-toolbar",
        "service-experiments",
        "service-fretboard",
        "service-glean",
        "support-test",
        "tooling-lint",
        "ui-tabcounter"
    )
}
