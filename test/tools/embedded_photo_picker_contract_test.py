import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


class EmbeddedPhotoPickerContractTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (REPO_ROOT / relative_path).read_text(encoding="utf-8")

    def test_bridge_is_extension_gated_and_image_only(self):
        source = self.read("app/src/main/java/com/freevibe/service/PhotoPickerCustomization.kt")

        self.assertIn("EMBEDDED_PICKER_MIN_EXTENSION = 15", source)
        self.assertIn("Build.VERSION_CODES.UPSIDE_DOWN_CAKE", source)
        self.assertIn("SdkExtensions.getExtensionVersion", source)
        self.assertIn("android.widget.photopicker.EmbeddedPhotoPickerProviderFactory", source)
        self.assertIn("android.widget.photopicker.EmbeddedPhotoPickerClient", source)
        self.assertIn("setMaxSelectionLimit(1)", source)
        self.assertIn("setMimeTypes", source)
        self.assertIn('"image/*"', source)

    def test_portrait_grid_customization_uses_the_platform_api(self):
        source = self.read("app/src/main/java/com/freevibe/service/PhotoPickerCustomization.kt")

        self.assertIn("android.widget.photopicker.PhotoPickerUiCustomizationParams", source)
        self.assertIn("ASPECT_RATIO_PORTRAIT_9_16", source)
        self.assertIn("MediaStore.EXTRA_PICK_IMAGES_UI_CUSTOMIZATION_PARAMS", source)
        self.assertIn("CUSTOMIZATION_MIN_EXTENSION = 22", source)
        self.assertIn("Build.VERSION.SDK_INT < 37", source)

    def test_bridge_has_no_reflection_left(self):
        source = self.read("app/src/main/java/com/freevibe/service/PhotoPickerCustomization.kt")

        for term in ("Class.forName", "getMethod(", "getField(", "getDeclaredConstructor(", "Proxy.newProxyInstance"):
            self.assertNotIn(term, source)

    def test_wallpaper_and_collection_imports_have_classic_picker_fallback(self):
        wallpaper_screen = self.read("app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt")
        collections_screen = self.read("app/src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt")

        for source in (wallpaper_screen, collections_screen):
            self.assertIn("EmbeddedImagePickerSheet", source)
            self.assertIn("PhotoPickerCustomization.isEmbeddedImagePickerAvailable(context)", source)
            self.assertIn("AuraPickVisualMedia()", source)
            self.assertIn("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)", source)

        self.assertIn("wallpaperUploadLauncher.launch(wallpaperUploadPickerRequest)", wallpaper_screen)
        self.assertIn("qrImportLauncher.launch(qrImportPickerRequest)", collections_screen)

    def test_no_broad_storage_permission_was_introduced(self):
        manifest = self.read("app/src/main/AndroidManifest.xml")

        self.assertNotIn("READ_MEDIA_IMAGES", manifest)
        self.assertNotIn("READ_EXTERNAL_STORAGE", manifest)


if __name__ == "__main__":
    unittest.main()
