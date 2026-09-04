# Proguard rules for Samsung Health Exporter
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.health.connect.client.records.* <fields>;
}
