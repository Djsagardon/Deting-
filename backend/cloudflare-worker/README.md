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
Paste your real **ImgBB API Key** from [api.imgbb.com](https://api.imgbb.com/) and press **Enter**.

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
{"status":"healthy","service":"bondly-image-uploader","hasImgbbKey":true,"projectId":"dating-buddy-b3581"}
```

The upload endpoint will be:
`https://bondly-image-uploader.<your-subdomain>.workers.dev/upload`

---

## Configuring the Worker URL in Bondly Android App

You have **three convenient ways** to set your deployed Cloudflare Worker URL in the app:

### Option 1: In-App Upload Server Settings (Instant – No Rebuilding Needed!)
1. Open the Bondly app on your Android device.
2. Go to **Manage Photos** (or the Profile screen).
3. Tap the **Cloud / Server Settings icon** in the top right corner.
4. Paste your published Worker URL (e.g. `https://bondly-image-uploader.<your-subdomain>.workers.dev/upload`).
5. Tap **Test Connection (/health)** to verify reachability and that `hasImgbbKey` is true.
6. Tap **Save & Apply**. Your uploads will immediately use this server!

### Option 2: GitHub Repository Secret (For Automatic GitHub Actions Builds)
1. Go to your GitHub Repository -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Click **New repository secret**.
3. Name: `CLOUDFLARE_WORKER_URL`
4. Value: `https://bondly-image-uploader.<your-subdomain>.workers.dev/upload`
5. The GitHub Actions build workflow will automatically inject this URL into your `app-debug.apk` and `app-release.apk`.

### Option 3: Local Gradle Build
When building locally with Gradle:
```bash
./gradlew assembleDebug -PcloudflareWorkerUrl="https://bondly-image-uploader.<your-subdomain>.workers.dev/upload"
```
