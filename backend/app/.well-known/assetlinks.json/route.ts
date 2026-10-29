import { NextResponse } from "next/server";

export async function GET() {
  const pkg = process.env.ANDROID_PACKAGE_NAME || "com.metafrazo.app";
  const sha = process.env.ANDROID_SHA256_CERT_FINGERPRINT || "REPLACE_WITH_SHA256";
  const body = [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: { namespace: "android_app", package_name: pkg, sha256_cert_fingerprints: [sha] },
    },
  ];
  return NextResponse.json(body, { headers: { "Content-Type": "application/json" } });
}