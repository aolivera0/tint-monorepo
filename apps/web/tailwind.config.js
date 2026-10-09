/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        tint: {
          bg: '#1E1F22',
          surface: '#2B2D31',
          card: '#313338',
          muted: '#B5BAC1',
          text: '#DBDEE1',
          accent: '#FF6B1A',
          accentHover: '#FF8A3D',
          accentDark: '#C4520A',
          success: '#23A55A',
        },
      },
    },
  },
  plugins: [],
};
