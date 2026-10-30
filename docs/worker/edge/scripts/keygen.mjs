// Prints the signing key pair for /v1/config. The PRIVATE half goes into the Worker (wrangler secret put SIGNING_KEY);
// the PUBLIC half goes into the app (EDGE_PUBLIC_KEY in edge/EdgeConfig.kt). Never commit the private half.
import { webcrypto as c } from "node:crypto";
const pair = await c.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const b64 = (buf) => Buffer.from(buf).toString("base64");
console.log("PRIVATE (secret SIGNING_KEY):\n" + b64(await c.subtle.exportKey("pkcs8", pair.privateKey)));
console.log("\nPUBLIC (app EDGE_PUBLIC_KEY):\n" + b64(await c.subtle.exportKey("spki", pair.publicKey)));
