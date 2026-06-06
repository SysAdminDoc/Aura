# FFmpeg Source Correspondence Review

Date: 2026-06-06

This file records the current evidence for the FFmpeg payload distributed through
`io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`. It is a release review
checklist, not a legal conclusion.

## Resolved Artifact

| Field | Value |
| --- | --- |
| Maven coordinate | `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` |
| Resolved AAR | `ffmpeg-0.18.1.aar` |
| AAR SHA-256 | `0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49` |
| AAR bytes | `139371444` |
| youtubedl-android tag | `0.18.1` |
| youtubedl-android commit | `d725d5c9a18c3a99a13ee0308bf78275dc310760` |
| FFmpeg version embedded in payload | `7.1.1` |
| FFmpeg source tag candidate | `n7.1.1` / `db69d06eeeab4f46da15030a80d539efb4503ca8` |
| FFmpeg source tarball candidate | `https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz` |
| FFmpeg source tarball SHA-256 | `733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1` |

Primary references:

- [FFmpeg license and legal considerations](https://ffmpeg.org/legal.html)
- [FFmpeg release downloads and verification guidance](https://ffmpeg.org/download.html)
- [FFmpeg 7.1.1 source tarball](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz)
- [FFmpeg 7.1.1 PGP signature](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz.asc)
- [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android/tree/0.18.1)
- [youtubedl-android FFmpeg build note](https://raw.githubusercontent.com/yausername/youtubedl-android/master/BUILD_FFMPEG.md)
- [Termux packages FFmpeg recipe](https://github.com/termux/termux-packages/tree/master/packages/ffmpeg)

## Current Finding

The resolved AAR embeds Termux-style `usr/lib` payload zips for `arm64-v8a`,
`armeabi-v7a`, `x86`, and `x86_64`. The nested payloads include shared FFmpeg
libraries and external codec libraries, but they do not include `usr/bin`,
`usr/share`, Debian control metadata, package build logs, Termux package source
commit metadata, or source files.

Embedded strings in the nested FFmpeg shared libraries do expose the FFmpeg
version and configure lines. Every ABI reports `FFmpeg version 7.1.1`. Every
configure line includes `--enable-gpl` and `--enable-version3`; none of the
extracted configure lines include `--enable-nonfree`. The resolved payload
therefore must be treated as GPL-3.0-or-later FFmpeg evidence for release
review unless a future payload proves a different build mode.

FFmpeg's own legal checklist requires matching source code and build/configure
evidence for distributed binaries. The evidence below supplies the binary-side
version/configure facts, but it does not yet prove the exact Termux package
commit, Termux patches, dependency source set, or build logs used to create the
resolved Maven AAR.

## Embedded ABI Evidence

| ABI | Nested payload | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `arm64-v8a` | `jni/arm64-v8a/libffmpeg.zip.so` | 35624931 | `4664a895283f9f31786be39b0c484c1f96631f02c7c7f9c70a4a4a2da324a092` |
| `armeabi-v7a` | `jni/armeabi-v7a/libffmpeg.zip.so` | 30476956 | `5b4a436358e28fc0f0788f5f917dcce9d35c0107b7454ca78dda3191e6dc120c` |
| `x86` | `jni/x86/libffmpeg.zip.so` | 34544829 | `45652e124a27c823fdd76c31207caaf5879eab1e3c69677dd2e89df4e6bc6b50` |
| `x86_64` | `jni/x86_64/libffmpeg.zip.so` | 38595503 | `79a442979bfc0363fcdd7e47f75daf022891e84fc01f3fdd866af785d22321ef` |

### `arm64-v8a`

```text
--arch=aarch64 --as=aarch64-linux-android-clang --cc=aarch64-linux-android-clang --cxx=aarch64-linux-android-clang++ --nm=llvm-nm --ar=llvm-ar --ranlib=llvm-ranlib --pkg-config=/home/builder/.termux-build/_cache/android-r28c-api-24-v1/bin/pkg-config --strip=llvm-strip --cross-prefix=aarch64-linux-android- --disable-indevs --disable-outdevs --enable-indev=lavfi --disable-static --disable-symver --enable-cross-compile --enable-gnutls --enable-gpl --enable-version3 --enable-jni --enable-lcms2 --enable-libaom --enable-libass --enable-libbluray --enable-libdav1d --enable-libfontconfig --enable-libfreetype --enable-libfribidi --enable-libgme --enable-libharfbuzz --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenmpt --enable-libopus --enable-librav1e --enable-librubberband --enable-libsoxr --enable-libsrt --enable-libssh --enable-libsvtav1 --enable-libtheora --enable-libv4l2 --enable-libvidstab --enable-libvmaf --enable-libvo-amrwbenc --enable-libvorbis --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libxvid --enable-libzimg --enable-libzmq --enable-mediacodec --enable-opencl --enable-shared --prefix=/data/data/com.termux/files/usr --target-os=android --extra-libs=-landroid-glob --disable-vulkan --enable-neon --disable-libfdk-aac
```

### `armeabi-v7a`

```text
--arch=armeabi-v7a --as=arm-linux-androideabi-clang --cc=arm-linux-androideabi-clang --cxx=arm-linux-androideabi-clang++ --nm=llvm-nm --ar=llvm-ar --ranlib=llvm-ranlib --pkg-config=/home/builder/.termux-build/_cache/android-r28c-api-24-v1/bin/pkg-config --strip=llvm-strip --cross-prefix=arm-linux-androideabi- --disable-indevs --disable-outdevs --enable-indev=lavfi --disable-static --disable-symver --enable-cross-compile --enable-gnutls --enable-gpl --enable-version3 --enable-jni --enable-lcms2 --enable-libaom --enable-libass --enable-libbluray --enable-libdav1d --enable-libfontconfig --enable-libfreetype --enable-libfribidi --enable-libgme --enable-libharfbuzz --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenmpt --enable-libopus --enable-librav1e --enable-librubberband --enable-libsoxr --enable-libsrt --enable-libssh --enable-libsvtav1 --enable-libtheora --enable-libv4l2 --enable-libvidstab --enable-libvmaf --enable-libvo-amrwbenc --enable-libvorbis --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libxvid --enable-libzimg --enable-libzmq --enable-mediacodec --enable-opencl --enable-shared --prefix=/data/data/com.termux/files/usr --target-os=android --extra-libs=-landroid-glob --disable-vulkan --enable-neon --disable-libfdk-aac
```

### `x86`

```text
--arch=x86 --as=i686-linux-android-clang --cc=i686-linux-android-clang --cxx=i686-linux-android-clang++ --nm=llvm-nm --ar=llvm-ar --ranlib=llvm-ranlib --pkg-config=/home/builder/.termux-build/_cache/android-r28c-api-24-v1/bin/pkg-config --strip=llvm-strip --cross-prefix=i686-linux-android- --disable-indevs --disable-outdevs --enable-indev=lavfi --disable-static --disable-symver --enable-cross-compile --enable-gnutls --enable-gpl --enable-version3 --enable-jni --enable-lcms2 --enable-libaom --enable-libass --enable-libbluray --enable-libdav1d --enable-libfontconfig --enable-libfreetype --enable-libfribidi --enable-libgme --enable-libharfbuzz --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenmpt --enable-libopus --enable-librav1e --enable-librubberband --enable-libsoxr --enable-libsrt --enable-libssh --enable-libsvtav1 --enable-libtheora --enable-libv4l2 --enable-libvidstab --enable-libvmaf --enable-libvo-amrwbenc --enable-libvorbis --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libxvid --enable-libzimg --enable-libzmq --enable-mediacodec --enable-opencl --enable-shared --prefix=/data/data/com.termux/files/usr --target-os=android --extra-libs=-landroid-glob --disable-vulkan --disable-asm --disable-libfdk-aac
```

### `x86_64`

```text
--arch=x86_64 --as=x86_64-linux-android-clang --cc=x86_64-linux-android-clang --cxx=x86_64-linux-android-clang++ --nm=llvm-nm --ar=llvm-ar --ranlib=llvm-ranlib --pkg-config=/home/builder/.termux-build/_cache/android-r28c-api-24-v1/bin/pkg-config --strip=llvm-strip --cross-prefix=x86_64-linux-android- --disable-indevs --disable-outdevs --enable-indev=lavfi --disable-static --disable-symver --enable-cross-compile --enable-gnutls --enable-gpl --enable-version3 --enable-jni --enable-lcms2 --enable-libaom --enable-libass --enable-libbluray --enable-libdav1d --enable-libfontconfig --enable-libfreetype --enable-libfribidi --enable-libgme --enable-libharfbuzz --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenmpt --enable-libopus --enable-librav1e --enable-librubberband --enable-libsoxr --enable-libsrt --enable-libssh --enable-libsvtav1 --enable-libtheora --enable-libv4l2 --enable-libvidstab --enable-libvmaf --enable-libvo-amrwbenc --enable-libvorbis --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libxvid --enable-libzimg --enable-libzmq --enable-mediacodec --enable-opencl --enable-shared --prefix=/data/data/com.termux/files/usr --target-os=android --extra-libs=-landroid-glob --disable-vulkan --disable-libfdk-aac
```

## Source Correspondence Checklist

Before publishing a release with a changed FFmpeg payload, the release owner
must attach or retain these records:

- [ ] The resolved `ffmpeg-0.18.1.aar` hash and each nested
  `libffmpeg.zip.so` hash from this file or a refreshed successor.
- [ ] The exact youtubedl-android source tag/commit used for the Maven
  coordinate.
- [ ] The exact Termux packages commit used for `packages/ffmpeg/build.sh` and
  all Termux patch files under `packages/ffmpeg/`.
- [ ] The FFmpeg source archive or git tag used by that Termux package recipe.
- [ ] The source archive hash and signature verification result for FFmpeg.
- [ ] The complete build log or captured configure line for every ABI.
- [ ] Source or source-offer records for GPL/LGPL external libraries enabled in
  the configure line, including x264, x265, xvidcore, vpx, rav1e, dav1d,
  libass, libtheora, libvorbis, libopus, lame, and the other enabled libraries.
- [ ] A release asset or same-server source-offer location that lets recipients
  obtain the corresponding FFmpeg source and build notes for the shipped
  binaries.

## Unresolved Owner Action

This cycle identifies the embedded version/configure evidence and the likely
upstream FFmpeg source candidate, but it does not prove the complete Termux
source correspondence chain. A public release that changes the FFmpeg payload
still needs the exact Termux package commit, patches, dependency source set, and
build log before the review can be marked complete.
