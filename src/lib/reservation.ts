/**
 * Reservierungslogik ohne Abhängigkeiten – läuft identisch im Browser
 * (Live-Validierung) und auf dem Server (verbindliche Prüfung).
 */
import { openingHours, reservationRules as rules } from '../config/site.ts';

export type ReservationInput = {
  date: string; // JJJJ-MM-TT
  time: string; // HH:MM
  guests: string | number;
  name: string;
  phone: string;
  email: string;
  note?: string;
  privacy?: string | boolean;
};

export type ReservationField = 'date' | 'time' | 'guests' | 'name' | 'phone' | 'email' | 'note' | 'privacy';

export type ErrorCode =
  | 'required'
  | 'dateInvalid'
  | 'datePast'
  | 'dateTooFar'
  | 'closedWeekday'
  | 'closedDate'
  | 'timeInvalid'
  | 'timeOutsideHours'
  | 'timeTooSoon'
  | 'guestsInvalid'
  | 'guestsTooMany'
  | 'emailInvalid'
  | 'phoneInvalid'
  | 'tooLong'
  | 'privacy';

export type ValidationErrors = Partial<Record<ReservationField, ErrorCode>>;

export type Reservation = {
  date: string;
  time: string;
  guests: number;
  name: string;
  phone: string;
  email: string;
  note: string;
};

const pad = (n: number) => String(n).padStart(2, '0');

export const toMinutes = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
};

const fromMinutes = (min: number) => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`;

/** Aktuelles Datum und Uhrzeit in Berlin – unabhängig von der Zeitzone des Geräts/Servers. */
export function berlinNow(now: Date = new Date()) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: rules.timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  );
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    minutes: Number(parts.hour) * 60 + Number(parts.minute),
  };
}

/** Parst JJJJ-MM-TT streng; gibt UTC-Mittag zurück, damit Wochentage stabil sind. */
export function parseDate(value: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return null;
  const d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], 12));
  return d.getUTCMonth() === +m[2] - 1 && d.getUTCDate() === +m[3] ? d : null;
}

export const addDays = (isoDate: string, days: number) => {
  const d = parseDate(isoDate)!;
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};

export const weekdayOf = (isoDate: string) => parseDate(isoDate)!.getUTCDay();

export function isOpenOn(isoDate: string) {
  const day = weekdayOf(isoDate);
  const entry = openingHours.find((h) => h.day === day);
  return Boolean(entry?.opensFrom) && !rules.closedDates.includes(isoDate);
}

/** Alle buchbaren Uhrzeiten eines Tages (berücksichtigt den Vorlauf am selben Tag). */
export function timeSlots(isoDate: string, now: Date = new Date()): string[] {
  if (!parseDate(isoDate) || !isOpenOn(isoDate)) return [];
  const today = berlinNow(now);
  const earliest = isoDate === today.date ? today.minutes + rules.minLeadMinutes : -1;
  const slots: string[] = [];
  for (let t = toMinutes(rules.firstSeating); t <= toMinutes(rules.lastSeating); t += rules.slotMinutes) {
    if (t >= earliest) slots.push(fromMinutes(t));
  }
  return slots;
}

/** Frühestes und spätestes Datum für das Datumsfeld. */
export function dateRange(now: Date = new Date()) {
  const today = berlinNow(now).date;
  return { min: today, max: addDays(today, rules.maxAdvanceDays) };
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
// Ziffern, Leerzeichen, +, -, /, Klammern; mindestens 6 Ziffern.
const PHONE_RE = /^[+\d][\d\s()/-]{4,}$/;

const LIMITS = { name: 120, phone: 40, email: 200, note: 1000 } as const;

export function validateReservation(
  input: ReservationInput,
  now: Date = new Date(),
): { ok: true; value: Reservation } | { ok: false; errors: ValidationErrors } {
  const errors: ValidationErrors = {};
  const date = String(input.date ?? '').trim();
  const time = String(input.time ?? '').trim();
  const name = String(input.name ?? '').trim();
  const phone = String(input.phone ?? '').trim();
  const email = String(input.email ?? '').trim();
  const note = String(input.note ?? '').trim();
  const guestsRaw = String(input.guests ?? '').trim();

  // Datum
  const { min, max } = dateRange(now);
  if (!date) errors.date = 'required';
  else if (!parseDate(date)) errors.date = 'dateInvalid';
  else if (date < min) errors.date = 'datePast';
  else if (date > max) errors.date = 'dateTooFar';
  else if (rules.closedDates.includes(date)) errors.date = 'closedDate';
  else if (!isOpenOn(date)) errors.date = 'closedWeekday';

  // Uhrzeit (nur prüfen, wenn das Datum gültig ist)
  if (!time) errors.time = 'required';
  else if (!/^\d{2}:\d{2}$/.test(time) || toMinutes(time) >= 24 * 60) errors.time = 'timeInvalid';
  else if (!errors.date) {
    const t = toMinutes(time);
    if (t < toMinutes(rules.firstSeating) || t > toMinutes(rules.lastSeating)) errors.time = 'timeOutsideHours';
    else if (!timeSlots(date, now).includes(time)) {
      errors.time = date === berlinNow(now).date ? 'timeTooSoon' : 'timeOutsideHours';
    }
  }

  // Personen
  const guests = Number(guestsRaw);
  if (!guestsRaw) errors.guests = 'required';
  else if (!Number.isInteger(guests) || guests < rules.minGuests) errors.guests = 'guestsInvalid';
  else if (guests > rules.maxGuests) errors.guests = 'guestsTooMany';

  // Kontaktdaten
  if (!name) errors.name = 'required';
  else if (name.length > LIMITS.name) errors.name = 'tooLong';

  if (!phone) errors.phone = 'required';
  else if (phone.length > LIMITS.phone) errors.phone = 'tooLong';
  else if (!PHONE_RE.test(phone) || phone.replace(/\D/g, '').length < 6) errors.phone = 'phoneInvalid';

  if (!email) errors.email = 'required';
  else if (email.length > LIMITS.email) errors.email = 'tooLong';
  else if (!EMAIL_RE.test(email)) errors.email = 'emailInvalid';

  if (note.length > LIMITS.note) errors.note = 'tooLong';

  const privacy = input.privacy === true || input.privacy === 'on' || input.privacy === 'true';
  if (!privacy) errors.privacy = 'privacy';

  if (Object.keys(errors).length) return { ok: false, errors };
  return { ok: true, value: { date, time, guests, name, phone, email, note } };
}

/** „Freitag, 24.10.2026“ bzw. „Friday, 24/10/2026“ – Wochentagsnamen kommen vom Aufrufer. */
export function formatDateWith(iso: string, lang: 'de' | 'en', dayNames: readonly string[]) {
  const [y, m, d] = iso.split('-');
  const weekday = dayNames[weekdayOf(iso)];
  return lang === 'de' ? `${weekday}, ${d}.${m}.${y}` : `${weekday}, ${d}/${m}/${y}`;
}

/** Kurze, gut vorlesbare Vorgangsnummer, z. B. SB-1024-K7QX. */
export function reservationRef(date: string, random: () => number = Math.random) {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 4; i++) code += alphabet[Math.floor(random() * alphabet.length)];
  return `SB-${date.slice(5, 7)}${date.slice(8, 10)}-${code}`;
}
