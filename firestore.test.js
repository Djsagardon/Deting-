const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-no-project",
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated access to users is denied", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("users").doc("userA").get());
});

test("User A can create and read their own profile", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertSucceeds(
    userADb.collection("users").doc("userA").set({
      userId: "userA",
      displayName: "Alice",
      role: "user",
      accountStatus: "active"
    })
  );
  const snap = await userADb.collection("users").doc("userA").get();
  assert.strictEqual(snap.data().displayName, "Alice");
});

test("User A cannot create a profile with role = admin", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("users").doc("userA").set({
      userId: "userA",
      displayName: "Alice",
      role: "admin",
      accountStatus: "active"
    })
  );
});

test("User A cannot update User B's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("userB").set({
      userId: "userB",
      displayName: "Bob",
      role: "user",
      accountStatus: "active"
    });
  });

  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("users").doc("userB").update({
      displayName: "Hacked"
    })
  );
});

test("User A cannot escalate their own role to admin on update", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("userA").set({
      userId: "userA",
      displayName: "Alice",
      role: "user",
      accountStatus: "active"
    });
  });

  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("users").doc("userA").update({
      role: "admin"
    })
  );
});

test("User A cannot read User B's private notifications", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("userB").collection("notifications").doc("notif1").set({
      id: "notif1",
      userId: "userB",
      title: "Alert",
      message: "Secret info",
      type: "chat"
    });
  });

  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("users").doc("userB").collection("notifications").doc("notif1").get()
  );
});

test("User A cannot create admin_announcement notification to User B", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("users").doc("userB").collection("notifications").doc("notif1").set({
      id: "notif1",
      userId: "userB",
      title: "Fake Admin Message",
      message: "You are banned",
      type: "admin_announcement"
    })
  );
});

test("Admin CAN create admin_announcement notification", async () => {
  const adminDb = testEnv.authenticatedContext("adminUser", { role: "admin" }).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc("userB").collection("notifications").doc("notif1").set({
      id: "notif1",
      userId: "userB",
      title: "Official Bondly Announcement",
      message: "System maintenance tonight",
      type: "admin_announcement"
    })
  );
});

test("Non-matched user cannot read messages in a match", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("matches").doc("match1").set({
      id: "match1",
      user1Id: "userA",
      user2Id: "userB",
      users: ["userA", "userB"]
    });
    await context.firestore().collection("matches").doc("match1").collection("messages").doc("msg1").set({
      id: "msg1",
      matchId: "match1",
      senderId: "userA",
      text: "Hi Bob"
    });
  });

  const userCDb = testEnv.authenticatedContext("userC").firestore();
  await assertFails(
    userCDb.collection("matches").doc("match1").collection("messages").doc("msg1").get()
  );
});

test("Matched user CAN read and send messages in their match", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("matches").doc("match1").set({
      id: "match1",
      user1Id: "userA",
      user2Id: "userB",
      users: ["userA", "userB"]
    });
  });

  const userBDb = testEnv.authenticatedContext("userB").firestore();
  await assertSucceeds(
    userBDb.collection("matches").doc("match1").collection("messages").doc("msg2").set({
      id: "msg2",
      matchId: "match1",
      senderId: "userB",
      text: "Hello Alice!"
    })
  );
});

test("User cannot send message impersonating another sender in match", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("matches").doc("match1").set({
      id: "match1",
      user1Id: "userA",
      user2Id: "userB",
      users: ["userA", "userB"]
    });
  });

  const userBDb = testEnv.authenticatedContext("userB").firestore();
  await assertFails(
    userBDb.collection("matches").doc("match1").collection("messages").doc("msg3").set({
      id: "msg3",
      matchId: "match1",
      senderId: "userA", // User B trying to pretend to be User A!
      text: "I am Alice"
    })
  );
});

test("Non-admin cannot create broadcast", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertFails(
    userADb.collection("broadcasts").doc("bcast1").set({
      broadcastId: "bcast1",
      adminUid: "userA",
      title: "Fake Broadcast",
      message: "Free premium for all",
      targetAudience: "all"
    })
  );
});

test("Admin CAN create broadcast and normal users CAN read it", async () => {
  const adminDb = testEnv.authenticatedContext("adminUser", { role: "admin" }).firestore();
  await assertSucceeds(
    adminDb.collection("broadcasts").doc("bcast1").set({
      broadcastId: "bcast1",
      adminUid: "adminUser",
      title: "Welcome to Bondly",
      message: "Enjoy the app",
      targetAudience: "all"
    })
  );

  const userADb = testEnv.authenticatedContext("userA").firestore();
  const snap = await userADb.collection("broadcasts").doc("bcast1").get();
  assert.strictEqual(snap.data().title, "Welcome to Bondly");
});

test("Normal user cannot view other users moderation reports", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("reports").doc("rep1").set({
      id: "rep1",
      reporterUid: "userA",
      reportedUid: "userB",
      reason: "Harassment",
      status: "pending"
    });
  });

  const userBDb = testEnv.authenticatedContext("userB").firestore();
  await assertFails(
    userBDb.collection("reports").doc("rep1").get()
  );

  const adminDb = testEnv.authenticatedContext("adminUser", { role: "admin" }).firestore();
  await assertSucceeds(
    adminDb.collection("reports").doc("rep1").get()
  );
});

test("Call records can only be read by caller, receiver, or admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("calls").doc("call1").set({
      callId: "call1",
      callerUid: "userA",
      receiverUid: "userB",
      status: "ringing",
      callType: "voice"
    });
  });

  const userCDb = testEnv.authenticatedContext("userC").firestore();
  await assertFails(
    userCDb.collection("calls").doc("call1").get()
  );

  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertSucceeds(
    userADb.collection("calls").doc("call1").get()
  );

  const userBDb = testEnv.authenticatedContext("userB").firestore();
  await assertSucceeds(
    userBDb.collection("calls").doc("call1").get()
  );
});

test("Legacy user document without userId field can still be updated by owner (presence/token)", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("legacyUser1").set({
      displayName: "Pre-Update User",
      publicUserId: "BD112233",
      role: "user",
      accountStatus: "active"
    });
  });

  const legacyDb = testEnv.authenticatedContext("legacyUser1").firestore();
  await assertSucceeds(
    legacyDb.collection("users").doc("legacyUser1").set({
      userId: "legacyUser1",
      isOnline: true
    }, { merge: true })
  );
  // Cannot overwrite an already-assigned publicUserId
  await assertFails(
    legacyDb.collection("users").doc("legacyUser1").update({
      publicUserId: "BD999999"
    })
  );
});
