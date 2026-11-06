# Bondly Secure Image Uploader (Cloudflare Worker)

This backend proxy allows Bondly to securely upload images to **ImgBB** without exposing your ImgBB API key inside the Android application, APK, BuildConfig, or GitHub repository.

## Architecture

```
[Bondly Android App]
        │
        │ 1. POST /upload with Firebase ID Token & Image
        ▼
[Cloudflare Worker]
        │ 2. Verifies Firebase Auth JWT (dating-buddy-b3581)
        │ 3. Validates file size & format
        │ 4. Adds IMGBB_API_KEY from Cloudflare Secret
        ▼
   [ImgBB API]
        │ 5. Saves image
        ▼
[Cloudflare Worker]
        │ 6. Returns clean image URL (without delete tokens)
        ▼
[Bondly Android App]
        │ 7. Saves image URL into Firestore user profile
```

---

## Deployment Instructions

### Prerequisites
Make sure you have Node.js (v18+) and npm installed on your machine.

### Step 1: Install Wrangler CLI
Open a terminal and install Cloudflare's official CLI:
```bash
npm install -g wrangler
```

### Step 2: Authenticate with Cloudflare
Log in to your Cloudflare account:
```bash
wrangler login
```
*(A browser window will open asking you to authorize Wrangler. Click **Allow**.)*

### Step 3: Add Your ImgBB API Key as a Worker Secret
Navigate to this directory:
```bash
cd backend/cloudflare-worker
```

Run this command to securely set your secret:
```bash
wrangler secret put IMGBB_API_KEY
```
When prompted:
```
Enter a secret value: ********************************
```
Paste your real **ImgBB API Key** and press **Enter**.

> **Important**: This secret is encrypted on Cloudflare's edge servers. It is **never** checked into Git or exposed to the Android client.

### Step 4: Deploy the Worker
Deploy the worker with one command:
```bash
wrangler deploy
```

You will see output similar to:
```
Uploaded bondly-image-uploader (1.23 sec)
Published bondly-image-uploader (0.45 sec)
  https://bondly-image-uploader.<your-subdomain>.workers.dev
```

### Step 5: Test the Endpoint
Run a health check in your browser or with curl:
```bash
curl https://bondly-image-uploader.<your-subdomain>.workers.dev/health
```
Expected response:
```json
{"status":"healthy","service":"bondly-image-uploader"}
```

The upload endpoint will be:
`https://bondly-image-uploader.<your-subdomain>.workers.dev/upload`

---

## Android App Integration
The Bondly Android app uses `ImageUploadRepository.kt`, which communicates with this endpoint.
You can configure the endpoint URL in the app's `ImageUploadRepository.kt` or `.env` file:

```kotlin
const val CLOUDFLARE_WORKER_URL = "https://bondly-image-uploader.<your-subdomain>.workers.dev/upload"
```
