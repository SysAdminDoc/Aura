# Native Compliance Inventory

Generated from local Gradle cache artifacts. This is a factual payload inventory for release review; it does not replace legal review.

- Gradle cache: `~/.gradle/caches/modules-2/files-2.1`

## Coordinate Summary

| Coordinate | Artifact | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `io.github.junkfood02.youtubedl-android:common:0.18.1` | `common-0.18.1.aar` | 5186 | `e0c0530bdbdf203e1598e900085d3582e27360731872872f3a67f23a9b24c0b5` |
| `io.github.junkfood02.youtubedl-android:common:0.18.1` | `common-0.18.1.pom` | 2365 | `de96f5a108c493e464d0e3fe7a73ae21a10d71f98f49024e043650bf22381b78` |
| `io.github.junkfood02.youtubedl-android:common:0.18.1` | `common-0.18.1.module` | 3724 | `6e1cd6c75a8f416cbcf1ee36443fc4a7df542a09a56ab51d2220d0a639aa28fe` |
| `io.github.junkfood02.youtubedl-android:library:0.18.1` | `library-0.18.1.aar` | 59213110 | `579b5fb480892b1abc2b218c2089699d52759cc8d7ba256bf876453f0365faef` |
| `io.github.junkfood02.youtubedl-android:library:0.18.1` | `library-0.18.1.pom` | 2773 | `98f5eb540b7ec22ddd73373a7e87149d0c490e0790cfeeb0343285a643d64690` |
| `io.github.junkfood02.youtubedl-android:library:0.18.1` | `library-0.18.1.module` | 4111 | `3e8ad9aa05cd197bc7ad038c235055b11b2dad74cbb1b4ecc99fbc82a0ff0c9f` |
| `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` | `NewPipeExtractor-v0.26.5.pom` | 2438 | `6ffc041f836531a2706cb9652014f4e2c0d7c0d34a2913e95601675cfa46062b` |
| `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` | `NewPipeExtractor-v0.26.5.jar` | 802094 | `923bf0a60938d570ad176ed7b08fa9fec7d0772d7a386ca89a2dd3b9212979c7` |
| `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` | `NewPipeExtractor-v0.26.5.module` | 4164 | `43456deb5537cedfd53c6ff42e831faad1651ac8e5f9b059ec29fa5c46ffefdf` |

## Upstream Source And License References

| Coordinate | License | Source | License text | Review note |
| --- | --- | --- | --- | --- |
| `io.github.junkfood02.youtubedl-android:common:0.18.1` | GPL-3.0 | https://github.com/yausername/youtubedl-android/tree/master/common | https://raw.githubusercontent.com/yausername/youtubedl-android/master/LICENSE | Wrapper component; no native payload entries matched this inventory. |
| `io.github.junkfood02.youtubedl-android:library:0.18.1` | GPL-3.0 | https://github.com/yausername/youtubedl-android/tree/master/library | https://raw.githubusercontent.com/yausername/youtubedl-android/master/LICENSE | Bundles yt-dlp plus Python/native runtime payloads; exact nested payload source evidence is required for release review. |
| `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` | GPL-3.0-or-later | https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5 | https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.26.5/LICENSE | Single NewPipeExtractor artifact used by Aura's YouTube metadata path. |

## Payload Source And Build References

| Payload | License / status | Evidence | Review note |
| --- | --- | --- | --- |
| yt-dlp payload | Unlicense for source; zipimport release file also includes ISC/MIT components | https://github.com/yt-dlp/yt-dlp/tree/2025.11.12 | The report extracts the bundled version, origin, and git head from res/raw/ytdlp. |
| Python runtime payload | PSF-2.0 plus packaged dependency licenses | https://docs.python.org/3.12/license.html | The report extracts python3.12 directories from libpython.zip.so; exact Termux package source set still needs release-owner review. |
| QuickJS runtime payload | MIT | https://bellard.org/quickjs/ | libqjs.so is present in youtubedl-android library AARs; exact packaged QuickJS revision is not encoded in the AAR. |
| youtubedl-android Python build notes | Build evidence, not a separate license | https://raw.githubusercontent.com/yausername/youtubedl-android/master/BUILD_PYTHON.md | Upstream describes a Termux package build path; the resolved AAR contains python3.12 despite older README/build-note examples. |

## APK Payload Entries

- APK: `app/build/outputs/apk/full/release/app-full-universal-release.apk`
- Bytes: `70651429`
- SHA-256: `706966afcf21d9fb885cb1134355b89a79571b75b94b11a2e871c4a84286916e`

