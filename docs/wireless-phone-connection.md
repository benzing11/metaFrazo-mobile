# Wireless Phone-to-Phone Connection Guide

MetaFrazo enables wireless real-time communication between multiple Android phones for two-way audio, video, live translated captions, and meeting notes/transcripts.

---

## 1. How It Works

MetaFrazo uses **LiveKit (WebRTC Selective Forwarding Unit - SFU)** for real-time media and state synchronization:

```
 Phone A (Host)                              Phone B (Peer)
[Camera / Mic]                              [Camera / Mic]
[Captions / State]                          [Captions / State]
       │                                           ▲
       ▼                                           │
       └────► WebRTC (Opus Audio + Video) ─────────┘
              (Relayed through LiveKit SFU)
       └────► WebRTC DataChannel (Captions/Sync) ──┘
```

1. **Signaling & Room Creation**:
   - Phone A connects to the backend REST API (`POST /api/meetings`) to create a meeting session with a short code (e.g., `abc123xyz`).
   - The backend creates a LiveKit room and mints a cryptographically signed JWT access token.
2. **Audio & Video (WebRTC Media)**:
   - Voice audio is encoded with Opus and streamed directly over low-latency UDP.
   - Video is transmitted with `adaptiveStream = true` and `dynacast = true`, ensuring bandwidth is conserved by only sending the resolution matching the peer's screen tile.
3. **Captions & Transcript Sync (WebRTC DataChannel)**:
   - Spoken phrases are transcribed locally or via the AI service, translated into the recipient's preferred language, and delivered over LiveKit's reliable WebRTC DataChannel.
   - Messages are targeted per-recipient (`caption_pref`) to minimize data overhead.
   - `CaptionUpsert` deduplicates and updates captions in-place so there is no UI stutter or blanking between partial and final translations.

---

## 2. Setup Scenarios

### Scenario A: Same Local Wi-Fi or Mobile Hotspot (Lowest Latency, < 50ms)

When both phones are in the same room or office:

1. **Network**:
   - Connect both phones to the same Wi-Fi network, **or** have one phone turn on a Personal Mobile Hotspot and connect the second phone to it.
2. **Backend & Media Server Configuration**:
   - Run the MetaFrazo backend and LiveKit on your computer or local server.
   - Find your computer's local LAN IP (e.g., `192.168.1.50`).
   - On **both phones**, open MetaFrazo **Settings > Backend URL** and set:
     ```
     http://192.168.1.50:3000
     ```
   - Ensure the backend's `.env` specifies the LiveKit URL reachable over the LAN:
     ```env
     LIVEKIT_URL=ws://192.168.1.50:7880
     ```
   - *Note:* Do **not** use `127.0.0.1` or `localhost` when connecting physical devices over Wi-Fi, as loopback addresses only refer to the individual device itself.

### Scenario B: Remote / Cross-Network Connection

When phones are on different networks (e.g., Cellular LTE/5G or separate Wi-Fi routers):

1. Use a publicly accessible backend and LiveKit instance (e.g., deployed to an EC2/VPS server with HTTPS/WSS and standard WebRTC ports `50000-60000/udp` open, or LiveKit Cloud).
2. Set the **Backend URL** on both devices to `https://metafrazo.yourdomain.com`.
3. WebRTC ICE will automatically negotiate STUN/TURN traversal to route traffic across NATs with minimal latency.

---

## 3. Step-by-Step Connection Flow

1. **Host Phone (Phone 1)**:
   - Open MetaFrazo and tap **New Meeting**.
   - Your camera and mic will activate, and you will enter the `MeetingRoomScreen`.
   - Tap the meeting code at the top or the **Invite** button to copy the code or share the deep link.
2. **Joining Phone (Phone 2)**:
   - Open MetaFrazo and tap **Join Meeting**.
   - Enter the meeting code from Phone 1 and tap **Join**.
   - (Alternatively, click the shared link `https://<domain>/join/<code\>` to open the meeting automatically).
3. **Connected State**:
   - Both phone streams will appear in the grid layout.
   - Live captions appear in the reader's preferred language at the bottom.
   - When speaking, translations stream across the peer connection instantaneously.

---

## 4. Latency & Reliability Optimizations in MetaFrazo

- **Adaptive Streaming**: Only the thumbnail resolution is transferred over Wi-Fi for multi-participant layouts, reducing bitrate and buffering.
- **Dynacast**: The sender pauses unviewed video layers, preventing congestion on slower Wi-Fi channels.
- **Proactive Language Preference Handshake**: Peers immediately exchange language preferences on `ParticipantConnected` and `Reconnected`, ensuring zero missed caption translations upon network recovery.
- **Progressive Caption Upserting**: Prevents UI redraw jitter and duplicate lines during real-time speech.
- **Foreground Service Keep-Alive**: Prevents Android battery optimizations from killing the peer connection while the screen is locked or during background use.
