// Pure rules shared by the Worker and its tests (and mirrored, line for line, by the app's ShuffleMerge.kt).

/** Most ids kept for ONE shuffle context, and how many contexts a group keeps. Beyond that the oldest go. */
export const MAX_IDS_PER_CONTEXT = 3000;
export const MAX_CONTEXTS = 300;

/**
 * Merge what a device reports about a shuffle context into what the room holds.
 *
 *  - A HIGHER epoch means the context finished a full no-repeat cycle somewhere: the old set is dropped and the new cycle
 *    starts from what that device reports.
 *  - The SAME epoch is a union (two devices played different songs of the same cycle).
 *  - A LOWER epoch is stale (a device that has been offline since before the cycle ended): ignored, and the room's state is
 *    sent back so the device catches up.
 *
 * `state` is { epoch, ids: string[], last: string|null }; `incoming` is { epoch, add: string[], last: string|null }.
 * Returns { state, changed, stale, delta } where `delta` is what other devices have to be told.
 */
export function mergeShuffle(state, incoming) {
  const base = state ?? { epoch: 0, ids: [], last: null };
  const epoch = Number.isFinite(incoming.epoch) ? Math.max(0, Math.floor(incoming.epoch)) : 0;
  const add = Array.isArray(incoming.add) ? incoming.add.filter((x) => typeof x === "string" && x.length > 0 && x.length < 64) : [];
  if (epoch < base.epoch) return { state: base, changed: false, stale: true, delta: null };
  let ids;
  let reset = false;
  if (epoch > base.epoch) {
    ids = unique(add);
    reset = true;
  } else {
    const have = new Set(base.ids);
    const fresh = unique(add).filter((id) => !have.has(id));
    ids = base.ids.concat(fresh);
  }
  if (ids.length > MAX_IDS_PER_CONTEXT) ids = ids.slice(ids.length - MAX_IDS_PER_CONTEXT);
  const last = typeof incoming.last === "string" && incoming.last ? incoming.last : base.last;
  const next = { epoch, ids, last };
  const changed = reset || ids.length !== base.ids.length || last !== base.last;
  const delta = changed ? { epoch, add: reset ? ids : ids.filter((id) => !new Set(base.ids).has(id)), last, reset } : null;
  return { state: next, changed, stale: false, delta };
}

function unique(list) {
  return [...new Set(list)];
}
