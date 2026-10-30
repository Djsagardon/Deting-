const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- TESTS ---

test("Unauthenticated user: cannot read users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create their own profile with valid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      publicUserId: "BD123456",
      displayName: "Alice",
      age: 24,
      accountStatus: "active",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create profile for another user UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      publicUserId: "BD654321",
      displayName: "Bob",
      age: 26,
      accountStatus: "active",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create profile under 18", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      publicUserId: "BD123456",
      displayName: "Alice",
      age: 17,
      accountStatus: "active",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can read other user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      publicUserId: "BD654321",
      displayName: "Bob",
      age: 26,
      accountStatus: "active",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Private subcollection: only owner can read and write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      mobileNumber: "+919876543210",
      verified: true,
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      mobileNumber: "+910000000000",
    })
  );
});

test("Notifications: owner can read; another user can create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Alice can create a notification directed to Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_1").set({
      id: "notif_1",
      userId: BOB_UID,
      title: "New Match!",
      message: "You matched with Alice",
      type: "match",
      isRead: false,
      createdAt: new Date(),
    })
  );

  // Bob can read his notifications
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_1").get()
  );

  // Charlie cannot read Bob's notifications
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_1").get()
  );
});

test("Matches and Messages: participants can read and send", async () => {
  const matchId = "alice_123_bob_456";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("matches").doc(matchId).set({
      id: matchId,
      user1Id: ALICE_UID,
      user2Id: BOB_UID,
      users: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("matches").doc(matchId).get());

  // Alice can send message
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").set({
      id: "msg_1",
      matchId: matchId,
      senderId: ALICE_UID,
      text: "Hello Bob!",
      imageUrl: "",
      status: "sent",
      createdAt: new Date(),
    })
  );

  // Charlie cannot read match or send message
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("matches").doc(matchId).get());
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("messages").doc("msg_2").set({
      id: "msg_2",
      matchId: matchId,
      senderId: CHARLIE_UID,
      text: "Spam",
      imageUrl: "",
      status: "sent",
      createdAt: new Date(),
    })
  );

  // Message deletion: Sender can soft-delete message for everyone
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").update({
      deletedForEveryone: true,
      text: "🚫 This message was deleted",
    })
  );

  // Bob can delete message for himself
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").update({
      deletedForUserIds: [BOB_UID],
    })
  );
});

test("Role Management: regular user cannot promote self; admin can update roles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      accountStatus: "active",
      age: 24,
    });
  });

  // Alice cannot promote herself to admin
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
    })
  );

  // Admin context can promote user to admin
  const adminDb = testEnv.authenticatedContext("admin_user", { admin: true }).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
    })
  );
});

test("Voice/Video Calls: participants can read and update; non-participants denied", async () => {
  const callId = "call_abc_123";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Alice initiates call to Bob
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      callId: callId,
      callerUid: ALICE_UID,
      receiverUid: BOB_UID,
      callerName: "Alice",
      receiverName: "Bob",
      channelName: "bondly_call_call_abc",
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      createdAt: new Date(),
    })
  );

  // Bob can read call and accept
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("calls").doc(callId).get());
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "accepted",
    })
  );

  // Charlie cannot read or tamper with call
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("calls").doc(callId).get());
  await assertFails(
    charlieDb.collection("calls").doc(callId).update({
      status: "declined",
    })
  );
});

test("Safety: Blocks and Reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Alice reports Charlie
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      id: "rep_1",
      reporterUid: ALICE_UID,
      reportedUid: CHARLIE_UID,
      reason: "Inappropriate behavior",
      details: "Spam messages",
      createdAt: new Date(),
    })
  );

  // Alice blocks Charlie
  await assertSucceeds(
    aliceDb.collection("blocks").doc("alice_123_charlie_789").set({
      id: "alice_123_charlie_789",
      blockerUid: ALICE_UID,
      blockedUid: CHARLIE_UID,
      createdAt: new Date(),
    })
  );

  // Charlie cannot read Alice's report or block record
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("reports").doc("rep_1").get());
  await assertFails(charlieDb.collection("blocks").doc("alice_123_charlie_789").get());
});
