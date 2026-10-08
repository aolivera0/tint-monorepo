/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        tint: {
          bg: '#0B0C10',
          surface: '#1F2833',
          muted: '#C5C6C7',
          accent: '#66FCF1',
          accentDark: '#45A29E',
        },
      },
    },
  },
  plugins: [],
};
