# Zenith Studio — Product Demo Script & Voice-Over Guide

This document contains the complete production script, visual choreography, and narration timings for the official **Zenith Studio** walkthrough video (`demo/zenith-studio-demo.mp4`).

---

## 🎬 Production Notes

- **Tone**: Modern, confident, developer-focused, clear and unhurried.
- **Pacing**: Conversational with natural pauses between statements.
- **Resolution**: 1080p Full HD (1920 × 1080), 30 fps.
- **Audio Mix**:
  - Voice narration at 100% volume with gentle broadcast compression.
  - Ambient synthesizer pad in C minor / E♭ major ducked to 14% (-17 dB) underneath narration.

---

## ⏱️ Scene Breakdown & Timestamps

| Scene | Time | Visual Action / Camera Motion | On-Screen Caption | Voice-Over Narration |
|---|---|---|---|---|
| **01 · Intro** | 0:00 – 0:04 | Animated dark title card with glowing Zenith brand mark, tagline, and output badge pills (`Spring Boot 3`, `TypeScript SDK`, `Docker`). | — | *"What if your entire backend fit in a single file?"* |
| **02 · Overview** | 0:04 – 0:12 | Studio split-screen overview. Left: Monaco code editor with Zenith DSL. Right: 2-column Schema ERD with relationship connectors. Subtle slow zoom. | `ZENITH STUDIO`<br>**Describe your data once. Zenith builds the backend.** | *"This is Zenith Studio. You describe your data once, and Zenith builds the backend around it."* |
| **03 · Model** | 0:12 – 0:21 | Camera zooms onto code editor. Live typing adds `model OrderItem` with typed fields, constraints, and many-to-one relations. | `1 · MODEL`<br>**A model is a few lines: types, validation, relations.** | *"Here I add an order item: a quantity, a price, and links to an order and a product. That's the whole definition."* |
| **04 · Live ERD** | 0:21 – 0:28 | Live compilation triggers as code is typed. Camera pans to Schema ERD. The `OrderItem` card renders live with visual badges and relationship connectors to `Order` and `Product`. | `2 · LIVE DIAGRAM`<br>**It compiles as you type. The diagram updates live.** | *"It compiles as I type, and the schema diagram updates instantly, relations included."* |
| **05 · Real Code** | 0:28 – 0:39 | Switches to **Repository Tree** tab. File tree displays generated project structure. Clicks `OrderController.java`. Monaco viewer highlights pure Spring Boot 3 Java code. | `3 · REAL CODE`<br>**Underneath: a real Spring Boot 3 project you own.** | *"Underneath is a real Spring Boot 3 project: entities, DTOs, services, controllers and security. Plain Java you can read and keep."* |
| **06 · API Tester** | 0:39 – 0:46 | Switches to **API Tester** tab. Displays automatically generated interactive REST endpoints: `GET`, `POST`, pagination params, and query filters. | `4 · TEST`<br>**Every endpoint is ready to try: filters and pagination included.** | *"Every endpoint is ready to try, with validation, filters and pagination."* |
| **07 · TypeScript** | 0:46 – 0:53 | Switches to **TypeScript SDK** tab. Displays typed interfaces and client methods (`client.ts`), ready for React/Next.js/Vue frontends. | `5 · SHIP`<br>**Plus a typed TypeScript SDK and a Docker setup.** | *"You also get a typed TypeScript client and a Docker setup, from the same file."* |
| **08 · Run App** | 0:53 – 0:57 | Switches to **Console** tab. The app compiles and boots with Spring Boot banner: *Started ShopSphere in 2.14 seconds*. | `6 · RUN`<br>**One click builds and boots the app.** | *"And one click builds and starts the app."* |
| **09 · Outro** | 0:57 – 1:02 | Sleek dark outro card with animated terminal command `$ zenith studio` and tech stack badges. | — | *"Zenith. One schema, a full backend."* |

---

## 🎙️ Voice-Over Text (Copy-Paste for Custom Voice Recording)

If you wish to re-record with your own voice or generate high-fidelity AI speech (e.g. ElevenLabs, OpenAI Audio, or Murf):

```text
What if your entire backend fit in a single file?

This is Zenith Studio. You describe your data once, and Zenith builds the backend around it.

Here I add an order item: a quantity, a price, and links to an order and a product. That's the whole definition.

It compiles as I type, and the schema diagram updates instantly, relations included.

Underneath is a real Spring Boot 3 project: entities, DTOs, services, controllers and security. Plain Java you can read and keep.

Every endpoint is ready to try, with validation, filters and pagination.

You also get a typed TypeScript client and a Docker setup, from the same file.

And one click builds and starts the app.

Zenith. One schema, a full backend.
```

---

## 🛠️ Re-generating the Demo Video

To rebuild the demo video from scratch:

```bash
# 1. Start the Studio daemon in the background
.\bin\zenith.bat studio

# 2. Run the automated capture engine (captures CDP frames from headless Chrome)
node demo/build/record.js

# 3. Assemble clips, apply pan/zoom motion, mix audio & music
node demo/build/compose.js
```

The output file is written to `demo/zenith-studio-demo.mp4`.

