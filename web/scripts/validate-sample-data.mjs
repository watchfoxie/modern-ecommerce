import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(scriptDir, "..");
const projectRoot = path.resolve(webRoot, "..");
const schemasDir = path.join(projectRoot, "static", "sample-data", "json-schemas");
const dataDir = path.join(projectRoot, "static", "sample-data", "structured-data");
const assetsDir = path.join(projectRoot, "static", "assets");

const collections = [
  "auth-service.roles",
  "auth-service.users",
  "user-service.users",
  "category-service.categories",
  "product-service.products",
  "cart-service.carts",
  "order-service.orders",
];

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

const failures = [];
const documentsByCollection = new Map();

async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

function objectId(document, fieldName = "_id") {
  const value = document[fieldName];
  return value && typeof value === "object" ? value.$oid : undefined;
}

function collectValidationError(collection, index, errors) {
  const detail = ajv.errorsText(errors, { dataVar: `${collection}[${index}]` });
  failures.push(`${collection}[${index}] ${detail}`);
}

function assertUnique(collection, documents, selector, label) {
  const seen = new Set();
  for (const document of documents) {
    const value = selector(document);
    if (value === undefined || value === null || value === "") {
      continue;
    }

    if (seen.has(value)) {
      failures.push(`${collection} contains duplicate ${label}: ${value}`);
    }
    seen.add(value);
  }
}

async function assertAssetExists(collection, imagePath) {
  if (!imagePath) {
    return;
  }

  const normalizedPath = imagePath.replaceAll("\\", "/");
  if (!normalizedPath.startsWith("static/assets/")) {
    failures.push(`${collection} image path must stay under static/assets: ${imagePath}`);
    return;
  }

  const absolutePath = path.resolve(projectRoot, normalizedPath);
  const relativeToAssets = path.relative(assetsDir, absolutePath);
  if (relativeToAssets.startsWith("..") || path.isAbsolute(relativeToAssets)) {
    failures.push(`${collection} image path escapes static/assets: ${imagePath}`);
    return;
  }

  try {
    await fs.access(absolutePath);
  } catch {
    failures.push(`${collection} references missing asset: ${imagePath}`);
  }
}

for (const collection of collections) {
  const schemaPath = path.join(schemasDir, `${collection}.schema.json`);
  const dataPath = path.join(dataDir, `${collection}.json`);
  const schema = await readJson(schemaPath);
  const data = await readJson(dataPath);
  const validate = ajv.compile(schema);

  if (!Array.isArray(data)) {
    failures.push(`${collection}.json must contain an array of documents`);
    continue;
  }

  data.forEach((document, index) => {
    if (!validate(document)) {
      collectValidationError(collection, index, validate.errors);
    }
  });

  documentsByCollection.set(collection, data);
}

const categories = documentsByCollection.get("category-service.categories") ?? [];
const products = documentsByCollection.get("product-service.products") ?? [];
const categoryIds = new Set(categories.map((category) => objectId(category)).filter(Boolean));
const categorySlugs = new Set(categories.map((category) => category.slug).filter(Boolean));

assertUnique("category-service.categories", categories, objectId, "_id");
assertUnique("category-service.categories", categories, (category) => category.slug, "slug");

for (const category of categories) {
  if (category.parentId && !categoryIds.has(category.parentId.$oid)) {
    failures.push(`category-service.categories has unknown parentId: ${category.parentId.$oid}`);
  }
  await assertAssetExists("category-service.categories", category.imageUrl);
}

assertUnique("product-service.products", products, objectId, "_id");
assertUnique("product-service.products", products, (product) => product.slug, "slug");

for (const product of products) {
  if (!categoryIds.has(product.categoryId.$oid)) {
    failures.push(`product-service.products ${product.slug} has unknown categoryId: ${product.categoryId.$oid}`);
  }
  if (!categorySlugs.has(product.categorySlug)) {
    failures.push(`product-service.products ${product.slug} has unknown categorySlug: ${product.categorySlug}`);
  }
  for (const imagePath of product.imageUrls ?? []) {
    await assertAssetExists("product-service.products", imagePath);
  }
}

for (const collection of collections) {
  const documents = documentsByCollection.get(collection) ?? [];
  assertUnique(collection, documents, objectId, "_id");
}

if (failures.length > 0) {
  console.error(`Sample data validation failed with ${failures.length} issue(s):`);
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

const totalDocuments = [...documentsByCollection.values()].reduce((total, documents) => total + documents.length, 0);
console.log(`Validated ${totalDocuments} sample document(s) across ${collections.length} collection schema(s).`);
