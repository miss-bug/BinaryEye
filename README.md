# Binary Eye

Yet another barcode scanner for Android. As if there weren't [enough][play].

This one is free, without any ads and open source.

Works in portrait and landscape orientation, can read inverted codes,
comes in Material Design and can also generate barcodes.

Binary Eye uses the [ZXing][zxing] ("Zebra Crossing") barcode scanning
library.

## Screenshots

![Screenshot Scanning](fastlane/metadata/android/en-US/images/phoneScreenshots/screencap-scanning.png)
![Screenshot Result](fastlane/metadata/android/en-US/images/phoneScreenshots/screencap-decoded.png)
![Screenshot Compose Barcode](fastlane/metadata/android/en-US/images/phoneScreenshots/screencap-compose-barcode.png)

## Download

<a href="https://f-droid.org/en/packages/de.markusfisch.android.binaryeye/"><img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80"/></a> <a href="https://play.google.com/store/apps/details?id=de.markusfisch.android.binaryeye"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80"/></a>

## Supported Barcode Formats

### Read

ZXing can read the following barcode formats:
* [AZTEC][aztec]
* [CODABAR][codabar]
* [CODE 39][code_39]
* [CODE 93][code_93]
* [CODE 128][code_128]
* [DATA MATRIX][data_matrix]
* [EAN 8][ean_8]
* [EAN 13][ean_13]
* [ITF][itf]
* [MAXICODE][maxicode] (only when unrotated and unskewed, see [77][77],
	because of which Binary Eye *cannot* read this barcode)
* [PDF417][pdf417]
* [QR CODE][qr_code]
* [RSS 14][rss]
* [RSS EXPANDED][rss]
* [UPC A][upc_a]
* [UPC E][upc_e]
* [UPC EAN EXTENSION][upc_ean]

### Generate

ZXing can generate the following barcode formats:
* [AZTEC][aztec]
* [CODABAR][codabar]
* [CODE 39][code_39]
* [CODE 128][code_128]
* [DATA MATRIX][data_matrix]
* [EAN 8][ean_8]
* [EAN 13][ean_13]
* [ITF][itf]
* [PDF 417][pdf417]
* [QR CODE][qr_code]
* [UPC A][upc_a]

## RenderScript

This app uses [RenderScript][rs] to resize and rotate the camera image.
Unfortunately, RenderScript has some nasty gotchas.

### RenderScript.forceCompat()

It's necessary to call `RenderScript.forceCompat()` on some devices/roms.

`RenderScript.forceCompat()` needs to be run before any other RenderScript
function and unfortunately there is no way to know if invoking `forceCompat()`
is necessary or not.

If `RenderScript.forceCompat()` is necessary, a `RSRuntimeException` will
be thrown and the only option is to restart the app, this time with calling
`forceCompat()` first.

Calling `RenderScript.forceCompat()` means the processing is done in
software so you probably don't want to enable it by default.

### macOS Catalina

At the time of writing, calling `RenderScript.forceCompat()` in a build made
on macOS Catalina with build tools 29.0.3 will crash the app. This is a bug
in the build tools for Catalina.

So *do not* build release builts on macOS Catalina with build tools 29.0.3.

Building on Linux with 29.0.3 works fine.

### App Bundle

At the time of writing, `./gradlew bundleRelease` will produce a broken
App Bundle without the necessary RenderScript libraries. This is another
bug in the build tools.

[play]: https://play.google.com/store/search?q=barcode%20scanner&c=apps
[zxing]: https://github.com/zxing/zxing
[kotlin]: http://kotlinlang.org/
[aztec]: https://en.wikipedia.org/wiki/Aztec_Code
[codabar]: https://en.wikipedia.org/wiki/Codabar
[code_39]: https://en.wikipedia.org/wiki/Code_39
[code_93]: https://en.wikipedia.org/wiki/Code_93
[code_128]: https://en.wikipedia.org/wiki/Code_128
[data_matrix]: https://en.wikipedia.org/wiki/Data_Matrix
[ean_8]: https://en.wikipedia.org/wiki/EAN-8
[ean_13]: https://en.wikipedia.org/wiki/International_Article_Number
[itf]: https://en.wikipedia.org/wiki/Interleaved_2_of_5
[maxicode]: https://en.wikipedia.org/wiki/MaxiCode
[pdf417]: https://en.wikipedia.org/wiki/PDF417
[qr_code]: https://en.wikipedia.org/wiki/QR_code
[rss]: https://en.wikipedia.org/wiki/GS1_DataBar
[upc_a]: https://en.wikipedia.org/wiki/Universal_Product_Code
[upc_e]: https://en.wikipedia.org/wiki/Universal_Product_Code#UPC-E
[upc_ean]: https://en.wikipedia.org/wiki/Universal_Product_Code#EAN-13
[77]: https://github.com/markusfisch/BinaryEye/issues/77
[rs]: https://developer.android.com/guide/topics/renderscript/compute
