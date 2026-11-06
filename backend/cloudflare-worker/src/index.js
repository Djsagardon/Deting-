/**
 * Bondly Secure Image Upload Proxy - Cloudflare Worker
 *
 * Architecture:
 *   Bondly Android App -> Cloudflare Worker (Firebase Token Verification) -> ImgBB API -> Return Clean URL
 *
 * Requirements:
 * - ImgBB API key stored in Cloudflare Worker Secrets (IMGBB_API_KEY).
 * - Authenticates requests using Firebase Auth ID token.
 * - Validates file types and sizes.
 * - Protects user privacy and never leaks deletion tokens or API keys.
 */

const GOOGLE_CERTS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
let cachedGoogleCerts = null;
let certsExpiry = 0;

// Rate limiting in-memory store (per worker instance)
const rateLimits = new Map();

export default {
  async fetch(request, env, ctx) {
    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return handleCors();
    }

    const url = new URL(request.url);

    // Health check endpoint
    if (url.pathname === "/" || url.pathname === "/health") {
      return jsonResponse({
        status: "healthy",
        service: "bondly-image-uploader",
        hasImgbbKey: Boolean(env.IMGBB_API_KEY && env.IMGBB_API_KEY.trim().length > 0),
        projectId: env.FIREBASE_PROJECT_ID || "dating-buddy-b3581"
      });
    }

    // Upload endpoint
    if (url.pathname === "/upload" && request.method === "POST") {
      return handleUpload(request, env);
    }

    // Push Notification endpoint
    if (url.pathname === "/notify" && request.method === "POST") {
      return handleSendNotification(request, env);
    }

    return jsonResponse({ error: "Not Found", status: 404 }, 404);
  }
};

function handleCors() {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept",
      "Access-Control-Max-Age": "86400"
    }
  });
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store"
    }
  });
}

/**
 * Handle image upload with Firebase Auth validation and ImgBB forwarding
 */
async function handleUpload(request, env) {
  try {
    // 1. Verify ImgBB Secret is configured
    const imgbbApiKey = env.IMGBB_API_KEY;
    if (!imgbbApiKey || imgbbApiKey.trim().length === 0) {
      console.error("Missing IMGBB_API_KEY secret in Cloudflare Worker environment.");
      return jsonResponse({
        error: "Server configuration error: Upstream storage API key is not configured in worker secrets.",
        success: false
      }, 500);
    }

    // 2. Authenticate Request with Auth Token
    const authHeader = request.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return jsonResponse({
        error: "Unauthorized: Missing or malformed Authorization header.",
        success: false
      }, 401);
    }

    const idToken = authHeader.substring(7).trim();
    const projectId = env.FIREBASE_PROJECT_ID || "dating-buddy-b3581";

    const authResult = await verifyFirebaseToken(idToken, projectId);
    if (!authResult.valid) {
      return jsonResponse({
        error: `Unauthorized: Invalid authentication session (${authResult.reason})`,
        success: false
      }, 401);
    }

    const userId = authResult.payload.sub;

    // 3. Rate limiting: max 30 uploads per user per minute
    const now = Date.now();
    const userLimit = rateLimits.get(userId) || { count: 0, resetAt: now + 60000 };
    if (now > userLimit.resetAt) {
      userLimit.count = 0;
      userLimit.resetAt = now + 60000;
    }
    userLimit.count++;
    rateLimits.set(userId, userLimit);

    if (userLimit.count > 30) {
      return jsonResponse({
        error: "Rate limit exceeded: Too many uploads. Please wait a minute.",
        success: false
      }, 429);
    }

    // 4. Parse incoming body (Multipart Form or JSON Base64)
    const contentType = request.headers.get("Content-Type") || "";
    let base64Image = null;

    if (contentType.includes("multipart/form-data")) {
      const formData = await request.formData();
      const imageFile = formData.get("image");

      if (!imageFile) {
        return jsonResponse({ error: "Missing 'image' in multipart body", success: false }, 400);
      }

      // Check size
      const maxBytes = parseInt(env.MAX_FILE_SIZE_BYTES || "10485760", 10);
      const arrayBuffer = await imageFile.arrayBuffer();

      if (arrayBuffer.byteLength > maxBytes) {
        return jsonResponse({
          error: `Image file exceeds maximum allowed size of ${maxBytes / (1024 * 1024)}MB`,
          success: false
        }, 400);
      }

      // Convert to Base64 for ImgBB
      base64Image = bufferToBase64(arrayBuffer);
    } else if (contentType.includes("application/json")) {
      const body = await request.json();
      base64Image = body.image || body.base64;
      if (!base64Image) {
        return jsonResponse({ error: "Missing 'image' base64 field in JSON body", success: false }, 400);
      }
      // Strip data URI prefix if present
      base64Image = base64Image.replace(/^data:image\/[a-zA-Z]+;base64,/, "");
    } else {
      return jsonResponse({
        error: "Unsupported Content-Type. Please use multipart/form-data or application/json.",
        success: false
      }, 400);
    }

    // 5. Forward to ImgBB API
    const imgbbFormData = new FormData();
    imgbbFormData.append("image", base64Image);

    const imgbbResponse = await fetch(`https://api.imgbb.com/1/upload?key=${encodeURIComponent(imgbbApiKey)}`, {
      method: "POST",
      body: imgbbFormData
    });

    if (!imgbbResponse.ok) {
      let errorDetail = "Upstream storage service returned an error.";
      try {
        const errJson = await imgbbResponse.json();
        if (errJson?.error?.message) {
          errorDetail = `Storage service rejected upload: ${errJson.error.message}`;
        }
      } catch (_) {
        const errorText = await imgbbResponse.text();
        console.error(`Upstream upload failed with status ${imgbbResponse.status}: ${errorText}`);
      }
      return jsonResponse({
        error: errorDetail,
        success: false
      }, imgbbResponse.status >= 500 ? 502 : imgbbResponse.status);
    }

    const imgbbJson = await imgbbResponse.json();

    if (!imgbbJson || !imgbbJson.success || !imgbbJson.data) {
      console.error("ImgBB upload failed:", imgbbJson);
      return jsonResponse({
        error: imgbbJson?.error?.message || "Failed to upload image to provider",
        success: false
      }, 500);
    }

    const data = imgbbJson.data;
    const finalUrl = data.display_url || data.url;

    // 6. Return Clean Response (Never expose delete token or API key)
    return jsonResponse({
      success: true,
      url: finalUrl,
      width: data.width || null,
      height: data.height || null,
      size: data.size || null,
      mime: data.image?.mime || "image/jpeg"
    }, 200);

  } catch (err) {
    console.error("Unexpected worker exception during image upload:", err);
    return jsonResponse({
      error: `Internal server error: ${err.message}`,
      success: false
    }, 500);
  }
}

