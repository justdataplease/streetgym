"""Progress analytics older than 24 months, deleted: what the privacy page promises ("each upload is deleted 24 months
after it was made"). Both times are the server's, so a phone's clock can't keep a document longer.

    analytics/{installId}/sessions/{id}   uploadedAt, when the class was uploaded
    analytics/{installId}                 lastSeen, the install's latest upload

A session is uploaded in the same batch as its install's lastSeen, so an install past the cutoff has no newer session.
Takes the Firestore client and its FieldFilter, so it can be tested without Firebase.
"""
import datetime

KEEP = datetime.timedelta(days=730)
PAGE = 400
# A run stops after this many deletions and the next day's run goes on.
MOST = 20000


def cutoff(now: datetime.datetime) -> datetime.datetime:
    return now - KEEP


def delete_expired(db, field_filter, now: datetime.datetime) -> int:
    """Deletes the sessions, then the installs, older than the cutoff; returns how many documents went."""
    before = cutoff(now)
    deleted = 0
    for query in (
        db.collection_group("sessions").where(filter=field_filter("uploadedAt", "<", before)),
        db.collection("analytics").where(filter=field_filter("lastSeen", "<", before)),
    ):
        while deleted < MOST:
            refs = [doc.reference for doc in query.limit(min(PAGE, MOST - deleted)).stream()]
            if not refs:
                break
            batch = db.batch()
            for ref in refs:
                batch.delete(ref)
            batch.commit()
            deleted += len(refs)
    return deleted
