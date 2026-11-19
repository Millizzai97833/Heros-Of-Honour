const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_commander_1";
const BOB_UID = "bob_commander_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    callsign: "Shahab",
    coins: 1000,
    xp: 0,
    level: 1,
    skillPoints: 2,
    eloRating: 1000,
    wins: 0,
    matchesPlayed: 0,
    loginStreak: 0,
    lastDailyClaimDay: "",
    unlockedSkins: ["skin_olive"],
    unlockedWeapons: ["wpn_tank_120", "muz_std", "opt_holo", "arm_composite", "amm_apfsds"],
    unlockedSkills: [],
    completedMissions: [],
    equippedSkin: "skin_olive",
    equippedTheatre: "TANK",
    equippedWeapon: "wpn_tank_120",
    equippedMuzzle: "muz_std",
    equippedOptic: "opt_holo",
    equippedArmor: "arm_composite",
    equippedAmmo: "amm_apfsds",
    graphicsQuality: "HIGH",
    integrityScore: 100,
    adCooldownEpochSec: 0,
    createdAt: ts,
    updatedAt: ts,
  };
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("match_rooms").where("status", "==", "WAITING").get());
  await assertFails(unauthDb.collection("leaderboard").where("verified", "==", true).get());
});

test("Player profile: owner create, partial update, anti-cheat coin cap, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  // Valid partial update (e.g. daily login reward)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 1300,
      loginStreak: 1,
      lastDailyClaimDay: "2026-11-19",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Anti-cheat rejection: massive coin jump (> 5000 in one update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 99999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (Shadow Update Test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ coins: 0 }));
});

test("Match logs: owner can append valid audit log and list; update/delete forbidden", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("match_logs").doc("log_1").set({
      mode: "CAMPAIGN",
      theatre: "TANK",
      missionId: "mis_tank_iron",
      kills: 5,
      damageDealt: 1200,
      shotsFired: 20,
      accuracyPct: 75,
      durationSec: 45,
      integrityPassed: true,
      cheatFlags: [],
      telemetryChecksum: "A1B2C3D4E5F67890",
      coinsAwarded: 450,
      xpAwarded: 350,
      createdAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("match_logs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("match_logs").get());

  // Append-only: updates/deletes must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("match_logs").doc("log_1").update({ kills: 99 })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("match_logs").doc("log_1").delete()
  );
});

test("Multiplayer match_rooms: host create, filtered list, guest join, telemetry sync, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("match_rooms").doc("room_1").set({
      hostUid: ALICE_UID,
      hostName: "Alice",
      guestUid: "",
      guestName: "",
      memberUids: [ALICE_UID],
      theatre: "TANK",
      weather: "RAIN",
      status: "WAITING",
      hostScore: 0,
      guestScore: 0,
      hostHp: 220,
      guestHp: 220,
      hostX: 0.0,
      hostZ: -45.0,
      hostYaw: 0.0,
      guestX: 0.0,
      guestZ: 45.0,
      guestYaw: 180.0,
      winnerUid: "",
      antiCheatHash: "VERIFIED123",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Filtered lobby query succeeds; unfiltered query fails
  await assertSucceeds(bobDb.collection("match_rooms").where("status", "==", "WAITING").get());
  await assertFails(bobDb.collection("match_rooms").get());

  // Guest joins room
  await assertSucceeds(
    bobDb.collection("match_rooms").doc("room_1").update({
      guestUid: BOB_UID,
      guestName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Host syncs live 3D coordinates
  await assertSucceeds(
    aliceDb.collection("match_rooms").doc("room_1").update({
      hostX: 12.5,
      hostZ: -18.0,
      hostYaw: 45.0,
      hostHp: 195,
      hostScore: 200,
      antiCheatHash: "HASH998877",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Finish match (terminal state lock)
  await assertSucceeds(
    aliceDb.collection("match_rooms").doc("room_1").update({
      status: "FINISHED",
      winnerUid: ALICE_UID,
      antiCheatHash: "FINALHASH",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Subsequent mutation after FINISHED must fail
  await assertFails(
    bobDb.collection("match_rooms").doc("room_1").update({
      guestScore: 500,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Leaderboard: filtered verified query succeeds; unfiltered fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      callsign: "Alice",
      eloRating: 1250,
      wins: 4,
      level: 3,
      favouriteTheatre: "AIRCRAFT",
      verified: true,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("leaderboard").where("verified", "==", true).get());
  await assertFails(bobDb.collection("leaderboard").get());
});
