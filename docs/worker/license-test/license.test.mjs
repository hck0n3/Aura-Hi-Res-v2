// Pruebas del Worker de licencia (fila 530): `node --test docs/worker/license-test/license.test.mjs`
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// worker.js se despliega TAL CUAL en Cloudflare (sin `export` extra que pueda rechazar). Para probar sus funciones internas se
// copia a un archivo temporal con un export añadido al final; el original no se toca.
const src = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "worker.js"), "utf8");
const dir = mkdtempSync(join(tmpdir(), "aura-license-"));
writeFileSync(join(dir, "worker.mjs"), src + "\nexport { decideBinding, parseBinding };\n");
const { default: worker, decideBinding, parseBinding } = await import(pathToFileURL(join(dir, "worker.mjs")).href);

const DAY = 24 * 60 * 60 * 1000;
const NOW = 1_800_000_000_000;

test("limit 1 is the old rule: empty -> allowed", () => {
  assert.equal(decideBinding([], "a", NOW, 1).allowed, true);
});
test("limit 1: other active device blocks", () => {
  const d = decideBinding([{ id: "a", last_seen: NOW - DAY }], "b", NOW, 1);
  assert.equal(d.allowed, false);
});
test("limit 1: other device idle >2 days is released", () => {
  const d = decideBinding([{ id: "a", last_seen: NOW - 3 * DAY }], "b", NOW, 1);
  assert.equal(d.allowed, true);
  assert.deepEqual(d.binding.devices.map((x) => x.id), ["b"]);
});
test("limit 3: three devices fit, the fourth is blocked", () => {
  let devs = [];
  for (const id of ["a", "b", "c"]) {
    const d = decideBinding(devs, id, NOW, 3);
    assert.equal(d.allowed, true, id);
    devs = d.binding.devices;
  }
  assert.equal(decideBinding(devs, "d", NOW, 3).allowed, false);
});
test("limit 3: an idle device frees a slot", () => {
  const devs = [
    { id: "a", last_seen: NOW - 5 * DAY },
    { id: "b", last_seen: NOW - DAY },
    { id: "c", last_seen: NOW - DAY },
  ];
  assert.equal(decideBinding(devs, "d", NOW, 3).allowed, true);
});
test("a key-only device already in the list does not evict the others", () => {
  const devs = [{ id: "a", last_seen: NOW - 1000 }, { id: "b", last_seen: NOW - 1000 }];
  const d = decideBinding(devs, "a", NOW, 1);
  assert.equal(d.allowed, true);
  assert.deepEqual(d.binding.devices.map((x) => x.id).sort(), ["a", "b"]);
});
test("a key-only NEW device is blocked while others are active", () => {
  const devs = [{ id: "a", last_seen: NOW - 1000 }, { id: "b", last_seen: NOW - 1000 }];
  assert.equal(decideBinding(devs, "c", NOW, 1).allowed, false);
});
test("old KV format is understood and new format keeps the old fields", () => {
  assert.deepEqual(parseBinding(JSON.stringify({ device_id: "a", last_seen: 5 })), [{ id: "a", last_seen: 5 }]);
  const d = decideBinding([], "z", NOW, 3);
  assert.equal(d.binding.device_id, "z");
  assert.equal(d.binding.last_seen, NOW);
});
test("garbage binding is treated as empty", () => {
  assert.deepEqual(parseBinding("{nope"), []);
  assert.deepEqual(parseBinding(null), []);
});

// --- full /verify through fetch(), with Gumroad and KV faked ---
function env(extra = {}) {
  const store = new Map();
  return {
    PRODUCT_ID: "p",
    LICENSES: { get: async (k) => store.get(k) ?? null, put: async (k, v) => void store.set(k, v) },
    store,
    ...extra,
  };
}
function gumroad(purchase) {
  globalThis.fetch = async () => ({ json: async () => ({ success: true, purchase }) });
}
const verify = (e, body) =>
  worker.fetch(new Request("https://x/verify", { method: "POST", body: JSON.stringify(body) }), e).then((r) => r.json());

test("verify: no email keeps one device per key", async () => {
  gumroad({ email: "Buyer@Mail.com" });
  const e = env();
  assert.equal((await verify(e, { license_key: "K", device_id: "a" })).status, "active");
  assert.equal((await verify(e, { license_key: "K", device_id: "b" })).status, "device_mismatch");
  assert.equal((await verify(e, { license_key: "K", device_id: "a" })).status, "active");
});
test("verify: matching email (any case) allows 3 devices", async () => {
  gumroad({ email: "Buyer@Mail.com" });
  const e = env();
  for (const id of ["a", "b", "c"]) {
    assert.equal((await verify(e, { license_key: "K", device_id: id, email: " buyer@mail.COM " })).status, "active", id);
  }
  assert.equal((await verify(e, { license_key: "K", device_id: "d", email: "buyer@mail.com" })).status, "device_mismatch");
});
test("verify: wrong email -> email_mismatch and nothing is stored", async () => {
  gumroad({ email: "buyer@mail.com" });
  const e = env();
  assert.equal((await verify(e, { license_key: "K", device_id: "a", email: "other@mail.com" })).status, "email_mismatch");
  assert.equal(e.store.size, 0);
});
test("verify: first device without email, second with email -> both active", async () => {
  gumroad({ email: "buyer@mail.com" });
  const e = env();
  assert.equal((await verify(e, { license_key: "K", device_id: "a" })).status, "active");
  assert.equal((await verify(e, { license_key: "K", device_id: "b", email: "buyer@mail.com" })).status, "active");
  assert.equal((await verify(e, { license_key: "K", device_id: "a" })).status, "active");
});
test("verify: MAX_DEVICES env changes the limit", async () => {
  gumroad({ email: "buyer@mail.com" });
  const e = env({ MAX_DEVICES: "2" });
  assert.equal((await verify(e, { license_key: "K", device_id: "a", email: "buyer@mail.com" })).status, "active");
  assert.equal((await verify(e, { license_key: "K", device_id: "b", email: "buyer@mail.com" })).status, "active");
  assert.equal((await verify(e, { license_key: "K", device_id: "c", email: "buyer@mail.com" })).status, "device_mismatch");
});
test("verify: ended and invalid are untouched", async () => {
  gumroad({ email: "b@m.com", subscription_ended_at: "2020-01-01T00:00:00Z" });
  assert.equal((await verify(env(), { license_key: "K", device_id: "a", email: "b@m.com" })).status, "ended");
  globalThis.fetch = async () => ({ json: async () => ({ success: false }) });
  assert.equal((await verify(env(), { license_key: "K", device_id: "a" })).status, "invalid");
});

test("verify: email_ok only when the purchase email was confirmed; master keys never carry it and have no limit", async () => {
  gumroad({ email: "buyer@mail.com" });
  const e = env({ MASTER_KEYS: "MASTERKEY" });
  const ok = await verify(e, { license_key: "K", device_id: "a", email: "buyer@mail.com" });
  assert.equal(ok.email_ok, true);
  const noEmail = await verify(e, { license_key: "K", device_id: "a" });
  assert.equal(noEmail.email_ok, undefined);
  for (const id of ["m1", "m2", "m3", "m4", "m5"]) {
    const m = await verify(e, { license_key: "MASTERKEY", device_id: id, email: "buyer@mail.com" });
    assert.deepEqual(m, { status: "active" }, id);
  }
  assert.equal(e.store.size, 1); // only K was stored; the master key leaves no trace
});
