package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Account
import com.justdataplease.streetgym.app.data.AccountUser
import com.justdataplease.streetgym.app.data.CloudDocuments
import com.justdataplease.streetgym.app.data.CloudPlan
import com.justdataplease.streetgym.app.data.CloudTime
import com.justdataplease.streetgym.app.data.CloudWrite
import com.justdataplease.streetgym.app.data.DeleteField
import com.justdataplease.streetgym.app.data.LinkResult
import com.justdataplease.streetgym.app.data.ServerTime
import com.justdataplease.streetgym.app.data.SignInProvider
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * Firebase on the desktop and in tests, in place of Swift's Firebase Auth and Firestore: an account service that signs
 * in at once, and a document store that keeps what is written and checks it against firestore.rules' shapes.
 */

/**
 * Firebase Auth as a fake: [identities] are the Google and Apple accounts the athlete can pick (their email), [owners]
 * the users those accounts already have (from another phone). [online] false is a phone offline; [cancel] makes the
 * next sign-in sheet cancelled; [reauthenticates] answers Delete account's confirmation.
 */
class FakeAccount(
    override val available: Boolean = true,
    override val providers: Set<SignInProvider> = setOf(SignInProvider.GOOGLE, SignInProvider.APPLE),
    /** The user each account (by provider and email) already has; two phones share them by passing the same map. */
    val owners: MutableMap<Pair<SignInProvider, String>, String> = mutableMapOf(),
) : Account {
    override val user = MutableStateFlow<AccountUser?>(null)
    var online = true
    var cancel = false
    var reauthenticates = true
    val identities = mutableMapOf(SignInProvider.GOOGLE to "athlete@gmail.com", SignInProvider.APPLE to "x7@privaterelay.appleid.com")
    val deletedUsers = mutableListOf<String>()
    private var pending: Pair<SignInProvider, String>? = null

    override fun signInAnonymously(done: (AccountUser?) -> Unit) {
        if (!online) return done(null)
        user.value = AccountUser("anon${++made}", anonymous = true)
        done(user.value)
    }

    override fun link(provider: SignInProvider, done: (LinkResult) -> Unit) {
        if (cancel) {
            cancel = false
            return done(LinkResult.Cancelled)
        }
        if (!online) return done(LinkResult.Failed("offline"))
        val identity = provider to identities.getValue(provider)
        val current = user.value
        val owner = owners[identity]
        when {
            current != null && current.anonymous && owner != null -> {
                pending = identity
                done(LinkResult.Collision(current.uid))
            }
            current != null && current.anonymous -> {
                owners[identity] = current.uid
                user.value = AccountUser(current.uid, false, identity.second, provider)
                done(LinkResult.Linked(user.value!!))
            }
            else -> {
                val uid = owner ?: "user${++made}".also { owners[identity] = it }
                user.value = AccountUser(uid, false, identity.second, provider)
                done(LinkResult.Linked(user.value!!))
            }
        }
    }

    override fun takeOver(done: (AccountUser?) -> Unit) {
        val identity = pending ?: return done(null)
        pending = null
        user.value?.takeIf { it.anonymous }?.let { deletedUsers += it.uid }
        user.value = AccountUser(owners.getValue(identity), false, identity.second, identity.first)
        done(user.value)
    }

    override fun dropTakeOver() {
        pending = null
    }

    private companion object {
        /** Users made on every fake phone, so two phones never make the same uid. */
        var made = 0
    }

    override fun reauthenticate(done: (Boolean) -> Unit) = done(online && reauthenticates && user.value?.anonymous == false)

    override fun signOut() {
        user.value = null
    }

    override fun deleteUser(done: (Boolean) -> Unit) {
        val current = user.value ?: return done(false)
        if (!online) return done(false)
        owners.entries.removeAll { it.value == current.uid }
        deletedUsers += current.uid
        user.value = null
        done(true)
    }

    override fun deleteAnonymous(uid: String) {
        if (user.value?.uid == uid && user.value?.anonymous == true) {
            deletedUsers += uid
            user.value = null
        }
    }
}

