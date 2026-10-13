package com.freevibe.ui.screens.sounds

import android.content.Context
import android.net.Uri
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.ContactRingtoneService
import com.freevibe.service.SoundUrlResolver
import com.freevibe.service.SoundApplier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ensureSelectedSound prefers fallback identity over duplicate favorite id`() = runTest(dispatcher) {
        val favoritesRepo = mockk<FavoritesRepository>()
        val bundledContent = mockk<BundledContentProvider>()
        val fallbackSound = Sound(
            id = "dup_sound",
            source = ContentSource.BUNDLED,
            name = "Aura Bell",
            previewUrl = "https://example.com/bundled.mp3",
            downloadUrl = "https://example.com/bundled.mp3",
        )
        coEvery { favoritesRepo.getLatestById("dup_sound") } returns Sound(
            id = "dup_sound",
            source = ContentSource.YOUTUBE,
            name = "Stale YouTube",
            previewUrl = "https://example.com/youtube.mp3",
            downloadUrl = "https://example.com/youtube.mp3",
        ).toFavoriteEntity()
        coEvery { favoritesRepo.getLatestByIdAndType("dup_sound", "SOUND") } returns Sound(
            id = "dup_sound",
            source = ContentSource.YOUTUBE,
            name = "Stale YouTube",
            previewUrl = "https://example.com/youtube.mp3",
            downloadUrl = "https://example.com/youtube.mp3",
        ).toFavoriteEntity()
        every { bundledContent.getRingtones() } returns listOf(fallbackSound)
        every { bundledContent.getNotifications() } returns emptyList()
        every { bundledContent.getAlarms() } returns emptyList()

        val viewModel = ContactPickerViewModel(
            context = mockk<Context>(relaxed = true),
            contactService = mockk<ContactRingtoneService>(relaxed = true),
            soundApplier = mockk<SoundApplier>(relaxed = true),
            favoritesRepo = favoritesRepo,
            bundledContent = bundledContent,
            soundUrlResolver = mockk<SoundUrlResolver>(relaxed = true),
        )

        val resolved = viewModel.ensureSelectedSound("dup_sound", fallbackSound)

        assertEquals(true, resolved)
        assertEquals(ContentSource.BUNDLED, viewModel.state.value.selectedSound?.source)
        assertEquals("https://example.com/bundled.mp3", viewModel.state.value.selectedSound?.previewUrl)
    }

    @Test
    fun `ensureSelectedSound ignores newer wrong-type favorite collisions`() = runTest(dispatcher) {
        val favoritesRepo = mockk<FavoritesRepository>()
        val bundledContent = mockk<BundledContentProvider>()

        coEvery { favoritesRepo.getLatestByIdAndType("shared_raw", "SOUND") } returns Sound(
            id = "shared_raw",
            source = ContentSource.YOUTUBE,
            name = "Recovered sound",
            previewUrl = "https://example.com/recovered.mp3",
            downloadUrl = "https://example.com/recovered.mp3",
        ).toFavoriteEntity()
        every { bundledContent.getRingtones() } returns emptyList()
        every { bundledContent.getNotifications() } returns emptyList()
        every { bundledContent.getAlarms() } returns emptyList()

        val viewModel = ContactPickerViewModel(
            context = mockk<Context>(relaxed = true),
            contactService = mockk<ContactRingtoneService>(relaxed = true),
            soundApplier = mockk<SoundApplier>(relaxed = true),
            favoritesRepo = favoritesRepo,
            bundledContent = bundledContent,
            soundUrlResolver = mockk<SoundUrlResolver>(relaxed = true),
        )

        val resolved = viewModel.ensureSelectedSound("shared_raw", null)

        assertEquals(true, resolved)
        assertEquals(ContentSource.YOUTUBE, viewModel.state.value.selectedSound?.source)
        assertEquals("Recovered sound", viewModel.state.value.selectedSound?.name)
    }

    @Test
    fun `vip preset assigns the contact tone and silences the default ringtone`() = runTest(dispatcher) {
        val sound = Sound(
            id = "local_tone",
            source = ContentSource.LOCAL,
            name = "VIP tone",
            previewUrl = "https://example.com/tone.mp3",
            downloadUrl = "https://example.com/tone.mp3",
        )
        val contact = com.freevibe.service.ContactInfo(
            id = 42L,
            lookupKey = "vip",
            name = "VIP",
        )
        val contactService = mockk<ContactRingtoneService>()
        val soundApplier = mockk<SoundApplier>()
        val favoritesRepo = mockk<FavoritesRepository>()
        val bundledContent = mockk<BundledContentProvider>()
        val soundUrlResolver = mockk<SoundUrlResolver>()
        coEvery { favoritesRepo.getLatestByIdAndType("local_tone", "SOUND") } returns null
        every { bundledContent.getRingtones() } returns emptyList()
        every { bundledContent.getNotifications() } returns emptyList()
        every { bundledContent.getAlarms() } returns emptyList()
        coEvery { contactService.getContact(any()) } returns contact
        every { contactService.getDndGuidance(contact) } returns com.freevibe.service.ContactDndGuidance.NONE
        coEvery { soundUrlResolver.resolve(sound) } returns sound.downloadUrl
        coEvery { soundApplier.downloadOnly(sound.downloadUrl, sound.name, ContentType.RINGTONE) } returns
            Result.success(mockk<Uri>())
        coEvery { contactService.setContactRingtone(42L, any(), any()) } returns Result.success(Unit)
        coEvery { soundApplier.setDefaultRingtoneSilent() } returns Result.success(Unit)

        val viewModel = ContactPickerViewModel(
            context = mockk<Context>(relaxed = true),
            contactService = contactService,
            soundApplier = soundApplier,
            favoritesRepo = favoritesRepo,
            bundledContent = bundledContent,
            soundUrlResolver = soundUrlResolver,
        )

        assertEquals(true, viewModel.ensureSelectedSound(sound.id, sound))
        viewModel.loadSelectedContact(mockk<Uri>())
        advanceUntilIdle()
        viewModel.assignVipOnlyRinging(42L, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { contactService.setContactRingtone(42L, any(), any()) }
        coVerify(exactly = 1) { soundApplier.setDefaultRingtoneSilent() }
        assertNotNull(viewModel.state.value.success)
    }
}
