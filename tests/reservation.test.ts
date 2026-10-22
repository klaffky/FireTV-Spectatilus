import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateReservation, timeSlots, berlinNow, isOpenOn, reservationRef } from '../src/lib/reservation.ts';

// Donnerstag, 22.10.2026, 10:00 Uhr in Berlin (MESZ = UTC+2)
const NOW = new Date('2026-10-22T08:00:00Z');

const valid = {
  date: '2026-10-23', // Freitag
  time: '19:00',
  guests: '4',
  name: 'Erika Mustermann',
  phone: '030 1234567',
  email: 'erika@example.org',
  note: '',
  privacy: 'on',
};

test('gültige Anfrage wird angenommen', () => {
  const res = validateReservation(valid, NOW);
  assert.equal(res.ok, true);
  if (res.ok) assert.equal(res.value.guests, 4);
});

test('Mittwoch ist Ruhetag', () => {
  assert.equal(isOpenOn('2026-10-28'), false);
  const res = validateReservation({ ...valid, date: '2026-10-28' }, NOW);
  assert.deepEqual(res.ok ? null : res.errors.date, 'closedWeekday');
  assert.deepEqual(timeSlots('2026-10-28', NOW), []);
});

test('Datum in der Vergangenheit und zu weit in der Zukunft', () => {
  const past = validateReservation({ ...valid, date: '2026-10-21' }, NOW);
  assert.equal(past.ok ? null : past.errors.date, 'datePast');
  const far = validateReservation({ ...valid, date: '2027-03-01' }, NOW);
  assert.equal(far.ok ? null : far.errors.date, 'dateTooFar');
  const bogus = validateReservation({ ...valid, date: '2026-02-30' }, NOW);
  assert.equal(bogus.ok ? null : bogus.errors.date, 'dateInvalid');
});

test('nur Uhrzeiten innerhalb der Öffnungszeiten', () => {
  const early = validateReservation({ ...valid, time: '11:30' }, NOW);
  assert.equal(early.ok ? null : early.errors.time, 'timeOutsideHours');
  const late = validateReservation({ ...valid, time: '23:00' }, NOW);
  assert.equal(late.ok ? null : late.errors.time, 'timeOutsideHours');
  const offGrid = validateReservation({ ...valid, time: '19:10' }, NOW);
  assert.equal(offGrid.ok ? null : offGrid.errors.time, 'timeOutsideHours');
  const slots = timeSlots('2026-10-23', NOW);
  assert.equal(slots[0], '12:00');
  assert.equal(slots.at(-1), '21:00');
});

test('heute mit Vorlauf (Berliner Zeit, nicht Server-Zeit)', () => {
  // 16:40 Berlin → frühester Slot 19:00 (2 h Vorlauf)
  const afternoon = new Date('2026-10-22T14:40:00Z');
  assert.equal(berlinNow(afternoon).date, '2026-10-22');
  assert.equal(timeSlots('2026-10-22', afternoon)[0], '19:00');
  const tooSoon = validateReservation({ ...valid, date: '2026-10-22', time: '17:00' }, afternoon);
  assert.equal(tooSoon.ok ? null : tooSoon.errors.time, 'timeTooSoon');
  // Kurz vor Mitternacht UTC ist in Berlin schon der nächste Tag
  assert.equal(berlinNow(new Date('2026-10-22T22:30:00Z')).date, '2026-10-23');
});

test('Personenzahl, Kontaktdaten und Einwilligung', () => {
  const res = validateReservation(
    { ...valid, guests: '11', name: ' ', phone: 'abc', email: 'nope', privacy: '' },
    NOW,
  );
  assert.equal(res.ok, false);
  if (!res.ok) {
    assert.equal(res.errors.guests, 'guestsTooMany');
    assert.equal(res.errors.name, 'required');
    assert.equal(res.errors.phone, 'phoneInvalid');
    assert.equal(res.errors.email, 'emailInvalid');
    assert.equal(res.errors.privacy, 'privacy');
  }
  const zero = validateReservation({ ...valid, guests: '0' }, NOW);
  assert.equal(zero.ok ? null : zero.errors.guests, 'guestsInvalid');
});

test('Vorgangsnummer enthält das Datum', () => {
  assert.match(reservationRef('2026-10-30', () => 0), /^SB-1030-AAAA$/);
});
