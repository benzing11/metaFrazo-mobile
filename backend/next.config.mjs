import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Pin the tracing root to this app so Next.js ignores unrelated lockfiles
  // higher up the tree (e.g. ~/package-lock.json) when inferring the workspace.
  outputFileTracingRoot: __dirname,
};

export default nextConfig;
