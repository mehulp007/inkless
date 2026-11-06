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

  test('the owner can switch backup on and off, and nothing else gets added', async () => {
    await seedAlice();
    const profile = doc(alice(), 'users', 'alice');
    await assertSucceeds(updateDoc(profile, { backup: false }));
    await assertSucceeds(updateDoc(profile, { backup: true }));
    await assertFails(updateDoc(profile, { backup: 'off' }));
    await assertFails(updateDoc(profile, { notes: 'hi' }));
    await assertFails(updateDoc(doc(bob(), 'users', 'alice'), { backup: false }));
  });

  test('a profile cannot be created for someone else', async () => {
    await assertFails(setDoc(doc(bob(), 'users', 'alice'), { username: 'x', email: 'x', createdAt: serverTimestamp() }));
  });

  test('nothing outside users and usernames is open', async () => {
    await assertFails(setDoc(doc(alice(), 'anything', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(alice(), 'anything', 'x')));
  });
});

// A transaction as the app uploads it.
function txn(extra = {}) {
  return {
    amountPaise: 4500, direction: 'DEBIT', type: 'EXPENSE', timestamp: 1, vpa: 'q829154@ybl', payeeRaw: 'Tapri',
    merchantId: 'm1', accountLast4: '1234', bankCode: 'SBI', upiRef: '412345678901', balanceAfterPaise: null,
    categoryId: 'food_canteen', confidence: 0.85, status: 'AUTO', sources: ['SMS', 'NOTIFICATION'], note: null,
    tags: [], createdAt: 1, updatedAt: 2, deleted: false, syncedAt: serverTimestamp(), ...extra,
  };
}

const synced = (data) => ({ ...data, updatedAt: 2, deleted: false, syncedAt: serverTimestamp() });

describe('backup', () => {
  const ref = (db, collectionName = 'transactions', id = 't1') => doc(db, 'users', 'alice', collectionName, id);

  test('only the owner can read, write and delete their backup', async () => {
    await assertSucceeds(setDoc(ref(alice()), txn()));
    await assertSucceeds(getDoc(ref(alice())));
    await assertSucceeds(getDocs(collection(alice(), 'users', 'alice', 'transactions')));
    await assertFails(getDoc(ref(bob())));
    await assertFails(getDocs(collection(bob(), 'users', 'alice', 'transactions')));
    await assertFails(setDoc(ref(bob()), txn()));
    await assertFails(getDoc(ref(nobody())));
    await assertFails(deleteDoc(ref(bob())));
    await assertSucceeds(deleteDoc(ref(alice())));
  });

  test('an edit older than the stored one is turned away', async () => {
    await assertSucceeds(setDoc(ref(alice()), txn({ updatedAt: 5 })));
    await assertFails(setDoc(ref(alice()), txn({ updatedAt: 4, note: 'stale' })));
    await assertSucceeds(setDoc(ref(alice()), txn({ updatedAt: 5 })));
  });

  test('a broken-down payment carries its items', async () => {
    const items = [
      { id: 'i1', amountPaise: 300000, categoryId: 'shopping', note: 'shoes' },
      { id: 'i2', amountPaise: 200000, categoryId: 'college', note: null },
    ];
    await assertSucceeds(setDoc(ref(alice()), txn({ amountPaise: 500000, items })));
    await assertFails(setDoc(ref(alice()), txn({ items: 'shoes' })));
    const tooMany = Array.from({ length: 51 }, (_, i) => ({ id: `i${i}`, amountPaise: 1, categoryId: 'other', note: null }));
    await assertFails(setDoc(ref(alice()), txn({ items: tooMany, updatedAt: 9 })));
  });

  test('a deletion travels as a tombstone', async () => {
    await assertSucceeds(setDoc(ref(alice()), txn()));
    await assertSucceeds(setDoc(ref(alice()), txn({ deleted: true, updatedAt: 3 })));
  });

  test('a transaction with any field beyond the parsed ones is refused', async () => {
    await assertFails(setDoc(ref(alice()), txn({ body: 'Rs.45 debited from A/c XX1234 to q829154@ybl' })));
    await assertFails(setDoc(ref(alice()), txn({ ownerUid: 'alice' })));
  });

  test('account numbers go up as four digits at most', async () => {
    await assertSucceeds(setDoc(ref(alice()), txn({ accountLast4: null })));
    await assertFails(setDoc(ref(alice()), txn({ accountLast4: '50100123451234' })));
  });

  test('the sync time must come from the server', async () => {
    await assertFails(setDoc(ref(alice()), txn({ syncedAt: 1_900_000_000_000 })));
    await assertFails(setDoc(ref(alice()), txn({ amountPaise: '45' })));
  });

  test('categories, merchants, rules, my accounts and budgets hold only their own fields', async () => {
    const db = alice();
    await assertSucceeds(setDoc(ref(db, 'categories', 'food_canteen'), synced({
      name: 'Food & Canteen', emoji: '🍔', colorArgb: 4294090040, monthlyBudgetPaise: null, sortOrder: 0, isSystem: true,
    })));
    await assertSucceeds(setDoc(ref(db, 'merchants', 'm1'), synced({
      vpa: 'q829154@ybl', displayName: 'Tapri', defaultCategoryId: 'food_canteen', timesSeen: 3,
    })));
    await assertSucceeds(setDoc(ref(db, 'rules', 'r1'), synced({
      field: 'UPI_ID', match: 'CONTAINS', pattern: 'canteen', categoryId: 'food_canteen', priority: 1,
    })));
    await assertSucceeds(setDoc(ref(db, 'myAccounts', 'a1'), synced({
      match: 'UPI_ID', value: 'alice@oksbi', role: 'MINE', label: null,
    })));
    await assertSucceeds(setDoc(ref(db, 'budgets', 'monthly'), synced({
      period: 'MONTH', amountPaise: 1200000, allowanceDay: 1, dailyFoodCapPaise: 20000,
    })));
    await assertFails(setDoc(ref(db, 'budgets', 'monthly'), synced({ period: 'MONTH', amountPaise: '12000', allowanceDay: 1 })));
    await assertFails(setDoc(ref(db, 'merchants', 'm2'), synced({ vpa: 'x@ybl', displayName: 'X', timesSeen: 1, sms: 'hi' })));
  });

  test('collections that are not part of the backup are closed', async () => {
    await assertFails(setDoc(ref(alice(), 'rawCaptures', 'c1'), synced({ body: 'Rs.45 debited' })));
    await assertFails(getDoc(ref(alice(), 'rawCaptures', 'c1')));
  });
});

