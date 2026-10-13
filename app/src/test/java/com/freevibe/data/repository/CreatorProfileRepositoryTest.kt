package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorProfileRepositoryTest {

    @Test
    fun `aggregateCreatorStats totals uploads votes and follow state`() {
        val uploads = listOf(
            upload("s1", "creator_a", "Alex", "sound", votes = 3),
            upload("w1", "creator_a", "Alex", "wallpaper", votes = 5),
            upload("s2", "creator_b", "Blair", "sound", votes = -2),
        )

        val stats = aggregateCreatorStats(
            uploads = uploads,
            currentUserIds = setOf("creator_a"),
            followedCreatorIds = setOf("creator_a", "creator_b"),
        )

        val alex = stats.first { it.creatorId == "creator_a" }
        assertEquals(1, alex.soundUploads)
        assertEquals(1, alex.wallpaperUploads)
        assertEquals(8, alex.totalVotes)
        assertFalse(alex.isFollowed)

        val blair = stats.first { it.creatorId == "creator_b" }
        assertEquals(1, blair.soundUploads)
        assertEquals(0, blair.totalVotes)
        assertTrue(blair.isFollowed)
    }

    @Test
    fun `aggregateCreatorStats sorts by votes then upload count`() {
        val stats = aggregateCreatorStats(
            uploads = listOf(
                upload("a1", "a", "A", "sound", votes = 4),
                upload("b1", "b", "B", "wallpaper", votes = 4),
                upload("b2", "b", "B", "sound", votes = 0),
                upload("c1", "c", "C", "sound", votes = 9),
            ),
        )

        assertEquals(listOf("c", "b", "a"), stats.map { it.creatorId })
    }

    @Test
    fun `filterCreatorUploadsForBlockedUsers removes blocked creators`() {
        val uploads = listOf(
            upload("s1", "creator_a", "Alex", "sound", votes = 3),
            upload("s2", "creator.b", "Blair", "sound", votes = 5),
        )

        val visible = filterCreatorUploadsForBlockedUsers(uploads, setOf("creator/b"))

        assertEquals(listOf("creator_a"), visible.map { it.creatorId })
    }

    @Test
    fun `followed sections show only followed creators and their uploads`() {
        val uploads = listOf(
            upload("s1", "alice", "Alice", "sound", 1),
            upload("w1", "bob", "Bob", "wallpaper", 2),
            upload("w2", "carol", "Carol", "wallpaper", 9),
        )
        val sections = followedCreatorSections(
            followed = mapOf("alice" to "Alice", "bob" to "Bob"),
            rankedCreators = aggregateCreatorStats(uploads),
            uploads = uploads,
        )

        assertEquals(listOf("alice", "bob"), sections.creators.map { it.creatorId })
        assertEquals(setOf("s1", "w1"), sections.uploads.map { it.id }.toSet())
        assertEquals(listOf(true, true), sections.creators.map { it.isFollowed })
    }

    @Test
    fun `a followed creator with nothing recent stays listed so it can be unfollowed`() {
        val sections = followedCreatorSections(
            followed = mapOf("quiet" to "Quiet Creator"),
            rankedCreators = emptyList(),
            uploads = emptyList(),
        )

        assertEquals("Quiet Creator", sections.creators.single().label)
        assertEquals(0, sections.creators.single().uploadCount)
        assertEquals(emptyList<CreatorUploadRef>(), sections.uploads)
    }

    @Test
    fun `unfollowing or blocking drops the creator and their uploads`() {
        val uploads = listOf(upload("s1", "alice", "Alice", "sound", 1), upload("w1", "bob", "Bob", "wallpaper", 2))
        val ranked = aggregateCreatorStats(uploads)

        val afterUnfollow = followedCreatorSections(mapOf("alice" to "Alice"), ranked, uploads)
        val blocked = followedCreatorSections(mapOf("alice" to "Alice", "bob" to "Bob"), ranked, uploads, excludedCreatorIds = setOf("bob"))

        assertEquals(listOf("alice"), afterUnfollow.creators.map { it.creatorId })
        assertEquals(listOf("s1"), afterUnfollow.uploads.map { it.id })
        assertEquals(listOf("alice"), blocked.creators.map { it.creatorId })
        assertEquals(listOf("s1"), blocked.uploads.map { it.id })
    }

    @Test
    fun `no follows leaves both sections empty`() {
        val sections = followedCreatorSections(emptyMap(), emptyList(), listOf(upload("s1", "alice", "Alice", "sound", 1)))

        assertEquals(emptyList<CreatorStats>(), sections.creators)
        assertEquals(emptyList<CreatorUploadRef>(), sections.uploads)
    }

    private fun upload(
        id: String,
        creatorId: String,
        label: String,
        type: String,
        votes: Int,
    ) = CreatorUploadRef(
        id = id,
        stableKey = "stable_$id",
        contentType = type,
        title = id,
        creatorId = creatorId,
        creatorLabel = label,
        thumbnailUrl = "",
        votes = votes,
        uploadedAt = 0L,
    )
}
