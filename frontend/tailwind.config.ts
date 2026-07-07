import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        playful: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      colors: {
        clay: {
          ink: '#161616',
          muted: '#6F6A64',
          subtle: '#9B948C',
          base: '#F8F6F3',
          cream: '#FFF7D6',
          paper: '#FFFFFF',
          glass: '#FFFFFF',
          border: '#111111',
          primary: '#F45113',
          'primary-dark': '#C63A08',
          secondary: '#9EEBC5',
          'secondary-dark': '#63C995',
          cta: '#F45113',
          'cta-dark': '#FF6A2A',
          accent: '#BEE7F8',
          purple: '#F8B8C8',
          gold: '#FFF176',
          success: '#18A96B',
          error: '#E23B2E',
          mint: '#9EEBC5',
          yellow: '#FFF176',
          coral: '#F45113',
          indigo: '#BEE7F8',
          lavender: '#F8B8C8',
          orange: '#F45113',
          pink: '#F8B8C8',
          cyan: '#BEE7F8',
        },
      },
      boxShadow: {
        clay: '5px 6px 0 rgba(17, 17, 17, 0.78)',
        'clay-sm': '2px 3px 0 rgba(17, 17, 17, 0.72)',
        'clay-hover': '6px 7px 0 rgba(17, 17, 17, 0.82)',
      },
      borderRadius: {
        clay: '1.5rem',
        blob: '2rem',
      },
    },
  },
  plugins: [],
} satisfies Config;
