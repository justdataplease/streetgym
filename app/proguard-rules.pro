# R8 runs on release builds since 2.11.0. The app itself uses no reflection-based serialization: the curriculum,
# parks and animations are parsed with org.json, and Firestore reads and writes plain maps. The libraries (Firebase,
# Play Billing, AdMob, UMP, Credential Manager, MapLibre) ship their own consumer rules.

# Readable stack traces in Play's crash reports; the mapping file in the bundle de-obfuscates the names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
