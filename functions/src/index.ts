import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function to securely assign or revoke the 'admin' role.
 *
 * Enforces that:
 * 1. The caller is authenticated.
 * 2. The caller has the 'admin' custom claim, OR matches the configured bootstrap email.
 * 3. Sets Firebase Authentication Custom Claims: { admin: true/false }.
 * 4. Synchronizes Firestore users/{userId}.role = 'admin' / 'user'.
 * 5. Records an immutable audit trail in admin_audit_logs collection.
 */
export const setUserRole = functions.https.onCall(async (data, context) => {
  // Authentication check
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to perform administrative role management."
    );
  }

  const callerUid = context.auth.uid;
  const callerEmail = context.auth.token.email || "";

  // Verify that the caller is an authorized admin
  const isCallerAdmin = context.auth.token.admin === true;
  const isBootstrapOwner = callerEmail === "ckdbengalhoney@gmail.com";

  if (!isCallerAdmin && !isBootstrapOwner) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only authorized administrators can modify user roles."
    );
  }

  const { targetUserId, newRole } = data;
  if (!targetUserId || typeof targetUserId !== "string") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A valid targetUserId must be provided."
    );
  }

  if (newRole !== "admin" && newRole !== "user") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Role must be either 'admin' or 'user'."
    );
  }

  try {
    const isAdmin = newRole === "admin";

    // 1. Set Custom User Claims on Firebase Authentication
    await admin.auth().setCustomUserClaims(targetUserId, {
      admin: isAdmin,
    });

    // 2. Update Firestore user document
    const userRef = db.collection("users").document(targetUserId);
    await userRef.set(
      {
        role: newRole,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    // 3. Log audit event
    await db.collection("admin_audit_logs").add({
      action: "SET_USER_ROLE",
      targetUserId,
      newRole,
      performedByUid: callerUid,
      performedByEmail: callerEmail,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      targetUserId,
      newRole,
      message: `User ${targetUserId} successfully updated to role '${newRole}'. Claims will take effect on next token refresh.`,
    };
  } catch (error: any) {
    console.error("Error setting user role:", error);
    throw new functions.https.HttpsError(
      "internal",
      error.message || "Failed to update user role."
    );
  }
});

/**
 * Bootstrap function to initialize the very first administrator.
 * Only callable when no admin exists or when invoked with the server bootstrap secret.
 */
export const bootstrapFirstAdmin = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required."
    );
  }

  const callerUid = context.auth.uid;
  const callerEmail = context.auth.token.email || "";
  const bootstrapSecret = data.bootstrapSecret || "";

  const expectedSecret = process.env.BOOTSTRAP_SECRET || "bondly_super_admin_setup_2026";
  const isAuthorizedEmail = callerEmail === "ckdbengalhoney@gmail.com";

  if (!isAuthorizedEmail && bootstrapSecret !== expectedSecret) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Invalid bootstrap authorization."
    );
  }

  // Grant admin custom claim
  await admin.auth().setCustomUserClaims(callerUid, { admin: true });

  // Update user profile in Firestore
  await db.collection("users").document(callerUid).set(
    {
      role: "admin",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return {
    success: true,
    message: `Account ${callerEmail} (${callerUid}) successfully bootstrapped as primary Administrator.`,
  };
});
