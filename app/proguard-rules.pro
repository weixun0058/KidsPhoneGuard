# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities
-keep class com.kidsphoneguard.data.model.** { *; }

# Keep AccessibilityService
-keep class com.kidsphoneguard.service.GuardAccessibilityService { *; }
