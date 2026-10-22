/**
 * Versand der Formulare ohne eigenen Server (statisches Hosting, z. B. GitHub Pages).
 *
 * - Mit PUBLIC_WEB3FORMS_KEY: direkter Versand per E-Mail über Web3Forms (https://web3forms.com).
 * - Ohne Schlüssel: Das E-Mail-Programm des Gastes öffnet sich mit einer fertig ausgefüllten Nachricht.
 */
import { site } from '../config/site.ts';

const ACCESS_KEY = import.meta.env.PUBLIC_WEB3FORMS_KEY as string | undefined;

export type Outgoing = {
  subject: string;
  /** Beschriftung → Wert, in dieser Reihenfolge in der E-Mail */
  fields: [string, string][];
  name: string;
  replyTo: string;
  /** Honeypot-Feld aus dem Formular */
  botcheck?: string;
};

export type SendResult = 'sent' | 'mailto';

export async function sendForm(mail: Outgoing): Promise<SendResult> {
  if (!ACCESS_KEY) {
    const body = mail.fields.map(([k, v]) => `${k}: ${v}`).join('\n');
    window.location.href = `mailto:${site.email}?subject=${encodeURIComponent(mail.subject)}&body=${encodeURIComponent(body)}`;
    return 'mailto';
  }
  const res = await fetch('https://api.web3forms.com/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      access_key: ACCESS_KEY,
      subject: mail.subject,
      from_name: `${site.shortName} Website – ${mail.name}`,
      replyto: mail.replyTo,
      botcheck: mail.botcheck ?? '',
      ...Object.fromEntries(mail.fields),
    }),
  });
  const data = (await res.json().catch(() => ({}))) as { success?: boolean };
  if (!res.ok || !data.success) throw new Error(`Versand fehlgeschlagen (${res.status})`);
  return 'sent';
}

export const formServiceConfigured = Boolean(ACCESS_KEY);
