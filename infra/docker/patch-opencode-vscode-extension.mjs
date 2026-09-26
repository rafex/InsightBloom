#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const extensionDir = process.argv[2];
if (!extensionDir) {
  console.error("usage: patch-opencode-vscode-extension.mjs EXTENSION_DIRECTORY");
  process.exit(2);
}

const manifestPath = path.join(extensionDir, "package.json");
const bundlePath = path.join(extensionDir, "dist", "extension.js");
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
if (manifest.publisher !== "sst-dev" || manifest.name !== "opencode-v2" || manifest.version !== "0.1.1") {
  throw new Error(`Unexpected OpenCode extension package: ${manifest.publisher}.${manifest.name}@${manifest.version}`);
}

let bundle = fs.readFileSync(bundlePath, "utf8");
const marker = 'o.startsWith("opencode server listening")';
const occurrences = bundle.split(marker).length - 1;
if (occurrences !== 1) {
  throw new Error(`Expected one occurrence of OpenCode compatibility marker, found ${occurrences}`);
}
bundle = bundle.replace(marker, 'o.startsWith("server listening")');

const passwordPrelude = `;(()=>{const password=process.env.OPENCODE_SERVER_PASSWORD||require("node:crypto").randomBytes(32).toString("base64url");process.env.OPENCODE_SERVER_PASSWORD=password;const authorization="Basic "+Buffer.from("opencode:"+password).toString("base64");const nativeFetch=globalThis.fetch.bind(globalThis);globalThis.fetch=(input,init={})=>{let url;try{url=new URL(input instanceof Request?input.url:input)}catch{}if(url?.origin==="http://localhost:4096"){const aliases={"/health":"/global/health","/app/providers":"/config/providers"};const targetPath=aliases[url.pathname];if(targetPath){url.pathname=targetPath;input=input instanceof Request?new Request(url.href,input):url.href}const headers=new Headers(input instanceof Request?input.headers:undefined);new Headers(init.headers).forEach((value,key)=>headers.set(key,value));headers.set("Authorization",authorization);init={...init,headers}}return nativeFetch(input,init)}})();\n`;
if (bundle.startsWith(";(()=>{const password=")) {
  throw new Error("OpenCode extension bundle is already patched; refusing to patch twice");
}
bundle = passwordPrelude + bundle;

fs.writeFileSync(bundlePath, bundle);
console.log("Patched OpenCode v2 extension: updated serve marker, v2 API routes, and localhost Basic auth");
