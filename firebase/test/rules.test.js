import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-inkless',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const alice = () => env.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
const bob = () => env.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
const nobody = () => env.unauthenticatedContext().firestore();

// What the app does on sign-up: claim the name and create the profile in one go.
function claim(db, uid, email, username) {
  const batch = writeBatch(db);
  batch.set(doc(db, 'usernames', username), { uid, email });
  batch.set(doc(db, 'users', uid), { username, email, createdAt: serverTimestamp() });
  return batch.commit();
}

async function seedAlice() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'usernames', 'alice'), { uid: 'alice', email: 'alice@example.com' });
    await setDoc(doc(db, 'users', 'alice'), { username: 'alice', email: 'alice@example.com' });
  });
}

describe('usernames', () => {
  test('a free username can be claimed along with the profile', async () => {
    await assertSucceeds(claim(alice(), 'alice', 'alice@example.com', 'alice'));
  });

  test('anyone can look up one username, even signed out', async () => {
    await seedAlice();
    await assertSucceeds(getDoc(doc(nobody(), 'usernames', 'alice')));
  });

  test('nobody can list usernames', async () => {
    await seedAlice();
    await assertFails(getDocs(collection(nobody(), 'usernames')));
    await assertFails(getDocs(collection(bob(), 'usernames')));
  });

  test('a taken username cannot be claimed again', async () => {
    await seedAlice();
    await assertFails(claim(bob(), 'bob', 'bob@example.com', 'alice'));
  });

  test('a claim cannot be made without the profile', async () => {
    await assertFails(setDoc(doc(alice(), 'usernames', 'alice'), { uid: 'alice', email: 'alice@example.com' }));
  });

  test('a claim cannot point at another user', async () => {
    const db = bob();
    const batch = writeBatch(db);
    batch.set(doc(db, 'usernames', 'alice'), { uid: 'alice', email: 'alice@example.com' });
    batch.set(doc(db, 'users', 'alice'), { username: 'alice', email: 'alice@example.com', createdAt: serverTimestamp() });
    await assertFails(batch.commit());
  });

  test("a claim must carry the account's own email", async () => {
    await assertFails(claim(alice(), 'alice', 'someone-else@example.com', 'alice'));
  });

  test('badly formed usernames are rejected', async () => {
    await assertFails(claim(alice(), 'alice', 'alice@example.com', 'Alice'));
    await assertFails(claim(alice(), 'alice', 'alice@example.com', 'al'));
    await assertFails(claim(alice(), 'alice', 'alice@example.com', 'alice.patil'));
  });

  test('extra fields are rejected', async () => {
    const db = alice();
    const batch = writeBatch(db);
    batch.set(doc(db, 'usernames', 'alice'), { uid: 'alice', email: 'alice@example.com', admin: true });
    batch.set(doc(db, 'users', 'alice'), { username: 'alice', email: 'alice@example.com', createdAt: serverTimestamp() });
    await assertFails(batch.commit());
  });

  test('one account gets one username', async () => {
    await seedAlice();
    await assertFails(claim(alice(), 'alice', 'alice@example.com', 'alice_two'));
  });

  test('claims are never updated', async () => {
    await seedAlice();
    await assertFails(updateDoc(doc(alice(), 'usernames', 'alice'), { email: 'new@example.com' }));
  });

  test('the owner frees the name together with the profile', async () => {
    await seedAlice();
    const db = alice();
    const batch = writeBatch(db);
    batch.delete(doc(db, 'usernames', 'alice'));
    batch.delete(doc(db, 'users', 'alice'));
    await assertSucceeds(batch.commit());
  });

  test('the name cannot be freed while the profile still uses it', async () => {
    await seedAlice();
    await assertFails(deleteDoc(doc(alice(), 'usernames', 'alice')));
  });

  test("nobody else can free someone's name", async () => {
    await seedAlice();
    await assertFails(deleteDoc(doc(bob(), 'usernames', 'alice')));
  });
});

describe('users', () => {
  test('a profile is readable only by its owner', async () => {
    await seedAlice();
    await assertSucceeds(getDoc(doc(alice(), 'users', 'alice')));
    await assertFails(getDoc(doc(bob(), 'users', 'alice')));
    await assertFails(getDoc(doc(nobody(), 'users', 'alice')));
  });

  test('profiles cannot be listed', async () => {
    await seedAlice();
    await assertFails(getDocs(collection(bob(), 'users')));
  });

  test('the username on a profile cannot change', async () => {
    await seedAlice();
    await assertFails(updateDoc(doc(alice(), 'users', 'alice'), { username: 'bob' }));
    await assertSucceeds(updateDoc(doc(alice(), 'users', 'alice'), { email: 'alice@example.com' }));
  });

  test('a profile cannot be created for someone else', async () => {
    await assertFails(setDoc(doc(bob(), 'users', 'alice'), { username: 'x', email: 'x', createdAt: serverTimestamp() }));
  });

  test('everything under a user is private to them', async () => {
    await seedAlice();
    const ref = (db) => doc(db, 'users', 'alice', 'transactions', 't1');
    await assertSucceeds(setDoc(ref(alice()), { amountPaise: 4500 }));
    await assertSucceeds(getDoc(ref(alice())));
    await assertFails(getDoc(ref(bob())));
    await assertFails(setDoc(ref(bob()), { amountPaise: 1 }));
    await assertFails(getDocs(collection(bob(), 'users', 'alice', 'transactions')));
  });

  test('nothing outside users and usernames is open', async () => {
    await assertFails(setDoc(doc(alice(), 'anything', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(alice(), 'anything', 'x')));
  });
});
