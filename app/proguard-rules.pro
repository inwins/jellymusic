# Add project specific ProGuard rules here.
# Keep default rules as-is.
# Keep the media session service referenced from the manifest.
-keep class io.github.inwins.jellymusic.player.PlaybackService { *; }
-dontwarn org.conscrypt.**