/**
 * Firestore as a fake: [documents] by path, with the server's time written as [now]'s. Every write is checked against
 * the shapes firestore.rules allows (android-streetgym/firebase/firestore.rules), signed as [auth]; a refused write
 * fails the whole batch, as the server's rules do. [online] false fails everything. [writes] keeps every batch this
 * phone wrote.
 */
class FakeCloud(
    private val auth: () -> AccountUser?,
    var now: () -> Long = { 0L },
    /** The server's documents; two phones share them by passing the same map. */
    val documents: MutableMap<String, Map<String, Any?>> = linkedMapOf(),
) : CloudDocuments {
    var online = true
    val writes = mutableListOf<List<CloudWrite>>()
    /** Why the last batch was refused, if it was. */
    var refused: String? = null
        private set

    override fun read(paths: List<String>, done: (Map<String, Map<String, Any?>?>?) -> Unit) {
        if (!online) return done(null)
        done(paths.associateWith { documents[it] })
    }

    override fun write(writes: List<CloudWrite>, done: (Boolean) -> Unit) = done(apply(writes))

    override fun transaction(paths: List<String>, decide: (Map<String, Map<String, Any?>?>) -> CloudPlan, done: (String?) -> Unit) {
        if (!online) return done(null)
        val plan = decide(paths.associateWith { documents[it] })
        done(if (apply(plan.writes)) plan.outcome else null)
    }

    private fun apply(batch: List<CloudWrite>): Boolean {
        if (!online) return false
        val after = LinkedHashMap(documents)
        val at = now()
        batch.forEach { write ->
            when (write) {
                is CloudWrite.Remove -> after.remove(write.path)
                is CloudWrite.Put -> {
                    val values = write.fields.mapValues { (_, value) -> if (value === ServerTime) CloudTime(at) else value }
                    val fields = write.mergeFields
                    val merged = if (fields == null) values.filterValues { it !== DeleteField }
                        else (after[write.path].orEmpty() + values.filterKeys { it in fields }).filterValues { it !== DeleteField }
                    after[write.path] = merged
                }
            }
        }
        val problem = batch.firstNotNullOfOrNull { check(it, after, at) }
        refused = problem
        if (problem != null) return false
        documents.clear()
        documents.putAll(after)
        writes += batch
        return true
    }

    /** firestore.rules for one write, given the documents [after] the batch; null when allowed. */
    private fun check(write: CloudWrite, after: Map<String, Map<String, Any?>>, at: Long): String? {
        val user = auth() ?: return "not signed in"
        val parts = write.path.split('/')
        val data = after[write.path]
        fun Map<String, Any?>.whole(key: String): Long? = (this[key] as? Number)?.takeIf { it is Int || it is Long }?.toLong()
        return when {
            parts[0] == "usernames" && parts.size == 2 -> when (write) {
                is CloudWrite.Remove -> if (documents[write.path]?.get("uid") == user.uid) null else "releasing another name"
                is CloudWrite.Put -> when {
                    !Regex("^[a-z0-9_.]{3,20}$").matches(parts[1]) -> "bad name"
                    data!!.keys != setOf("uid") || data["uid"] != user.uid -> "claim shape"
                    (after["users/${user.uid}"]?.get("username") as? String)?.lowercase() != parts[1] -> "claim without profile"
                    else -> null
                }
            }
            parts[0] == "users" && parts.size == 2 -> when {
                parts[1] != user.uid -> "another profile"
                write is CloudWrite.Remove -> null
                else -> {
                    val name = data!!["username"] as? String
                    when {
                        name == null || !Regex("^[A-Za-z0-9_.]{3,20}$").matches(name) -> "profile name"
                        after["usernames/${name.lowercase()}"]?.get("uid") != user.uid -> "name not held"
                        !Regex("^[A-Z]{2}$").matches(data["country"] as? String ?: "") -> "country"
                        data["visible"] !is Boolean -> "visible"
                        else -> null
                    }
                }
            }
            parts[0] == "users" && parts.size == 4 && parts[2] == "data" ->
                if (!user.anonymous && parts[1] == user.uid && parts[3] in setOf("progress", "library")) null else "cloud copy"
            parts[0] == "scores" && parts.size == 2 -> when {
                parts[1] != user.uid -> "another score"
                write is CloudWrite.Remove -> null
                else -> {
                    val d = data!!
                    val points = d.whole("points")
                    val month = d.whole("month_points")
                    when {
                        !setOf("v", "username", "country", "points", "month", "month_points", "updated_at").containsAll(d.keys) -> "score keys"
                        d["v"] != 4 -> "score version"
                        d["username"] != after["users/${user.uid}"]?.get("username") -> "score name"
                        after["usernames/${(d["username"] as String).lowercase()}"]?.get("uid") != user.uid -> "score claim"
                        d["updated_at"] != CloudTime(at) -> "score time"
                        !Regex("^[A-Z]{2}$").matches(d["country"] as? String ?: "") -> "score country"
                        points == null || points < 0 || points > 100_000_000 -> "points"
                        !Regex("^[0-9]{4}-(0[1-9]|1[0-2])$").matches(d["month"] as? String ?: "") -> "month"
                        month == null || month < 0 || month > points -> "month points"
                        else -> null
                    }
                }
            }
            parts[0] == "analytics" -> when {
                !Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(parts[1]) -> "install id"
                write is CloudWrite.Remove -> null
                parts.size == 2 -> installProblem(data!!, documents[write.path], user, at)
                parts.size == 4 && parts[2] == "sessions" ->
                    if (!Regex("^[0-9]{10,15}$").matches(parts[3])) "session id" else sessionProblem(data!!, at)
                else -> "analytics path"
            }
            parts[0] == "boards" || parts[0] == "meta" -> "server only"
            else -> "unknown path"
        }
    }

    private fun installProblem(d: Map<String, Any?>, before: Map<String, Any?>?, user: AccountUser, at: Long): String? {
        val allowed = setOf("v", "appSetId", "appSetScope", "accountUid", "firstSeen", "lastSeen", "versionName", "versionCode", "tier",
            "debug", "shareProgress", "placement", "skills", "score", "switches", "completedClasses", "trainedClasses", "markedClasses",
            "atLevel", "atDay")
        val placement = setOf("pullUps", "pushUps", "squats", "skillTest", "startLevel", "startDay")
        return when {
            !allowed.containsAll(d.keys) -> "install keys ${d.keys - allowed}"
            !d.keys.containsAll(setOf("v", "firstSeen", "lastSeen", "versionName", "versionCode", "tier", "debug", "shareProgress")) -> "install missing"
            d["v"] != 1 || d["lastSeen"] != CloudTime(at) || d["firstSeen"] !is CloudTime -> "install times"
            d["tier"] !in setOf("free", "plus", "pro") || d["debug"] !is Boolean || d["shareProgress"] != true -> "install values"
            // accountUid is only ever the writer's own account; one left as it was may stay (the rules' affectedKeys).
            "accountUid" in d && d["accountUid"] != before?.get("accountUid") && (user.anonymous || d["accountUid"] != user.uid) -> "account uid"
            (d["placement"] as? Map<*, *>)?.keys?.let { !placement.containsAll(it) } == true -> "placement"
            else -> null
        }
    }

    private fun sessionProblem(d: Map<String, Any?>, at: Long): String? {
        val allowed = setOf("v", "programId", "level", "day", "dayType", "exam", "custom", "repeat", "intensity", "effort", "startedAt",
            "finishedAt", "durationSec", "plannedSets", "doneSets", "leftOutSets", "exercises", "uploadedAt")
        val required = allowed - setOf("dayType", "effort")
        return when {
            !allowed.containsAll(d.keys) || !d.keys.containsAll(required) -> "session keys"
            d["v"] != 1 || d["uploadedAt"] != CloudTime(at) -> "session version or time"
            !Regex("^(sg:l[0-9]{2}:p[0-9]{4}|ready:[a-z0-9_]{1,40}|custom|unknown)$").matches(d["programId"] as? String ?: "") -> "program id"
            d["intensity"] !in setOf("easier", "normal", "harder") -> "intensity"
            d["startedAt"] !is CloudTime || d["finishedAt"] !is CloudTime -> "session times"
            (d["exercises"] as? List<*>)?.let { it.size <= 60 } != true -> "exercises"
            else -> null
        }
    }
}
