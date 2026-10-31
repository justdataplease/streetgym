package com.justdataplease.streetgym.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** Releasing a profile (Delete profile, or connecting a Google account that has its own) deletes only what is there. */
class ProfilesTest {
    @Test fun aProfileReleasesItsScoreItsUsernameAndItself() {
        assertEquals(listOf("scores/u1", "usernames/street_ana", "users/u1"),
            Profiles.releasePaths("u1", Profile("Street_Ana", "GR")))
    }

    @Test fun withoutAProfileNoUsernameClaimIsTouched() {
        // The rules refuse deleting a claim that isn't there, and the whole batch with it.
        assertEquals(listOf("scores/u1", "users/u1"), Profiles.releasePaths("u1", null))
    }
}
