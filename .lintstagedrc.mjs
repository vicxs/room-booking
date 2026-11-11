// Fast, staged-only fixes. Full checks run in CI.
const escapeRegex = (path) => path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export default {
  "frontend/**/*.{ts,tsx,js,mjs,cjs,json,jsonc,css}": (files) =>
    `pnpm -C frontend exec biome check --write --no-errors-on-unmatched ${files.join(" ")}`,
  "backend/**/*.java": (files) =>
    `backend/mvnw -q -f backend/pom.xml spotless:apply -DspotlessFiles=${files.map(escapeRegex).join(",")}`,
  "backend/pom.xml": () => "backend/mvnw -q -f backend/pom.xml spotless:apply",
};