| Entry | Bytes | Nested entries | Facts |
| --- | ---: | ---: | --- |
| `assets/dexopt/baseline.prof` | 18148 |  |  |
| `assets/dexopt/baseline.profm` | 1365 |  |  |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10096 |  |  |
| `lib/arm64-v8a/libdatastore_shared_counter.so` | 7784 |  |  |
| `lib/arm64-v8a/libpython.so` | 4384 |  |  |
| `lib/arm64-v8a/libpython.zip.so` | 14305904 | 1007 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `lib/arm64-v8a/libqjs.so` | 916104 |  |  |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 7252 |  |  |
| `lib/armeabi-v7a/libdatastore_shared_counter.so` | 5916 |  |  |
| `lib/armeabi-v7a/libpython.so` | 3016 |  |  |
| `lib/armeabi-v7a/libpython.zip.so` | 12822487 | 1007 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `lib/armeabi-v7a/libqjs.so` | 634624 |  |  |
| `lib/x86/libandroidx.graphics.path.so` | 9284 |  |  |
| `lib/x86/libdatastore_shared_counter.so` | 6124 |  |  |
| `lib/x86/libpython.so` | 3240 |  |  |
| `lib/x86/libpython.zip.so` | 13979299 | 1009 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `lib/x86/libqjs.so` | 949596 |  |  |
| `lib/x86_64/libandroidx.graphics.path.so` | 10760 |  |  |
| `lib/x86_64/libdatastore_shared_counter.so` | 7336 |  |  |
| `lib/x86_64/libpython.so` | 4272 |  |  |
| `lib/x86_64/libpython.zip.so` | 14302766 | 1009 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `lib/x86_64/libqjs.so` | 981912 |  |  |
| `assets/PublicSuffixDatabase.list` | 132737 |  |  |
| `assets/aura_originals_manifest.json` | 384 |  |  |
| `META-INF/FastDoubleParser-LICENSE` | 3228 |  |  |
| `META-INF/FastDoubleParser-ThirdParty-LICENSE` | 4578 |  |  |
| `META-INF/NOTICE.md` | 1074 |  |  |
| `META-INF/Schubfach-LICENSE` | 1064 |  |  |
| `META-INF/androidx/annotation/annotation/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/collection/collection-ktx/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/collection/collection/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/datastore/datastore-core-okio/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt` | 1434 |  |  |
| `META-INF/androidx/datastore/datastore-preferences-proto/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/glance/glance-appwidget-external-protobuf/LICENSE.txt` | 1434 |  |  |
| `META-INF/androidx/glance/glance-appwidget-proto/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/hilt/hilt-common/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/lifecycle/lifecycle-common-java8/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/lifecycle/lifecycle-common/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/paging/paging-common-ktx/LICENSE.txt` | 10175 |  |  |
| `META-INF/androidx/room/room-common/LICENSE.txt` | 10175 |  |  |
| `META-INF/jsoup/LICENSE` | 1102 |  |  |

## Payload Entries

### `io.github.junkfood02.youtubedl-android:common:0.18.1` - `common-0.18.1.aar`

No native/payload/license entries matched the inventory filters.

### `io.github.junkfood02.youtubedl-android:library:0.18.1` - `library-0.18.1.aar`

| Entry | Bytes | Nested entries | Facts |
| --- | ---: | ---: | --- |
| `res/raw/` | 0 |  |  |
| `res/raw/ytdlp` | 3170726 | 1122 | yt-dlp version: 2025.11.12; yt-dlp git head: 335653be82d5ef999cfc2879d005397402eebec1; yt-dlp origin: yt-dlp/yt-dlp; nested sample: yt_dlp/version.py, yt_dlp/postprocessor/ffmpeg.py, yt_dlp_ejs/_version.py |
| `jni/` | 0 |  |  |
| `jni/arm64-v8a/` | 0 |  |  |
| `jni/arm64-v8a/libpython.so` | 4384 |  |  |
| `jni/arm64-v8a/libpython.zip.so` | 14305904 | 1007 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `jni/arm64-v8a/libqjs.so` | 916104 |  |  |
| `jni/armeabi-v7a/` | 0 |  |  |
| `jni/armeabi-v7a/libpython.so` | 3016 |  |  |
| `jni/armeabi-v7a/libpython.zip.so` | 12822487 | 1007 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `jni/armeabi-v7a/libqjs.so` | 634624 |  |  |
| `jni/x86/` | 0 |  |  |
| `jni/x86/libpython.so` | 3240 |  |  |
| `jni/x86/libpython.zip.so` | 13979299 | 1009 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `jni/x86/libqjs.so` | 949596 |  |  |
| `jni/x86_64/` | 0 |  |  |
| `jni/x86_64/libpython.so` | 4272 |  |  |
| `jni/x86_64/libpython.zip.so` | 14302766 | 1009 | python payload: python3.12; nested sample: usr/lib/, usr/lib/libform.so, usr/lib/libz.so, usr/lib/libffi.so, usr/lib/libtermcap.so, usr/lib/libcurses.so.6.5, usr/lib/libexpat.so.1.11.1, usr/lib/libgdbm_compat.so, ... |
| `jni/x86_64/libqjs.so` | 981912 |  |  |

### `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` - `NewPipeExtractor-v0.26.5.jar`

No native/payload/license entries matched the inventory filters.

## Release Review Notes

- Confirm exact upstream source and license text for every artifact listed above before public release expansion.
- youtubedl-android library payloads include yt-dlp and Python assets that need version/source disclosure.
- Generated Google OSS notices cover Maven coordinates but do not inspect these nested native payloads.
