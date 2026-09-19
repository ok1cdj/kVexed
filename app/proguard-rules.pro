# kVexed keeps default AGP/R8 rules. Compose + MMD are handled by their own
# consumer ProGuard files. The :core module is plain Kotlin with no reflection,
# so no keep rules are needed — levels load from resources by fixed path.
