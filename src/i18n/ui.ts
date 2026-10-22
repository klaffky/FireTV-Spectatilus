export const languages = { de: 'Deutsch', en: 'English' } as const;
export type Lang = keyof typeof languages;
export const defaultLang: Lang = 'de';

/** Seiten-Schlüssel → Pfad je Sprache (gleiche Slugs wie auf der alten Website). */
export const routes = {
  home: { de: '/', en: '/en' },
  menu: { de: '/menu', en: '/en/menu' },
  contact: { de: '/kontakt', en: '/en/kontakt' },
  reserve: { de: '/reservieren', en: '/en/reservieren' },
  reserveThanks: { de: '/reservieren/danke', en: '/en/reservieren/danke' },
  contactThanks: { de: '/kontakt/danke', en: '/en/kontakt/danke' },
  imprint: { de: '/impressum', en: '/en/impressum' },
  privacy: { de: '/datenschutz', en: '/en/datenschutz' },
} as const;
export type RouteKey = keyof typeof routes;

/** Basis-Pfad der Website (z. B. „/spreeblick-website“ auf GitHub Pages, leer bei eigener Domain). */
const BASE = (import.meta.env.BASE_URL ?? '/').replace(/\/$/, '');

/** Pfad inkl. Basis, z. B. withBase('/og.jpg') → „/spreeblick-website/og.jpg“. */
export const withBase = (p: string) => (p === '/' ? `${BASE}/` : `${BASE}${p}`);

export const path = (key: RouteKey, lang: Lang) => withBase(routes[key][lang]);

