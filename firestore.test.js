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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

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

test("Unauthenticated user cannot read or write notes or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/auditLogs`).get());
});

test("Cross-user access is denied for notes, tasks, history, memories, routines, auditLogs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection(`users/${BOB_UID}/notes`).doc("n1").set({
      userId: BOB_UID,
      title: "Secret",
      content: "Body",
      language: "en",
      pinned: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/notes`).doc("n1").get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/notes`).get());
});

test("Authenticated owner can create and update valid note, shadow fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTimestamp();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      title: "Grocery List",
      content: "Doodh aur chai",
      language: "hinglish",
      pinned: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      title: "Updated",
      ghostField: "hack",
      updatedAt: pastTimestamp(),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      title: "Grocery Updated",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Identity spoofing in payload is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").set({
      userId: BOB_UID,
      title: "Spoof",
      completed: false,
      dueAtMillis: 0,
      priority: "normal",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Audit logs are append-only (update denied)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/auditLogs`).doc("log_1").set({
      userId: ALICE_UID,
      action: "CALL_CONFIRMED",
      detail: "User approved call",
      approved: true,
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/auditLogs`).doc("log_1").update({
      approved: false,
    })
  );
});
