#!/usr/bin/env node

/**
 * Bondly Administrator Role Management Script
 *
 * Usage:
 *   node scripts/manage-roles.js --email user@example.com --role admin
 *   node scripts/manage-roles.js --uid <FIREBASE_UID> --role user
 *
 * Prerequisites:
 *   Ensure GOOGLE_APPLICATION_CREDENTIALS points to your service account key,
 *   or run on a machine authorized with `gcloud auth application-default login`.
 */

const admin = require('firebase-admin');

if (!admin.apps.length) {
  try {
    admin.initializeApp();
  } catch (e) {
    console.error('Failed to initialize Firebase Admin SDK. Please ensure GOOGLE_APPLICATION_CREDENTIALS or gcloud credentials are configured.');
    process.exit(1);
  }
}

const db = admin.firestore();
const auth = admin.auth();

async function main() {
  const args = process.argv.slice(2);
  let targetEmail = null;
  let targetUid = null;
  let role = null;

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--email' && args[i + 1]) targetEmail = args[++i];
    if (args[i] === '--uid' && args[i + 1]) targetUid = args[++i];
    if (args[i] === '--role' && args[i + 1]) role = args[++i].toLowerCase();
  }

  if ((!targetEmail && !targetUid) || !role) {
    console.log(`
=============================================================
  Bondly Administrator Role Management Utility
=============================================================
Usage:
  node scripts/manage-roles.js --email <EMAIL> --role <admin|user>
  node scripts/manage-roles.js --uid <UID> --role <admin|user>

Examples:
  node scripts/manage-roles.js --email ckdbengalhoney@gmail.com --role admin
  node scripts/manage-roles.js --uid 7VfG31bNxM... --role user
=============================================================
`);
    process.exit(1);
  }

  if (role !== 'admin' && role !== 'user') {
    console.error('Error: Role must be either "admin" or "user".');
    process.exit(1);
  }

  try {
    let userRecord;
    if (targetEmail) {
      console.log(`Looking up user by email: ${targetEmail}...`);
      userRecord = await auth.getUserByEmail(targetEmail);
    } else {
      console.log(`Looking up user by UID: ${targetUid}...`);
      userRecord = await auth.getUser(targetUid);
    }

    const uid = userRecord.uid;
    const email = userRecord.email || 'N/A';
    const isAdmin = role === 'admin';

    console.log(`Found account: ${userRecord.displayName || 'Unnamed'} (${email}) [UID: ${uid}]`);
    console.log(`Setting Firebase Custom Claims: { admin: ${isAdmin} }...`);
    await auth.setCustomUserClaims(uid, { admin: isAdmin });

    console.log(`Synchronizing Firestore users/${uid}.role = '${role}'...`);
    await db.collection('users').document(uid).set(
      {
        role: role,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    console.log(`Logging action to admin_audit_logs...`);
    await db.collection('admin_audit_logs').add({
      action: 'CLI_SET_ROLE',
      targetUid: uid,
      targetEmail: email,
      newRole: role,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log(`\n✅ SUCCESS: Account ${email} (${uid}) updated to '${role}'.`);
    console.log(`Note: If the user is currently logged in, the new role claim will take effect immediately upon their next token refresh or after re-signing in.\n`);
    process.exit(0);
  } catch (err) {
    console.error('Failed to update role:', err.message);
    process.exit(1);
  }
}

main();
