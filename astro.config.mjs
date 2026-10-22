// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';

// Standard: GitHub Pages unter https://klaffky.github.io/spreeblick-website
// Für die eigene Domain beim Build z. B. SITE_URL=https://www.spreeblick-nikolaiviertel.de BASE_PATH=/ setzen.
const site = process.env.SITE_URL || 'https://klaffky.github.io';
const base = process.env.BASE_PATH || '/spreeblick-website';

export default defineConfig({
  site,
  base,
  output: 'static',
  trailingSlash: 'ignore',
  devToolbar: { enabled: false },
  i18n: {
    locales: ['de', 'en'],
    defaultLocale: 'de',
    routing: { prefixDefaultLocale: false },
  },
  integrations: [
    sitemap({
      i18n: { defaultLocale: 'de', locales: { de: 'de-DE', en: 'en-GB' } },
      filter: (page) => !/\/404/.test(page),
    }),
  ],
  vite: { plugins: [tailwindcss()] },
});
