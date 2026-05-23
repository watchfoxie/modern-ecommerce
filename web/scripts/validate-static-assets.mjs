import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(scriptDir, "..");
const projectRoot = path.resolve(webRoot, "..");
const srcRoot = path.join(webRoot, "src");
const staticRoot = path.join(projectRoot, "static");
const failures = [];

async function sourceFiles(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(entryPath);
    }
    return /\.(ts|tsx|js|jsx|css)$/.test(entry.name) ? [entryPath] : [];
  }));
  return nested.flat();
}

function staticReferences(source) {
  return [...source.matchAll(/["'`]((?:\/)?static\/assets\/[^"'`)\s]+)/g)]
    .map((match) => match[1].replace(/^\/+/, ""));
}

for (const filePath of await sourceFiles(srcRoot)) {
  const source = await fs.readFile(filePath, "utf8");
  for (const reference of staticReferences(source)) {
    const absolutePath = path.resolve(projectRoot, reference);
    const relativeToStatic = path.relative(staticRoot, absolutePath);
    if (relativeToStatic.startsWith("..") || path.isAbsolute(relativeToStatic)) {
      failures.push(`${path.relative(webRoot, filePath)} escapes static root: ${reference}`);
      continue;
    }
    try {
      await fs.access(absolutePath);
    } catch {
      failures.push(`${path.relative(webRoot, filePath)} references missing asset: ${reference}`);
    }
  }
}

if (failures.length > 0) {
  console.error(`Static asset validation failed with ${failures.length} issue(s):`);
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log("Validated frontend static asset references.");
