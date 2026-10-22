import {
  validateReservation,
  timeSlots,
  dateRange,
  isOpenOn,
  addDays,
  formatDateWith,
  weekdayOf,
  reservationRef,
  type ValidationErrors,
  type Reservation,
} from '../lib/reservation.ts';
import { reservationRules as rules } from '../config/site.ts';
import { readMessages, setFieldError, clearErrors, showErrors, setBusy, stampStart } from './forms.ts';
import { sendForm, type SendResult } from './submit.ts';

const form = document.querySelector<HTMLFormElement>('[data-reservation-form]');

if (form) {
  const lang = document.documentElement.lang === 'en' ? 'en' : 'de';
  const msg = readMessages(form);
  const dayNames = msg['ui.days'].split('|');
  const $ = <T extends HTMLElement>(sel: string) => form.querySelector<T>(sel)!;
  const dateInput = $<HTMLInputElement>('#res-date');
  const timeSelect = $<HTMLSelectElement>('#res-time');
  const guestsInput = $<HTMLInputElement>('#res-guests');
  const quick = $<HTMLElement>('[data-quick-dates]');
  const touched = new Set<string>();

  const labels: Record<string, string> = Object.fromEntries(
    ['date', 'time', 'guests', 'name', 'phone', 'email', 'note', 'privacy'].map((k) => [k, msg[`label.${k}`]]),
  );
  const timeLabel = (s: string) => (msg['ui.oclock'] ? `${s} ${msg['ui.oclock']}` : s);

  // ── Datum: Grenzen und Schnellauswahl der nächsten offenen Tage ──
  const { min, max } = dateRange();
  dateInput.min = min;
  dateInput.max = max;

  const quickDays: string[] = [];
  for (let d = min; quickDays.length < 5 && d <= max; d = addDays(d, 1)) {
    if (isOpenOn(d) && timeSlots(d).length) quickDays.push(d);
  }
  quick.replaceChildren(
    ...quickDays.map((d) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.dataset.date = d;
      b.setAttribute('aria-pressed', 'false');
      b.className =
        'rounded-full border-[1.5px] border-line bg-card px-3.5 py-2 text-sm font-medium transition-colors hover:border-spree aria-pressed:border-spree aria-pressed:bg-spree aria-pressed:text-paper';
      const rel = d === min ? msg['ui.today'] : d === addDays(min, 1) ? msg['ui.tomorrow'] : dayNames[weekdayOf(d)].slice(0, 2);
      const [, m, day] = d.split('-');
      b.textContent = `${rel} ${lang === 'de' ? `${+day}.${+m}.` : `${+day}/${+m}`}`;
      b.title = formatDateWith(d, lang, dayNames);
      b.addEventListener('click', () => {
        dateInput.value = d;
        onDateChange();
        timeSelect.focus();
      });
      return b;
    }),
  );

  // ── Uhrzeiten passend zum Datum ──
  function renderSlots() {
    const date = dateInput.value;
    const slots = date ? timeSlots(date) : [];
    const keep = timeSelect.value;
    const placeholder = new Option(slots.length || !date ? msg['ui.timePick'] : msg['ui.timeNone'], '');
    const options = (date ? slots : timeSlots('2000-01-03', new Date('1999-12-01T00:00:00Z'))).map(
      (s) => new Option(timeLabel(s), s, false, s === keep),
    );
    timeSelect.replaceChildren(placeholder, ...options);
    if (keep && !slots.includes(keep) && date) timeSelect.value = '';
  }

  function onDateChange() {
    touched.add('date');
    quick.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.date === dateInput.value)));
    renderSlots();
    validateField('date');
    if (touched.has('time')) validateField('time');
  }
  dateInput.addEventListener('change', onDateChange);

  // ── Personen-Stepper ──
  form.querySelectorAll<HTMLButtonElement>('.guest-step').forEach((btn) =>
    btn.addEventListener('click', () => {
      const next = Math.min(rules.maxGuests + 1, Math.max(rules.minGuests, (Number(guestsInput.value) || 0) + Number(btn.dataset.step)));
      guestsInput.value = String(next);
      touched.add('guests');
      validateField('guests');
    }),
  );

  // ── Validierung (gleiche Regeln wie auf dem Server) ──
  const collect = () => {
    const data = new FormData(form);
    return {
      date: String(data.get('date') ?? ''),
      time: String(data.get('time') ?? ''),
      guests: String(data.get('guests') ?? ''),
      name: String(data.get('name') ?? ''),
      phone: String(data.get('phone') ?? ''),
      email: String(data.get('email') ?? ''),
      note: String(data.get('note') ?? ''),
      privacy: data.get('privacy') === 'on',
    };
  };
  const errorsNow = (): ValidationErrors => {
    const res = validateReservation(collect());
    return res.ok ? {} : res.errors;
  };
  function validateField(name: string) {
    const code = errorsNow()[name as keyof ValidationErrors];
    setFieldError(form!, name, code ? msg[code] : null);
  }

  form.querySelectorAll<HTMLInputElement>('input:not([type=hidden]), select, textarea').forEach((el) => {
    if (el.name === 'date' || el.name === 'company') return;
    const evt = el.type === 'checkbox' || el.tagName === 'SELECT' ? 'change' : 'blur';
    el.addEventListener(evt, () => {
      touched.add(el.name);
      validateField(el.name);
    });
    // Fehler verschwinden beim Korrigieren sofort, neue erscheinen erst beim Verlassen
    el.addEventListener('input', () => {
      if (el.getAttribute('aria-invalid') === 'true') validateField(el.name);
    });
  });

  // ── Absenden ──
  const success = document.querySelector<HTMLElement>('[data-success]')!;
  const failed = $<HTMLElement>('[data-failed]');
  stampStart(form);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors(form);
    failed.hidden = true;
    const errors = errorsNow();
    if (Object.keys(errors).length) {
      showErrors(form, Object.fromEntries(Object.entries(errors).map(([k, c]) => [k, msg[c]])), labels);
      return;
    }

    const res = validateReservation(collect());
    if (!res.ok) return;
    const r = res.value;
    const ref = reservationRef(r.date);
    const when = `${formatDateWith(r.date, 'de', msg['ui.daysDe'].split('|'))}, ${r.time} Uhr`;

    setBusy(form, true);
    try {
      // Die E-Mail ans Restaurant ist immer deutsch – unabhängig von der Sprache der Seite.
      const result = await sendForm({
        subject: `Reservierung ${when} – ${r.guests} Pers. – ${r.name} [${ref}]`,
        name: r.name,
        replyTo: r.email,
        botcheck: (form.querySelector<HTMLInputElement>('[name=company]')?.value ?? '') ? 'on' : '',
        fields: [
          ['Vorgangsnummer', ref],
          ['Datum & Uhrzeit', when],
          ['Personen', String(r.guests)],
          ['Name', r.name],
          ['Telefon', r.phone],
          ['E-Mail', r.email],
          ['Anmerkung', r.note || '–'],
          ['Sprache', lang === 'de' ? 'Deutsch' : 'Englisch'],
          ['Hinweis', 'Bitte bestätigen Sie die Reservierung dem Gast per E-Mail oder telefonisch.'],
        ],
      });
      showSuccess(ref, r, result);
    } catch {
      failed.hidden = false;
      failed.focus();
    } finally {
      setBusy(form, false);
    }
  });

  function showSuccess(ref: string, r: Reservation, result: SendResult) {
    const out = (k: string) => success.querySelector<HTMLElement>(`[data-out="${k}"]`)!;
    out('date').textContent = formatDateWith(r.date, lang, dayNames);
    out('time').textContent = timeLabel(r.time);
    out('guests').textContent = String(r.guests);
    out('ref').textContent = ref;
    out('title').textContent = msg[result === 'sent' ? 'ui.sentTitle' : 'ui.mailtoTitle'];
    out('mailto').hidden = result !== 'mailto';

    form!.hidden = true;
    document.querySelectorAll<HTMLElement>('[data-hide-on-success]').forEach((el) => (el.hidden = true));
    success.hidden = false;
    success.classList.add('success-in');
    success.scrollIntoView({ block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    success.focus({ preventScroll: true });
  }
}