describe('shared splits', () => {
  const split = (extra = {}) => ({
    memberUids: ['alice', 'bob'], payerUid: 'alice', total: 60000,
    shares: { alice: 30000, bob: 30000 }, settled: { alice: true, bob: false },
    note: 'Saturday dinner', createdAt: serverTimestamp(), ...extra,
  });

  async function seedSplit() {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'sharedSplits', 's1'), { ...split(), createdAt: 1 });
    });
  }

  const carol = () => env.authenticatedContext('carol', { email: 'carol@example.com' }).firestore();

  test('the payer creates a split they are part of', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'sharedSplits', 's1'), split()));
  });

  test("nobody creates a split in someone else's name", async () => {
    await assertFails(setDoc(doc(bob(), 'sharedSplits', 's1'), split()));
    await assertFails(setDoc(doc(carol(), 'sharedSplits', 's1'), split({ payerUid: 'carol' })));
  });

  test('only members can read a split', async () => {
    await seedSplit();
    await assertSucceeds(getDoc(doc(alice(), 'sharedSplits', 's1')));
    await assertSucceeds(getDoc(doc(bob(), 'sharedSplits', 's1')));
    await assertFails(getDoc(doc(carol(), 'sharedSplits', 's1')));
    await assertFails(getDoc(doc(nobody(), 'sharedSplits', 's1')));
  });

  test('a member ticks only their own share as settled', async () => {
    await seedSplit();
    await assertSucceeds(updateDoc(doc(bob(), 'sharedSplits', 's1'), { 'settled.bob': true }));
    await assertFails(updateDoc(doc(bob(), 'sharedSplits', 's1'), { 'settled.alice': false }));
    await assertFails(updateDoc(doc(bob(), 'sharedSplits', 's1'), { total: 1 }));
    await assertFails(updateDoc(doc(bob(), 'sharedSplits', 's1'), { 'shares.bob': 0 }));
    await assertFails(updateDoc(doc(carol(), 'sharedSplits', 's1'), { 'settled.carol': true }));
  });

  test('only the payer removes a split', async () => {
    await seedSplit();
    await assertFails(deleteDoc(doc(bob(), 'sharedSplits', 's1')));
    await assertSucceeds(deleteDoc(doc(alice(), 'sharedSplits', 's1')));
  });
});
