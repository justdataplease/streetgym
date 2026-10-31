package com.justdataplease.streetgym.account

import org.junit.Assert.*
import org.junit.Test

/**
 * The anonymous user is no account: Cloud.uid and cloud progress never see it. It may hold a profile (Cloud.profileUid),
 * and connecting Google links it, so the uid, username and score stay.
 */
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
