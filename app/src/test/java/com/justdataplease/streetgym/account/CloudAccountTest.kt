package com.justdataplease.streetgym.account

import org.junit.Assert.*
import org.junit.Test

/** The anonymous user progress analytics signs in with is no account: Cloud.uid, sync and the leaderboard never see it. */
class CloudAccountTest {
    private data class User(val uid: String, val anonymous: Boolean)

    @Test fun anAnonymousUserIsNoAccount() {
        assertNull(Cloud.account(User("anon", anonymous = true), User::anonymous))
        assertNull(Cloud.account(null, User::anonymous))
    }

    @Test fun aGoogleAccountIsTheAccount() {
        val google = User("google", anonymous = false)
        assertSame(google, Cloud.account(google, User::anonymous))
    }
}
