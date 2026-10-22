/** Gemeinsame Helfer für barrierearme Formulare (Fehlertexte, Zusammenfassung, Versand). */

export type Messages = Record<string, string>;

export const readMessages = (form: HTMLFormElement): Messages =>
  JSON.parse(form.querySelector<HTMLScriptElement>('script[data-messages]')?.textContent || '{}');

const errorEl = (form: HTMLFormElement, name: string) => form.querySelector<HTMLElement>(`[data-error-for="${name}"]`);
const fieldEl = (form: HTMLFormElement, name: string) =>
  form.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(`[name="${name}"]`);

export function setFieldError(form: HTMLFormElement, name: string, message: string | null) {
  const field = fieldEl(form, name);
  const out = errorEl(form, name);
  if (field) field.setAttribute('aria-invalid', message ? 'true' : 'false');
  if (out) out.textContent = message ?? '';
}

export function clearErrors(form: HTMLFormElement) {
  form.querySelectorAll<HTMLElement>('[data-error-for]').forEach((el) => (el.textContent = ''));
  form.querySelectorAll('[aria-invalid]').forEach((el) => el.setAttribute('aria-invalid', 'false'));
  const summary = form.querySelector<HTMLElement>('[data-summary]');
  if (summary) summary.hidden = true;
}

/** Zeigt alle Fehler an, listet sie oben auf und setzt den Fokus auf die Zusammenfassung. */
export function showErrors(form: HTMLFormElement, errors: Record<string, string>, labels: Record<string, string>) {
  for (const [name, msg] of Object.entries(errors)) setFieldError(form, name, msg);
  const summary = form.querySelector<HTMLElement>('[data-summary]');
  const list = summary?.querySelector('ul');
  if (!summary || !list) return;
  list.replaceChildren(
    ...Object.entries(errors).map(([name, msg]) => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      const field = fieldEl(form, name);
      a.href = `#${field?.id ?? ''}`;
      a.textContent = `${labels[name] ?? name}: ${msg}`;
      a.addEventListener('click', (e) => {
        e.preventDefault();
        field?.focus();
      });
      li.append(a);
      return li;
    }),
  );
  summary.hidden = false;
  summary.focus();
}

export function setBusy(form: HTMLFormElement, busy: boolean) {
  const button = form.querySelector<HTMLButtonElement>('button[type="submit"]');
  const label = button?.querySelector<HTMLElement>('[data-label]');
  if (!button || !label) return;
  button.disabled = busy;
  button.setAttribute('aria-disabled', String(busy));
  label.dataset.idle ??= label.textContent ?? '';
  label.textContent = busy ? button.dataset.busyLabel || label.dataset.idle : label.dataset.idle;
}

/** Start-Zeitstempel für die Spam-Prüfung setzen. */
export const stampStart = (form: HTMLFormElement) => {
  const input = form.querySelector<HTMLInputElement>('input[name="started"]');
  if (input) input.value = String(Date.now());
};
