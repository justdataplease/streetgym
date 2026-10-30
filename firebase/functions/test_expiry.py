"""The analytics expiry against a small in-memory Firestore: python -m pytest streetgymnastics/firebase/functions"""
import datetime

import expiry

NOW = datetime.datetime(2028, 11, 1, tzinfo=datetime.timezone.utc)
OLD = NOW - datetime.timedelta(days=731)
NEW = NOW - datetime.timedelta(days=729)


class Ref:
    def __init__(self, store, path):
        self.store, self.path = store, path


class Doc:
    def __init__(self, ref):
        self.reference = ref


class Query:
    def __init__(self, store, match, filter=None, limit=None):
        self.store, self.match, self.filter, self.count = store, match, filter, limit

    def where(self, filter):
        return Query(self.store, self.match, filter, self.count)

    def limit(self, count):
        return Query(self.store, self.match, self.filter, count)

    def stream(self):
        field, op, value = self.filter
        assert op == "<"
        hits = [path for path, data in sorted(self.store.docs.items()) if self.match(path) and data[field] < value]
        return [Doc(Ref(self.store, path)) for path in hits[:self.count]]


class Batch:
    def __init__(self, store):
        self.store, self.refs = store, []

    def delete(self, ref):
        self.refs.append(ref)

    def commit(self):
        assert len(self.refs) <= 500
        for ref in self.refs:
            del self.store.docs[ref.path]


class Store:
    def __init__(self, docs):
        self.docs = dict(docs)

    def collection_group(self, name):
        return Query(self, lambda path: path.split("/")[-2] == name)

    def collection(self, name):
        return Query(self, lambda path: path.count("/") == 1 and path.startswith(name + "/"))

    def batch(self):
        return Batch(self)


def field_filter(field, op, value):
    return field, op, value


def test_uploads_older_than_24_months_go_and_newer_ones_stay():
    store = Store({
        "analytics/a": {"lastSeen": OLD},
        "analytics/a/sessions/1": {"uploadedAt": OLD},
        "analytics/b": {"lastSeen": NEW},
        "analytics/b/sessions/1": {"uploadedAt": OLD},
        "analytics/b/sessions/2": {"uploadedAt": NEW},
    })
    assert expiry.delete_expired(store, field_filter, NOW) == 3
    assert set(store.docs) == {"analytics/b", "analytics/b/sessions/2"}


def test_the_cutoff_is_730_days():
    assert expiry.cutoff(NOW) == NOW - datetime.timedelta(days=730)


def test_many_documents_go_in_pages_and_a_run_stops_at_its_limit(monkeypatch):
    monkeypatch.setattr(expiry, "MOST", 1000)
    store = Store({f"analytics/a/sessions/{i:04d}": {"uploadedAt": OLD} for i in range(1200)} | {"analytics/a": {"lastSeen": OLD}})
    assert expiry.delete_expired(store, field_filter, NOW) == 1000
    assert len(store.docs) == 201
    assert expiry.delete_expired(store, field_filter, NOW) == 201
    assert not store.docs
