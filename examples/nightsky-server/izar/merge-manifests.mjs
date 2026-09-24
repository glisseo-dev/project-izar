// Regenerates manifest.json (see ../README.md) from the two client modules' own generated
// manifests, deduplicating by operation id. Run after `generate-sources` has produced both:
//
//   node izar/merge-manifests.mjs
//
// from this module's own directory.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const sources = [
  "../nightsky-sync-client/target/izar/manifest.json",
  "../nightsky-reactive-client/target/izar/manifest.json",
];

const manifests = sources.map((path) => JSON.parse(readFileSync(path, "utf8")));

const byId = new Map();
for (const manifest of manifests) {
  for (const operation of manifest.operations) {
    const existing = byId.get(operation.id);
    if (existing && existing.body !== operation.body) {
      throw new Error(`Conflicting bodies for operation id ${operation.id}`);
    }
    byId.set(operation.id, operation);
  }
}

const merged = {
  format: manifests[0].format,
  version: manifests[0].version,
  operations: [...byId.values()],
};

mkdirSync("izar", { recursive: true });
writeFileSync("izar/manifest.json", JSON.stringify(merged, null, 2) + "\n");
console.log(`Wrote izar/manifest.json with ${merged.operations.length} unique operation(s).`);
