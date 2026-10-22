/**
 * Zentrale Stammdaten des Restaurants. Alles, was sich ändern kann
 * (Telefonnummer, Ruhetag, Reservierungsregeln), wird nur hier gepflegt.
 */
export const site = {
  name: 'Café & Restaurant Spreeblick',
  shortName: 'Spreeblick',
  url: 'https://www.spreeblick-nikolaiviertel.de',
  address: {
    street: 'Propststraße 9',
    zip: '10178',
    city: 'Berlin',
    district: 'Mitte',
    country: 'DE',
  },
  geo: { lat: 52.516228, lng: 13.405982 },
  phone: { display: '030 2425247', displayGrouped: '030 24 25 247', href: 'tel:+49302425247' },
  fax: { display: '030 2474 60130' },
  email: 'spreeblick@t-online.de',
  maps: {
    apple:
      'https://maps.apple.com/?address=Propststra%C3%9Fe%209,%20Mitte,%2010178%20Berlin,%20Germany&auid=8723229496467652812&ll=52.516228,13.405982&lsp=9902&q=Caf%C3%A9%20%26%20Restaurant%20Spreeblick',
    google: 'https://goo.gl/maps/LeE2rB3QxQnrJAKS7',
  },
  legal: {
    company: 'Spreeblick Beteiligungsgesellschaft GmbH & Co. Gastro KG',
    register: 'HRA 26792',
    court: 'Amtsgericht Charlottenburg',
    representative: 'Udo Pape',
    vatId: 'DE170900738',
  },
  /**
   * Hosting-Anbieter für die Datenschutzerklärung. Bitte anpassen, falls die
   * Seite nicht mehr über GitHub Pages ausgeliefert wird.
   */
  hosting: {
    name: 'GitHub Inc.',
    address: '88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, USA',
    privacyUrl: 'https://docs.github.com/de/site-policy/privacy-policies/github-general-privacy-statement',
  },
  /** Dienst für den Versand der Formular-E-Mails (siehe scripts/submit.ts). */
  mailer: {
    name: 'Web3Forms',
    privacyUrl: 'https://web3forms.com/privacy',
  },
  privacyUpdated: { de: 'Oktober 2026', en: 'October 2026' },
} as const;

/** 0 = Sonntag … 6 = Samstag (wie Date#getDay). */
export type Weekday = 0 | 1 | 2 | 3 | 4 | 5 | 6;

/** Öffnungszeiten – Reihenfolge für die Anzeige: Montag zuerst. */
export const openingHours: { day: Weekday; opensFrom: string | null }[] = [
  { day: 1, opensFrom: '12:00' },
  { day: 2, opensFrom: '12:00' },
  { day: 3, opensFrom: null }, // Ruhetag
  { day: 4, opensFrom: '12:00' },
  { day: 5, opensFrom: '12:00' },
  { day: 6, opensFrom: '12:00' },
  { day: 0, opensFrom: '12:00' },
];

/**
 * Regeln für Online-Reservierungsanfragen.
 * `lastSeating` ist die späteste reservierbare Uhrzeit – die alte Website nennt
 * nur „ab 12 Uhr“, daher bitte mit dem Restaurant abstimmen.
 */
export const reservationRules = {
  timeZone: 'Europe/Berlin',
  firstSeating: '12:00',
  lastSeating: '21:00',
  slotMinutes: 30,
  /** Mindestvorlauf für Reservierungen am selben Tag. */
  minLeadMinutes: 120,
  /** Wie weit im Voraus online angefragt werden kann. */
  maxAdvanceDays: 90,
  minGuests: 1,
  /** Größere Gruppen bitte telefonisch – individuelle Absprache. */
  maxGuests: 10,
  /** Zusätzliche Schließtage im Format JJJJ-MM-TT (z. B. Betriebsferien). */
  closedDates: [] as string[],
};
