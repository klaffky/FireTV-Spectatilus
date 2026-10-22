import raw from '../data/menu.json';
import type { Lang } from '../i18n/ui.ts';

type RawItem = { de: string; en: string; descDe: string; descEn: string; price: string };
type RawCategory = { id: string; section: 'speisen' | 'getraenke'; de: string; en: string; items: RawItem[] };

export type MenuEntry = {
  name: string;
  description: string;
  /** Eine Zeile je Größe; ohne Größe ist `size` leer. */
  prices: { size: string; price: string }[];
};

export type MenuCategory = { id: string; section: RawCategory['section']; title: string; entries: MenuEntry[] };

const SIZE_RE = /^(.*?)\s+(\d+(?:[.,]\d+)?\s?c?l)$/;

const formatPrice = (price: string, lang: Lang) => (lang === 'en' ? price.replace(',', '.') : price);

/** „9,50 €“ bzw. „€9.50“ – erwartet einen bereits per getMenu formatierten Preis. */
export const euro = (price: string, lang: Lang) => (lang === 'de' ? `${price} €` : `€${price}`);

/**
 * Bereitet die Karte für eine Sprache auf und fasst Größenvarianten
 * („Apfelschorle 0,3 l“ / „0,5 l“) zu einem Eintrag mit mehreren Preisen zusammen.
 */
export function getMenu(lang: Lang): MenuCategory[] {
  return (raw as RawCategory[]).map((cat) => {
    const entries: MenuEntry[] = [];
    for (const item of cat.items) {
      const label = lang === 'de' ? item.de : item.en || item.de;
      const description = lang === 'de' ? item.descDe : item.descEn || item.descDe;
      const price = formatPrice(item.price, lang);
      const sized = SIZE_RE.exec(label);
      const prev = entries.at(-1);
      if (sized && prev && prev.name === sized[1] && prev.description === description && prev.prices[0].size) {
        prev.prices.push({ size: sized[2], price });
      } else if (sized) {
        entries.push({ name: sized[1], description, prices: [{ size: sized[2], price }] });
      } else {
        entries.push({ name: label, description, prices: [{ size: '', price }] });
      }
    }
    return { id: cat.id, section: cat.section, title: lang === 'de' ? cat.de : cat.en, entries };
  });
}
