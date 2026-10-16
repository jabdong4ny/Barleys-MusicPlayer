# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class com.barley.musicplayer.data.model.** {
  *;
}

# NewPipeExtractor
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn javax.annotation.**
