import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({ status: "ok", service: "metafrazo-backend", time: new Date().toISOString() });
}