export const ui = {
  de: {
    'meta.locale': 'de_DE',
    'skip': 'Zum Inhalt springen',
    'a11y.newWindow': '(öffnet neues Fenster)',
    'a11y.legal': 'Rechtliches',
    'nav.home': 'Startseite',
    'nav.menu': 'Speisekarte',
    'nav.contact': 'Kontakt',
    'nav.reserve': 'Jetzt reservieren',
    'nav.reserveShort': 'Reservieren',
    'nav.open': 'Menü öffnen',
    'nav.close': 'Menü schließen',
    'nav.main': 'Hauptnavigation',
    'nav.call': 'Anrufen',
    'lang.switch': 'Switch to English',
    'lang.label': 'EN',

    'hero.l1': 'Echte',
    'hero.l2': 'Berliner',
    'hero.l3': 'Küche',
    'hero.kicker': 'Café & Restaurant im Nikolaiviertel',
    'hero.menu': 'Zur Speisekarte',
    'hero.imgAlt': 'Wintergarten des Restaurants Spreeblick mit Tiffany-Lampen und gedeckten Tischen',

    'terrace': 'Für die Außenterrasse nehmen wir keine Reservierungen entgegen.',
    'terrace.contact': 'Für die Außenterrasse nehmen wir keine Reservierungen an.',
    'notice': 'Hinweis',

    'welcome.title': 'Willkommen im Café & Restaurant Spreeblick',
    'welcome.p1':
      'Von unserem Lokal aus blicken Sie mit Ihren Gästen auf ein berühmtes und beliebtes Denkmal, den Heiligen Georg als Drachentöter. Außerdem haben Sie einen fabelhaften Blick auf die Spree und das Stadtschloss von Berlin.',
    'welcome.p2': 'Sie bekommen bei uns, von Alt-Berliner Speisen bis zum schönen Stück Torte, alles was das Herz begehrt.',
    'welcome.p3':
      'Vom Geburtstag bis zur Hochzeit, durch eine individuelle Absprache und Beratung, bieten wir für jeden Anlass den idealen Rahmen.',
    'welcome.p4': 'Wir freuen uns auf Ihren Besuch in unserem Restaurant Spreeblick.',
    'welcome.p5': 'Wir befinden uns im Nikolaiviertel und sind täglich ab 12 Uhr für Sie da.',
    'welcome.imgAlt': 'Festlich eingedeckte Tafel im Gastraum mit Kerzen und Blumen',

    'quarter.kicker': 'Das Nikolaiviertel',
    'quarter.title': 'Im historischen Kern Berlins',
    'quarter.text':
      'Das malerische Nikolaiviertel ist das älteste Viertel Berlins, in deren Zentrum die im 13. Jahrhundert erbaute Nikolaikirche liegt. Unweit davon liegen das Rote Rathaus, sowie der Alexanderplatz. Somit bildet das Nikolaiviertel den historischen Kern von Berlin.',
    'quarter.century': '13. Jh.',
    'quarter.centuryLabel': 'Nikolaikirche',

    'teaser.kicker': 'Aus der Speisekarte',
    'teaser.all': 'Ganze Speisekarte ansehen',

    'when.title': 'Wann & wo?',
    'hours.title': 'Öffnungszeiten',
    'hours.from': 'ab 12 Uhr',
    'hours.closed': 'Ruhetag',
    'hours.today': 'Heute',
    'directions.title': 'Anfahrt',
    'contact.title': 'Kontakt',
    'contact.tel': 'Tel',
    'contact.fax': 'Fax',
    'contact.email': 'E-Mail',
    'contact.formLink': 'Zum Kontaktformular',
    'days': ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],

    'cta.title': 'Ihr Tisch im Spreeblick',
    'cta.text': 'Reservieren Sie bequem online – oder rufen Sie uns an.',

    'footer.imprint': 'Impressum',
    'footer.privacy': 'Datenschutz',

    'menu.title': 'Speisekarte',
    'menu.food': 'Speisen',
    'menu.drinks': 'Getränke',
    'menu.jump': 'Kategorien',

    'contactPage.title': 'Kontakt',
    'contactPage.reservations': 'Reservierungen',
    'contactPage.write': 'Schreiben Sie uns',
    'contactPage.online': 'Lieber online? Hier geht es zur Tischreservierung.',

    'form.name': 'Name',
    'form.email': 'E-Mail',
    'form.phone': 'Telefon',
    'form.message': 'Nachricht',
    'form.privacy1': 'Ich stimme der ',
    'form.privacy2': 'Datenschutzerklärung',
    'form.privacy3': ' zu.',
    'form.send': 'Absenden',
    'form.sending': 'Wird gesendet …',
    'form.sent': 'Ihre Nachricht wurde versendet.',
    'form.mailto': 'Ihr E-Mail-Programm wurde geöffnet. Bitte senden Sie die vorbereitete Nachricht ab.',
    'form.failed':
      'Ihre Nachricht konnte nicht versendet werden. Bitte kontaktieren Sie uns über unsere E-Mail Adresse oder telefonisch.',
    'form.required': 'Pflichtfeld',
    'form.optional': 'optional',
    'form.summary': 'Bitte prüfen Sie die markierten Felder:',
    'form.back': 'Zurück',

    'res.title': 'Tisch reservieren',
    'res.kicker': 'Online-Reservierung',
    'res.intro':
      'Wählen Sie Datum, Uhrzeit und Personenzahl – wir prüfen Ihre Anfrage und bestätigen sie Ihnen per E-Mail oder telefonisch.',
    'res.when': 'Wann möchten Sie kommen?',
    'res.who': 'Ihre Kontaktdaten',
    'res.date': 'Datum',
    'res.time': 'Uhrzeit',
    'res.timePick': 'Bitte wählen',
    'res.timeFirst': 'Erst Datum wählen',
    'res.timeNone': 'Keine Zeiten verfügbar',
    'res.tomorrow': 'Morgen',
    'res.guests': 'Personen',
    'res.less': 'Eine Person weniger',
    'res.more': 'Eine Person mehr',
    'res.note': 'Anmerkung',
    'res.noteHint': 'z. B. Allergien, Kinderstuhl, Anlass',
    'res.submit': 'Reservierung anfragen',
    'res.wednesday': 'Mittwoch ist Ruhetag.',
    'res.rules.hours': 'Reservierungen täglich ab 12 Uhr, Mittwoch Ruhetag',
    'res.rules.indoor': 'Reserviert werden ausschließlich Plätze im Innenbereich',
    'res.rules.group': 'Ab {n} Personen bitte telefonisch – wir beraten Sie gern zu Feiern & Anlässen',
    'res.rules.confirm': 'Ihre Reservierung ist erst mit unserer Bestätigung verbindlich',
    'res.phoneAlt': 'Lieber persönlich?',
    'res.done.title': 'Vielen Dank – Ihre Anfrage ist bei uns eingegangen.',
    'res.done.text': 'Wir prüfen die Verfügbarkeit und melden uns zur Bestätigung bei Ihnen.',
    'res.done.mailto':
      'Ihr E-Mail-Programm wurde mit einer vorbereiteten Nachricht geöffnet. Bitte senden Sie diese ab – erst dann erreicht uns Ihre Anfrage.',
    'res.done.sentTitle': 'Vielen Dank – Ihre Anfrage ist bei uns eingegangen.',
    'res.done.mailtoTitle': 'Fast geschafft – bitte E-Mail absenden.',
    'res.done.ref': 'Vorgangsnummer',
    'res.done.again': 'Weitere Reservierung anfragen',
    'res.done.home': 'Zur Startseite',
    'res.summary.date': 'Datum',
    'res.summary.time': 'Uhrzeit',
    'res.summary.guests': 'Personen',
    'res.summary.name': 'Name',
    'res.failed':
      'Ihre Anfrage konnte leider nicht übermittelt werden. Bitte rufen Sie uns an oder schreiben Sie uns eine E-Mail.',
    'res.oclock': 'Uhr',

    'err.required': 'Bitte ausfüllen.',
    'err.dateInvalid': 'Bitte ein gültiges Datum wählen.',
    'err.datePast': 'Dieses Datum liegt in der Vergangenheit.',
    'err.dateTooFar': 'Online-Reservierungen sind bis zu {n} Tage im Voraus möglich.',
    'err.closedWeekday': 'Mittwoch ist Ruhetag – bitte einen anderen Tag wählen.',
    'err.closedDate': 'An diesem Tag ist das Restaurant geschlossen.',
    'err.timeInvalid': 'Bitte eine Uhrzeit aus der Liste wählen.',
    'err.timeOutsideHours': 'Bitte eine Uhrzeit zwischen {from} und {to} Uhr wählen.',
    'err.timeTooSoon': 'Für heute bitte mindestens {h} Stunden Vorlauf – oder rufen Sie uns an.',
    'err.guestsInvalid': 'Bitte eine gültige Personenzahl angeben.',
    'err.guestsTooMany': 'Für mehr als {n} Personen rufen Sie uns bitte an.',
    'err.emailInvalid': 'Bitte eine gültige E-Mail-Adresse angeben.',
    'err.phoneInvalid': 'Bitte eine gültige Telefonnummer angeben.',
    'err.tooLong': 'Dieser Text ist zu lang.',
    'err.privacy': 'Bitte stimmen Sie der Datenschutzerklärung zu.',

    'imprint.title': 'Impressum',
    'imprint.according': 'Angaben gemäß § 5 DDG',
    'imprint.register': 'Handelsregister',
    'imprint.court': 'Registergericht',
    'imprint.rep': 'Vertreten durch',
    'imprint.contact': 'Kontakt',
    'imprint.phone': 'Telefon',
    'imprint.vat': 'Umsatzsteuer-ID',
    'imprint.vatText': 'Umsatzsteuer-Identifikationsnummer gemäß § 27 a Umsatzsteuergesetz:',
    'imprint.dispute': 'Verbraucherstreitbeilegung/Universal-Schlichtungsstelle',
    'imprint.disputeText':
      'Wir sind nicht bereit oder verpflichtet, an Streitbeilegungsverfahren vor einer Verbraucherschlichtungsstelle teilzunehmen.',

    'privacy.title': 'Datenschutzerklärung',
    'privacy.toc': 'Inhalt',

    'notfound.title': 'Seite nicht gefunden',
    'notfound.text': 'Diese Seite gibt es leider nicht (mehr). Vielleicht finden Sie hier, was Sie suchen:',

    'seo.home.title': 'Café & Restaurant Spreeblick – Echte Berliner Küche im Nikolaiviertel',
    'seo.home.desc':
      'Alt-Berliner Küche mit Blick auf die Spree: Eisbein, Königsberger Klopse, Currywurst und Torte im Nikolaiviertel, Propststraße 9. Täglich ab 12 Uhr, Mittwoch Ruhetag.',
    'seo.menu.title': 'Speisekarte – Café & Restaurant Spreeblick',
    'seo.menu.desc':
      'Die komplette Speisekarte des Restaurants Spreeblick: Berliner Originale, Saisonales, Fisch aus der Spree, Desserts, Eis, Biere vom Fass, Weine und Berliner Schnäpse.',
    'seo.contact.title': 'Kontakt – Café & Restaurant Spreeblick',
    'seo.contact.desc': 'Telefon 030 2425247, E-Mail spreeblick@t-online.de – oder schreiben Sie uns direkt über das Kontaktformular.',
    'seo.reserve.title': 'Tisch reservieren – Café & Restaurant Spreeblick',
    'seo.reserve.desc':
      'Reservieren Sie Ihren Tisch im Restaurant Spreeblick im Nikolaiviertel online. Täglich ab 12 Uhr, Mittwoch Ruhetag.',
    'seo.imprint.title': 'Impressum – Café & Restaurant Spreeblick',
    'seo.imprint.desc': 'Impressum der Spreeblick Beteiligungsgesellschaft GmbH & Co. Gastro KG, Propststraße 9, 10178 Berlin.',
    'seo.privacy.title': 'Datenschutzerklärung – Café & Restaurant Spreeblick',
    'seo.privacy.desc': 'Informationen zur Verarbeitung personenbezogener Daten auf der Website des Restaurants Spreeblick.',
  },
  en: {
    'meta.locale': 'en_GB',
    'skip': 'Skip to content',
    'a11y.newWindow': '(opens in a new window)',
    'a11y.legal': 'Legal',
    'nav.home': 'Home',
    'nav.menu': 'Menu',
    'nav.contact': 'Contact',
    'nav.reserve': 'Book a table',
    'nav.reserveShort': 'Book',
    'nav.open': 'Open menu',
    'nav.close': 'Close menu',
    'nav.main': 'Main navigation',
    'nav.call': 'Call',
    'lang.switch': 'Auf Deutsch wechseln',
    'lang.label': 'DE',

    'hero.l1': 'Real',
    'hero.l2': 'Berlin',
    'hero.l3': 'Cuisine',
    'hero.kicker': 'Café & Restaurant in the Nikolaiviertel',
    'hero.menu': 'See the menu',
    'hero.imgAlt': 'Conservatory of Restaurant Spreeblick with Tiffany lamps and laid tables',

    'terrace': 'We do not take reservations for the terrace.',
    'terrace.contact': 'We do not take reservations for the terrace.',
    'notice': 'Please note',

    'welcome.title': 'Welcome to Café & Restaurant Spreeblick',
    'welcome.p1':
      'From our restaurant you and your guests look out onto a famous and much-loved monument, Saint George slaying the dragon. You also enjoy a fabulous view of the Spree and the Berlin Palace.',
    'welcome.p2': 'From traditional Old Berlin dishes to a fine slice of cake – we have everything your heart desires.',
    'welcome.p3':
      'From birthdays to weddings: with individual planning and advice, we offer the ideal setting for every occasion.',
    'welcome.p4': 'We look forward to welcoming you at Restaurant Spreeblick.',
    'welcome.p5': 'You will find us in the Nikolaiviertel, open for you daily from 12 pm.',
    'welcome.imgAlt': 'Festively laid table in the dining room with candles and flowers',

    'quarter.kicker': 'The Nikolaiviertel',
    'quarter.title': 'In the historic heart of Berlin',
    'quarter.text':
      'The picturesque Nikolaiviertel is the oldest quarter of Berlin. At its centre stands the Nikolaikirche, built in the 13th century. The Red Town Hall and Alexanderplatz are close by – which makes the Nikolaiviertel the historic core of Berlin.',
    'quarter.century': '13th c.',
    'quarter.centuryLabel': 'Nikolaikirche',

    'teaser.kicker': 'From our menu',
    'teaser.all': 'See the full menu',

    'when.title': 'When & where?',
    'hours.title': 'Opening hours',
    'hours.from': 'from 12 pm',
    'hours.closed': 'closed',
    'hours.today': 'Today',
    'directions.title': 'Directions',
    'contact.title': 'Contact',
    'contact.tel': 'Phone',
    'contact.fax': 'Fax',
    'contact.email': 'Email',
    'contact.formLink': 'Contact form',
    'days': ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],

    'cta.title': 'Your table at Spreeblick',
    'cta.text': 'Book online in a minute – or give us a call.',

    'footer.imprint': 'Site notice',
    'footer.privacy': 'Privacy policy',

    'menu.title': 'Menu',
    'menu.food': 'Food',
    'menu.drinks': 'Drinks',
    'menu.jump': 'Categories',

    'contactPage.title': 'Contact',
    'contactPage.reservations': 'Reservations',
    'contactPage.write': 'Text us',
    'contactPage.online': 'Prefer online? Book your table here.',

    'form.name': 'Name',
    'form.email': 'Email',
    'form.phone': 'Phone',
    'form.message': 'Message',
    'form.privacy1': 'I agree to the ',
    'form.privacy2': 'privacy policy',
    'form.privacy3': '.',
    'form.send': 'Send',
    'form.sending': 'Sending …',
    'form.sent': 'Your message has been sent.',
    'form.mailto': 'Your email app has opened. Please send the prepared message.',
    'form.failed': 'Your message could not be sent. Please use the contact details.',
    'form.required': 'required',
    'form.optional': 'optional',
    'form.summary': 'Please check the highlighted fields:',
    'form.back': 'Back',

    'res.title': 'Book a table',
    'res.kicker': 'Online booking',
    'res.intro':
      'Choose a date, time and party size – we will check your request and confirm it by email or phone.',
    'res.when': 'When would you like to visit?',
    'res.who': 'Your contact details',
    'res.date': 'Date',
    'res.time': 'Time',
    'res.timePick': 'Please choose',
    'res.timeFirst': 'Choose a date first',
    'res.timeNone': 'No times available',
    'res.tomorrow': 'Tomorrow',
    'res.guests': 'Guests',
    'res.less': 'One guest fewer',
    'res.more': 'One guest more',
    'res.note': 'Note',
    'res.noteHint': 'e.g. allergies, high chair, occasion',
    'res.submit': 'Request booking',
    'res.wednesday': 'We are closed on Wednesdays.',
    'res.rules.hours': 'Bookings daily from 12 pm, closed on Wednesdays',
    'res.rules.indoor': 'Bookings are for indoor seating only',
    'res.rules.group': 'Groups of {n} or more please call us – we are happy to help plan celebrations',
    'res.rules.confirm': 'Your booking is binding once we have confirmed it',
    'res.phoneAlt': 'Prefer to talk?',
    'res.done.title': 'Thank you – we have received your request.',
    'res.done.text': 'We will check availability and get back to you to confirm.',
    'res.done.mailto':
      'Your email app has opened with a prepared message. Please send it – only then will your request reach us.',
    'res.done.sentTitle': 'Thank you – we have received your request.',
    'res.done.mailtoTitle': 'Almost done – please send the email.',
    'res.done.ref': 'Reference',
    'res.done.again': 'Make another request',
    'res.done.home': 'Back to home',
    'res.summary.date': 'Date',
    'res.summary.time': 'Time',
    'res.summary.guests': 'Guests',
    'res.summary.name': 'Name',
    'res.failed': 'Sorry, your request could not be sent. Please call us or send us an email.',
    'res.oclock': '',

    'err.required': 'Please fill in this field.',
    'err.dateInvalid': 'Please choose a valid date.',
    'err.datePast': 'This date is in the past.',
    'err.dateTooFar': 'Online bookings are possible up to {n} days in advance.',
    'err.closedWeekday': 'We are closed on Wednesdays – please choose another day.',
    'err.closedDate': 'The restaurant is closed on this day.',
    'err.timeInvalid': 'Please choose a time from the list.',
    'err.timeOutsideHours': 'Please choose a time between {from} and {to}.',
    'err.timeTooSoon': 'For today please allow at least {h} hours – or give us a call.',
    'err.guestsInvalid': 'Please enter a valid number of guests.',
    'err.guestsTooMany': 'For more than {n} guests please call us.',
    'err.emailInvalid': 'Please enter a valid email address.',
    'err.phoneInvalid': 'Please enter a valid phone number.',
    'err.tooLong': 'This text is too long.',
    'err.privacy': 'Please agree to the privacy policy.',

    'imprint.title': 'Site notice',
    'imprint.according': 'Information pursuant to Sect. 5 German Digital Services Act (DDG)',
    'imprint.register': 'Commercial register',
    'imprint.court': 'Register court',
    'imprint.rep': 'Represented by',
    'imprint.contact': 'Contact',
    'imprint.phone': 'Phone',
    'imprint.vat': 'VAT ID',
    'imprint.vatText': 'Sales tax identification number according to Sect. 27 a of the Sales Tax Law:',
    'imprint.dispute': 'Dispute resolution proceedings in front of a consumer arbitration board',
    'imprint.disputeText':
      'We are not willing or obliged to participate in dispute resolution proceedings in front of a consumer arbitration board.',

    'privacy.title': 'Privacy policy',
    'privacy.toc': 'Contents',

    'notfound.title': 'Page not found',
    'notfound.text': 'Sorry, this page does not exist (any more). Perhaps one of these helps:',

    'seo.home.title': 'Café & Restaurant Spreeblick – Real Berlin Cuisine in the Nikolaiviertel',
    'seo.home.desc':
      'Traditional Berlin food by the Spree: knuckle of pork, Königsberger Klopse, currywurst and cake in the Nikolaiviertel, Propststraße 9. Daily from 12 pm, closed Wednesdays.',
    'seo.menu.title': 'Menu – Café & Restaurant Spreeblick',
    'seo.menu.desc':
      'The full menu of Restaurant Spreeblick: Berlin originals, seasonal dishes, fish, desserts, ice cream, draft beers, wines and Berlin spirits.',
    'seo.contact.title': 'Contact – Café & Restaurant Spreeblick',
    'seo.contact.desc': 'Phone +49 30 2425247, email spreeblick@t-online.de – or write to us using the contact form.',
    'seo.reserve.title': 'Book a table – Café & Restaurant Spreeblick',
    'seo.reserve.desc': 'Book your table at Restaurant Spreeblick in Berlin’s Nikolaiviertel online. Daily from 12 pm, closed Wednesdays.',
    'seo.imprint.title': 'Site notice – Café & Restaurant Spreeblick',
    'seo.imprint.desc': 'Site notice of Spreeblick Beteiligungsgesellschaft GmbH & Co. Gastro KG, Propststraße 9, 10178 Berlin.',
    'seo.privacy.title': 'Privacy policy – Café & Restaurant Spreeblick',
    'seo.privacy.desc': 'Information on how personal data is processed on the website of Restaurant Spreeblick.',
  },
} as const;

type Dict = (typeof ui)['de'];
export type UiKey = { [K in keyof Dict]: Dict[K] extends string ? K : never }[keyof Dict];

/** Übersetzungsfunktion mit einfachen {platzhaltern}. */
export function useT(lang: Lang) {
  return (key: UiKey, vars: Record<string, string | number> = {}) =>
    (ui[lang][key] as string).replace(/\{(\w+)\}/g, (_, k) => String(vars[k] ?? `{${k}}`));
}

export const days = (lang: Lang) => ui[lang].days as readonly string[];
