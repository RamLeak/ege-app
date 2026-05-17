# Stage 1: minify=off, поэтому эти правила в дебаге не применяются.
# Оставлены для будущей сборки release.
-keepattributes *Annotation*
-keep class androidx.room.** { *; }