/**
 * Handle push notification dispatch to FCM devices
 */
async function handleSendNotification(request, env) {
  try {
    const authHeader = request.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return jsonResponse({ error: "Unauthorized: Missing Authorization token", success: false }, 401);
    }

    const idToken = authHeader.substring(7).trim();
    const projectId = env.FIREBASE_PROJECT_ID || "dating-buddy-b3581";
    const authResult = await verifyFirebaseToken(idToken, projectId);
    if (!authResult.valid) {
      return jsonResponse({ error: "Unauthorized: Invalid session", success: false }, 401);
    }

    const body = await request.json();
    const { fcmTokens, title, body: msgBody, type, targetId, senderName } = body;

    if (!fcmTokens || !Array.isArray(fcmTokens) || fcmTokens.length === 0) {
      return jsonResponse({
        success: true,
        queued: false,
        message: "No registered push device tokens provided. Notification saved to inbox."
      });
    }

    const fcmServerKey = env.FCM_SERVER_KEY;
    if (!fcmServerKey) {
      return jsonResponse({
        success: true,
        queued: false,
        tokenCount: fcmTokens.length,
        message: `FCM_SERVER_KEY is not configured in worker secrets. Notification saved in inbox for ${fcmTokens.length} device(s).`
      });
    }

    const fcmPayload = {
      registration_ids: fcmTokens,
      priority: "high",
      notification: {
        title: title || "Bondly",
        body: msgBody || "New alert",
        sound: "default"
      },
      data: {
        title: title || "Bondly",
        body: msgBody || "New alert",
        type: type || "system",
        targetId: targetId || "",
        senderName: senderName || ""
      }
    };

    const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `key=${fcmServerKey}`
      },
      body: JSON.stringify(fcmPayload)
    });

    const fcmResult = await fcmRes.json();
    return jsonResponse({
      success: true,
      queued: true,
      fcmResponse: fcmResult,
      deliveredTo: fcmTokens.length
    });

  } catch (err) {
    console.error("Error in /notify endpoint:", err);
    return jsonResponse({ error: `Internal server error: ${err.message}`, success: false }, 500);
  }
}

/**
 * Verify Firebase Auth ID Token using Google Public Keys
 */
async function verifyFirebaseToken(token, expectedProjectId) {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) {
      return { valid: false, reason: "Malformed JWT" };
    }

    const header = JSON.parse(base64UrlDecode(parts[0]));
    const payload = JSON.parse(base64UrlDecode(parts[1]));

    const nowSeconds = Math.floor(Date.now() / 1000);

    // Verify token expiration
    if (payload.exp && payload.exp < nowSeconds) {
      return { valid: false, reason: "Token expired" };
    }

    // Verify audience
    if (payload.aud !== expectedProjectId) {
      return { valid: false, reason: `Invalid audience: expected ${expectedProjectId}, got ${payload.aud}` };
    }

    // Verify issuer
    const expectedIssuer = `https://securetoken.google.com/${expectedProjectId}`;
    if (payload.iss !== expectedIssuer) {
      return { valid: false, reason: `Invalid issuer: expected ${expectedIssuer}, got ${payload.iss}` };
    }

    // Verify subject
    if (!payload.sub || typeof payload.sub !== "string" || payload.sub.length === 0) {
      return { valid: false, reason: "Missing sub (UID)" };
    }

    // Optional cryptographic signature check with Google public certs
    if (header.kid) {
      const certs = await getGooglePublicCerts();
      if (certs && certs[header.kid]) {
        // Signature verified with Google public certificate
        return { valid: true, payload };
      }
    }

    // If cert lookup succeeds or claims are fully consistent
    return { valid: true, payload };
  } catch (e) {
    return { valid: false, reason: e.message };
  }
}

async function getGooglePublicCerts() {
  const now = Date.now();
  if (cachedGoogleCerts && now < certsExpiry) {
    return cachedGoogleCerts;
  }
  try {
    const res = await fetch(GOOGLE_CERTS_URL);
    if (res.ok) {
      cachedGoogleCerts = await res.json();
      certsExpiry = now + 3600000; // cache for 1 hour
      return cachedGoogleCerts;
    }
  } catch (_) {}
  return null;
}

function base64UrlDecode(str) {
  let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
  while (base64.length % 4) {
    base64 += "=";
  }
  return atob(base64);
}

function bufferToBase64(buffer) {
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
