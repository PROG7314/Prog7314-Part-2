// fakeFirestore.js - a tiny in-memory stand-in for the parts of Firestore the routes use, so the
// route tests run without a Firebase project. Supports where('==' / 'in'), getAll, and transactions.

// Copies plain objects and arrays but keeps class instances (Firestore Timestamps) intact,
// because structuredClone would strip their methods.
function clone(v) {
  if (Array.isArray(v)) return v.map(clone);
  if (v && typeof v === 'object' && Object.getPrototypeOf(v) === Object.prototype) {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, clone(x)]));
  }
  return v;
}

class Snap {
  constructor(id, data) {
    this.id = id;
    this.exists = data !== undefined;
    this._data = data;
  }
  data() {
    return this._data === undefined ? undefined : clone(this._data);
  }
}

export function createFakeDb(seed = {}) {
  const store = new Map(); // collection name -> Map(id -> data)
  for (const [name, docs] of Object.entries(seed)) store.set(name, new Map(Object.entries(docs)));
  const coll = (name) => {
    if (!store.has(name)) store.set(name, new Map());
    return store.get(name);
  };
  let autoId = 0;

  const docRef = (name, id) => ({ id, collection: name });
  const readDoc = (ref) => new Snap(ref.id, coll(ref.collection).get(ref.id));

  const query = (name, filters = []) => ({
    where(field, op, value) {
      return query(name, [...filters, { field, op, value }]);
    },
    async get() {
      const docs = [...coll(name).entries()]
        .filter(([, d]) =>
          filters.every(({ field, op, value }) =>
            op === '==' ? d[field] === value : op === 'in' ? value.includes(d[field]) : false
          )
        )
        .map(([id, d]) => new Snap(id, d));
      return { docs };
    },
  });

  const write = {
    create(ref, data) {
      if (coll(ref.collection).has(ref.id)) throw new Error(`ALREADY_EXISTS: ${ref.collection}/${ref.id}`);
      coll(ref.collection).set(ref.id, clone(data));
    },
    set(ref, data) {
      coll(ref.collection).set(ref.id, clone(data));
    },
    update(ref, patch) {
      const current = coll(ref.collection).get(ref.id);
      if (!current) throw new Error(`NOT_FOUND: ${ref.collection}/${ref.id}`);
      coll(ref.collection).set(ref.id, { ...current, ...clone(patch) });
    },
  };

  return {
    collection(name) {
      return {
        doc: (id = `auto${++autoId}`) => ({
          ...docRef(name, id),
          get: async () => readDoc(docRef(name, id)),
          ...Object.fromEntries(Object.entries(write).map(([k, fn]) => [k, async (data) => fn(docRef(name, id), data)])),
        }),
        where: (...args) => query(name).where(...args),
        get: async () => query(name).get(),
      };
    },
    async getAll(...refs) {
      return refs.map(readDoc);
    },
    // Writes are buffered and applied on commit, like Firestore - nothing changes if the callback throws.
    async runTransaction(fn) {
      const pending = [];
      const t = {
        get: async (target) => (target.where ? target.get() : readDoc(target)),
        create: (ref, data) => pending.push(() => write.create(ref, data)),
        set: (ref, data) => pending.push(() => write.set(ref, data)),
        update: (ref, patch) => pending.push(() => write.update(ref, patch)),
      };
      const result = await fn(t);
      pending.forEach((apply) => apply());
      return result;
    },
    // test helper: peek at stored data
    _peek: (name, id) => coll(name).get(id),
  };
}
