"""Street Gymnastics leaderboard: rebuilds the boards from the Pro scores every six hours.

Cost: a run with no new or removed score is two reads (one changed-score probe, one count) and stops. Otherwise it
reads each score once, reads the boards' hashes, and writes only the boards that changed. Deploy with
`firebase deploy --only functions,firestore` from streetgymnastics/firebase (docs/TIERS.md, "Pro account setup").
"""
from firebase_admin import firestore, initialize_app
from firebase_functions import scheduler_fn

from boards import board_hash, build_boards

initialize_app()


@scheduler_fn.on_schedule(schedule="every 6 hours", region="europe-west1", memory=256, timeout_sec=300)
def rebuild_boards(event: scheduler_fn.ScheduledEvent) -> None:
    db = firestore.client()
    meta_ref = db.document("meta/boards")
    meta = (meta_ref.get().to_dict() or {})
    started = firestore.SERVER_TIMESTAMP
    scores = db.collection("scores")

    count = scores.count().get()[0][0].value
    since = meta.get("built_at")
    changed = True
    if since is not None and count == meta.get("count"):
        changed = any(True for _ in scores.where(filter=firestore.FieldFilter("updated_at", ">", since)).limit(1).stream())
    if not changed:
        return

    boards = build_boards([doc.to_dict() for doc in scores.stream()])
    existing = {doc.id: (doc.to_dict() or {}).get("hash") for doc in db.collection("boards").select(["hash"]).stream()}
    batch, pending = db.batch(), 0

    def flush():
        nonlocal batch, pending
        if pending:
            batch.commit()
        batch, pending = db.batch(), 0

    for board_id, board in boards.items():
        digest = board_hash(board)
        if existing.get(board_id) == digest:
            continue
        batch.set(db.document(f"boards/{board_id}"), {**board, "hash": digest, "updated_at": started})
        pending += 1
        if pending == 400:
            flush()
    for board_id in existing.keys() - boards.keys():
        batch.delete(db.document(f"boards/{board_id}"))
        pending += 1
        if pending == 400:
            flush()
    flush()
    meta_ref.set({"built_at": started, "count": count})
