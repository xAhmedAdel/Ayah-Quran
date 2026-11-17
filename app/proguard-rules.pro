# Ayah R8 rules.
# Most AndroidX/Compose/Hilt rules ship inside the libraries themselves.

# Room entities/DAOs are resolved via generated code; keep SQL-pojo mapping intact.
-keep class com.ayah.quran.core.database.entity.** { *; }

# Keep schema export artifacts out of the APK is not needed; nothing to strip here.

# Kotlin metadata is required by Room/Hilt reflection-free flows; default keep rules apply.
