import { readMessages, clearErrors, showErrors, setFieldError, setBusy, stampStart } from './forms.ts';
import { sendForm } from './submit.ts';

const form = document.querySelector<HTMLFormElement>('[data-contact-form]');
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

if (form) {
  const msg = readMessages(form);
  const labels = Object.fromEntries(['name', 'email', 'message', 'privacy'].map((k) => [k, msg[`label.${k}`]]));
  const status = (kind: 'ok' | 'error' | 'mailto') => form.querySelector<HTMLElement>(`[data-status="${kind}"]`)!;
  stampStart(form);

  const validate = () => {
    const d = new FormData(form);
    const errors: Record<string, string> = {};
    const name = String(d.get('name') ?? '').trim();
    const email = String(d.get('email') ?? '').trim();
    const message = String(d.get('message') ?? '').trim();
    if (!name) errors.name = msg.required;
    if (!email) errors.email = msg.required;
    else if (!EMAIL_RE.test(email)) errors.email = msg.emailInvalid;
    if (!message) errors.message = msg.required;
    if (d.get('privacy') !== 'on') errors.privacy = msg.privacy;
    return errors;
  };

  form.querySelectorAll<HTMLInputElement>('[name=name], [name=email], [name=message], [name=privacy]').forEach((el) =>
    el.addEventListener(el.type === 'checkbox' ? 'change' : 'blur', () => setFieldError(form, el.name, validate()[el.name] ?? null)),
  );

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors(form);
    status('ok').hidden = status('error').hidden = status('mailto').hidden = true;
    const errors = validate();
    if (Object.keys(errors).length) return showErrors(form, errors, labels);

    setBusy(form, true);
    try {
      const d = new FormData(form);
      const name = String(d.get('name')).trim();
      const email = String(d.get('email')).trim();
      const result = await sendForm({
        subject: `Kontaktanfrage über die Website – ${name}`,
        name,
        replyTo: email,
        botcheck: d.get('company') ? 'on' : '',
        fields: [
          ['Name', name],
          ['E-Mail', email],
          ['Nachricht', String(d.get('message')).trim()],
        ],
      });
      const shown = status(result === 'sent' ? 'ok' : 'mailto');
      if (result === 'sent') {
        form.reset();
        stampStart(form);
      }
      shown.hidden = false;
      shown.focus();
    } catch {
      status('error').hidden = false;
      status('error').focus();
    } finally {
      setBusy(form, false);
    }
  });
}
