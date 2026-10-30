import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";

const AI = process.env.AI_SERVICE_BASE_URL || "http://localhost:8000";

export async function POST(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const form = await req.formData();
  const file = form.get("file") as File | null;
  const targetLang = (form.get("target_lang") as string) || "en";
  // Language of the text on the shared screen (slides), independent of the speaker.
  const sourceLang = (form.get("source_lang") as string) || "en";
  if (!file) return NextResponse.json({ error: "missing file" }, { status: 400 });

  const fd = new FormData();
  fd.append("file", file);
  fd.append("language", sourceLang);
  const ocrRes = await fetch(`${AI}/ocr`, { method: "POST", body: fd });
  let regions: any[] = [];
  if (ocrRes.ok) {
    try {
      const ocr = await ocrRes.json();
      regions = Array.isArray(ocr.regions) ? ocr.regions.map((rg: any) => rg) : [];
    } catch {
      regions = [];
    }
  } else {
    // OCR not available on the AI service; return empty rather than throwing.
    console.warn("AI service /ocr unavailable:", ocrRes.status);
  }

  // translate each region text per target language
  const outRegions = [];
  for (const rg of regions) {
    let translated = rg.text;
    if (sourceLang !== targetLang) {
      try {
        const tr = await fetch(`${AI}/translate`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: rg.text, source_lang: sourceLang, target_lang: targetLang }),
        });
        const tj = await tr.json();
        translated = tj.translated_text || rg.text;
      } catch { }
    }
    outRegions.push({ ...rg, translated });
  }

  return NextResponse.json({ source_lang: sourceLang, target_lang: targetLang, regions: outRegions });
}