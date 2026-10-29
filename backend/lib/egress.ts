import { EgressClient } from "livekit-server-sdk";

export function getEgressClient() {
  const url = (process.env.LIVEKIT_URL || "ws://localhost:7880").replace(/^ws/, "http");
  const httpUrl = url.replace("wss", "https").replace("ws", "http");
  return new EgressClient(httpUrl, process.env.LIVEKIT_API_KEY || "devkey", process.env.LIVEKIT_API_SECRET || "secret");
}