// A tiny in-memory stand-in for Firestore, covering only the calls the API uses:
// collection/doc get-set-update-delete, where('==' | 'in'), getAll, batch, and runTransaction.
// Transactions are serialised (one at a time), which mimics the outcome Firestore guarantees.
import { randomUUID } from 'node:crypto';

class Snap {
  constructor(ref, data) {
    this.ref = ref;
    this.id = ref.id;
    this._data = data;
  }
  get exists() {
    return this._data !== undefined;
  }
  data() {
    return this._data ? { ...this._data } : undefined;
  }
}

class DocRef {
  constructor(db, col, id) {
    this.db = db;
    this.col = col;
    this.id = id;
  }
  _rows() {
    return this.db._col(this.col);
  }
  _exists() {
    return this._rows().has(this.id);
  }
  _set(d) {
    this._rows().set(this.id, { ...d });
  }
  _update(d) {
    const cur = this._rows().get(this.id);
    if (!cur) throw new Error('NOT_FOUND');
    this._rows().set(this.id, { ...cur, ...d });
  }
  _delete() {
    this._rows().delete(this.id);
  }
  async get() {
    const cur = this._rows().get(this.id);
    return new Snap(this, cur ? { ...cur } : undefined);
  }
  async set(d) {
    this._set(d);
  }
  async update(d) {
    this._update(d);
  }
  async delete() {
    this._delete();
  }
}

class Query {
  constructor(db, col, filters = []) {
    this.db = db;
    this.col = col;
    this.filters = filters;
  }
  where(field, op, value) {
    return new Query(this.db, this.col, [...this.filters, { field, op, value }]);
  }
  async get() {
    const test = ({ field, op, value }, row) => {
      if (op === '==') return row[field] === value;
      if (op === 'in') return value.includes(row[field]);
      throw new Error(`Fake Firestore: unsupported operator ${op}`);
    };
    const docs = [...this.db._col(this.col).entries()]
      .filter(([, row]) => this.filters.every((f) => test(f, row)))
      .map(([id, row]) => new Snap(new DocRef(this.db, this.col, id), { ...row }));
    return { docs, size: docs.length, empty: docs.length === 0 };
  }
}

class Collection extends Query {
  doc(id = randomUUID()) {
    return new DocRef(this.db, this.col, id);
  }
}

export class FakeFirestore {
  constructor() {
    this.tables = new Map();
    this.txChain = Promise.resolve();
  }
  _col(name) {
    if (!this.tables.has(name)) this.tables.set(name, new Map());
    return this.tables.get(name);
  }
  collection(name) {
    return new Collection(this, name);
  }
  async getAll(...refs) {
    return Promise.all(refs.map((r) => r.get()));
  }
  batch() {
    const ops = [];
    return {
      update: (ref, d) => ops.push(() => ref._update(d)),
      set: (ref, d) => ops.push(() => ref._set(d)),
      delete: (ref) => ops.push(() => ref._delete()),
      commit: async () => ops.forEach((op) => op()),
    };
  }
  runTransaction(fn) {
    const run = async () => {
      const writes = [];
      const t = {
        get: (target) => target.get(),
        create: (ref, d) =>
          writes.push(() => {
            if (ref._exists()) throw new Error('ALREADY_EXISTS');
            ref._set(d);
          }),
        set: (ref, d) => writes.push(() => ref._set(d)),
        update: (ref, d) => writes.push(() => ref._update(d)),
      };
      const result = await fn(t);
      writes.forEach((w) => w()); // writes are applied only if the callback finished without throwing
      return result;
    };
    const p = this.txChain.then(run);
    this.txChain = p.catch(() => {});
    return p;
  }
}